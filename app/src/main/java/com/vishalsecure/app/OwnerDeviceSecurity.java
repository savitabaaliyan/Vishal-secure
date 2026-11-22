package com.vishalsecure.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Isolated Android Keystore helper for the owner-device migration.
 *
 * This class does not change the existing local video encryption key or
 * automatically enroll an account. Callers must explicitly start enrollment
 * only after the backend has been deployed and the owner has confirmed it.
 */
public final class OwnerDeviceSecurity {
    private static final String PREFS = "VishalSecureOwnerDevice";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "VishalSecureOwnerSigningKey";
    private static final String FUNCTIONS_REGION = "us-central1";

    private final Context appContext;
    private final FirebaseFunctions functions;

    public OwnerDeviceSecurity(Context context) {
        appContext = context.getApplicationContext();
        functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION);
    }

    /** Random per-installation identifier; it is not treated as proof by itself. */
    public String getInstallationId() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_INSTALLATION_ID, null);
        if (id != null && !id.trim().isEmpty()) return id;

        id = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_INSTALLATION_ID, id).apply();
        return id;
    }

    /** Creates a non-exportable P-256 signing key in Android Keystore if absent. */
    public synchronized void ensureSigningKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return;

        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build();
        generator.initialize(spec);
        generator.generateKeyPair();
    }

    /** Returns the public key in PEM/SPKI form; private key never leaves Keystore. */
    public String getPublicKeyPem() throws Exception {
        ensureSigningKey();
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        java.security.cert.Certificate certificate = keyStore.getCertificate(KEY_ALIAS);
        if (certificate == null) throw new IllegalStateException("Owner signing certificate is missing.");
        byte[] encoded = certificate.getPublicKey().getEncoded();
        String body = Base64.encodeToString(encoded, Base64.NO_WRAP);
        StringBuilder pem = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < body.length(); i += 64) {
            pem.append(body, i, Math.min(i + 64, body.length())).append('\n');
        }
        return pem.append("-----END PUBLIC KEY-----").toString();
    }

    /** Signs the exact UTF-8 challenge message with the non-exportable private key. */
    public String signMessage(String message) throws Exception {
        ensureSigningKey();
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_ALIAS, null);
        if (privateKey == null) throw new IllegalStateException("Owner signing key is unavailable.");
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP);
    }

    public Task<HttpsCallableResult> beginEnrollment() {
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", getInstallationId());
        return functions.getHttpsCallable("beginOwnerDeviceEnrollment").call(data);
    }

    public Task<HttpsCallableResult> completeEnrollment(
            String challengeId, String nonce, String purpose) throws Exception {
        String deviceId = getInstallationId();
        String message = challengeId + "." + nonce + "."
                + getCurrentUid() + "." + deviceId + "." + purpose;
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", deviceId);
        data.put("publicKey", getPublicKeyPem());
        data.put("challengeId", challengeId);
        data.put("signature", signMessage(message));
        return functions.getHttpsCallable("completeOwnerDeviceEnrollment").call(data);
    }

    /**
     * Obtains a one-use challenge bound to the exact operation payload, signs it,
     * and returns the deviceProof map expected by the protected callable.
     */
    public Task<Map<String, Object>> createSensitiveRequestProof(
            String operation, Map<String, Object> payload) {
        Map<String, Object> request = new HashMap<>();
        request.put("deviceId", getInstallationId());
        request.put("operation", operation);
        request.put("payload", payload);

        return functions.getHttpsCallable("beginOwnerSensitiveRequest").call(request)
                .continueWith(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    Object raw = task.getResult().getData();
                    if (!(raw instanceof Map)) {
                        throw new IllegalStateException("Server returned an invalid device challenge.");
                    }
                    Map<?, ?> challenge = (Map<?, ?>) raw;
                    String challengeId = String.valueOf(challenge.get("challengeId"));
                    String nonce = String.valueOf(challenge.get("nonce"));
                    String purpose = String.valueOf(challenge.get("purpose"));
                    String requestHash = String.valueOf(challenge.get("requestHash"));
                    String message = challengeId + "." + nonce + "." + getCurrentUid()
                            + "." + getInstallationId() + "." + purpose + "." + requestHash;
                    String signature = signMessage(message);

                    Map<String, Object> proof = new HashMap<>();
                    proof.put("deviceId", getInstallationId());
                    proof.put("challengeId", challengeId);
                    proof.put("signature", signature);
                    proof.put("payload", payload);
                    return proof;
                });
    }

    /** Starts a replacement request; call from the old, currently active phone. */
    public Task<HttpsCallableResult> beginReplacement(String newDeviceId) {
        Map<String, Object> data = new HashMap<>();
        data.put("newDeviceId", newDeviceId);
        return functions.getHttpsCallable("beginOwnerDeviceReplacement").call(data);
    }

    /** Signs the server challenge on the old phone to approve its replacement. */
    public Task<HttpsCallableResult> approveReplacement(String challengeId, String nonce, String purpose) throws Exception {
        String message = challengeId + "." + nonce + "." + getCurrentUid()
                + "." + getInstallationId() + "." + purpose;
        Map<String, Object> data = new HashMap<>();
        data.put("challengeId", challengeId);
        data.put("signature", signMessage(message));
        return functions.getHttpsCallable("approveOwnerDeviceReplacement").call(data);
    }

    /** Requests the one-use proof challenge for the new phone after old-phone approval. */
    public Task<HttpsCallableResult> beginReplacementDeviceProof(String replacementId) {
        Map<String, Object> data = new HashMap<>();
        data.put("replacementId", replacementId);
        return functions.getHttpsCallable("beginOwnerReplacementDeviceProof").call(data);
    }

    /** Completes replacement on the new phone; server revokes the old phone atomically. */
    public Task<HttpsCallableResult> completeReplacement(
            String replacementId, String challengeId, String nonce, String purpose) throws Exception {
        String deviceId = getInstallationId();
        String message = challengeId + "." + nonce + "." + getCurrentUid()
                + "." + deviceId + "." + purpose;
        Map<String, Object> data = new HashMap<>();
        data.put("replacementId", replacementId);
        data.put("proofChallengeId", challengeId);
        data.put("deviceId", deviceId);
        data.put("publicKey", getPublicKeyPem());
        data.put("signature", signMessage(message));
        return functions.getHttpsCallable("completeOwnerDeviceReplacement").call(data);
    }

    /** Creates a one-time recovery code. Display/store it securely; never log it. */
    public Task<HttpsCallableResult> createRecoveryCode() {
        return functions.getHttpsCallable("createOwnerRecoveryCode").call(new HashMap<>());
    }

    /** Requests a new-phone challenge for recovery when the old phone is unavailable. */
    public Task<HttpsCallableResult> beginRecoveryChallenge() {
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", getInstallationId());
        return functions.getHttpsCallable("beginOwnerDeviceRecovery").call(data);
    }

    /** Completes recovery using the one-time offline code and this phone's Keystore key. */
    public Task<HttpsCallableResult> recoverOwnerDevice(
            String recoveryCode, String challengeId, String nonce, String purpose) throws Exception {
        String deviceId = getInstallationId();
        String message = challengeId + "." + nonce + "." + getCurrentUid()
                + "." + deviceId + "." + purpose;
        Map<String, Object> data = new HashMap<>();
        data.put("recoveryCode", recoveryCode);
        data.put("deviceId", deviceId);
        data.put("publicKey", getPublicKeyPem());
        data.put("challengeId", challengeId);
        data.put("signature", signMessage(message));
        return functions.getHttpsCallable("recoverOwnerDevice").call(data);
    }

    private String getCurrentUid() {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) throw new IllegalStateException("Owner must be signed in.");
        return user.getUid();
    }
}
