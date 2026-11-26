package com.vishalsecure.app;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Device-local recipient encryption key, separate from owner signing and
 * legacy .vsec keys. This class performs no enrollment or network operation.
 */
public final class RecipientEncryptionKeyManager {
    public static final int KEY_VERSION = 1;
    public static final int RSA_KEY_SIZE_BITS = 3072;
    public static final int DEK_LENGTH_BYTES = 32;

    private static final String KEYSTORE_NAME = "AndroidKeyStore";
    private static final String PRODUCTION_ALIAS = "VishalSecureRecipientEncryptionKeyV1";
    private static final String OAEP_TRANSFORMATION = "RSA/ECB/OAEPPadding";
    private static final OAEPParameterSpec OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);

    private final String alias;

    public RecipientEncryptionKeyManager() {
        this(PRODUCTION_ALIAS);
    }

    // Package-private only so instrumentation tests can use and delete an isolated alias.
    RecipientEncryptionKeyManager(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("A non-empty Keystore alias is required.");
        }
        this.alias = alias;
    }

    /** Creates the recipient RSA key once; never replaces an existing key. */
    public synchronized void ensureKeyPair() throws Exception {
        KeyStore keyStore = loadKeyStore();
        if (keyStore.containsAlias(alias)) {
            requireExistingKeyPair(keyStore);
            return;
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_NAME);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(RSA_KEY_SIZE_BITS)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setUserAuthenticationRequired(false);

        // API 35 can bind MGF1-SHA1 into the key authorization itself.
        // API 24-34 are accepted only if the explicit OAEP round-trip below works.
        if (Build.VERSION.SDK_INT >= 35) {
            builder.setMgf1Digests(KeyProperties.DIGEST_SHA1);
        }

        generator.initialize(builder.build());
        generator.generateKeyPair();

        // Prove the generated key supports the exact profile. Never downgrade.
        byte[] probe = new byte[DEK_LENGTH_BYTES];
        new java.security.SecureRandom().nextBytes(probe);
        byte[] wrapped = null;
        byte[] opened = null;
        try {
            wrapped = wrapWithPublicKey(getPublicKey(), probe);
            opened = unwrapWithPrivateKey(getPrivateKey(), wrapped);
            if (!MessageDigest.isEqual(probe, opened)) {
                throw new IllegalStateException("Recipient OAEP profile self-test failed.");
            }
        } catch (Exception failure) {
            // Do not leave a key that this installation cannot use safely.
            KeyStore cleanup = loadKeyStore();
            cleanup.deleteEntry(alias);
            throw failure;
        } finally {
            Arrays.fill(probe, (byte) 0);
            if (opened != null) Arrays.fill(opened, (byte) 0);
            if (wrapped != null) Arrays.fill(wrapped, (byte) 0);
        }
    }

    public synchronized PublicKey getPublicKey() throws Exception {
        KeyStore keyStore = loadKeyStore();
        requireExistingKeyPair(keyStore);
        Certificate certificate = keyStore.getCertificate(alias);
        if (certificate == null || certificate.getPublicKey() == null) {
            throw new IllegalStateException("Recipient public key certificate is missing.");
        }
        return certificate.getPublicKey();
    }

    /** Canonical DER SubjectPublicKeyInfo bytes for authenticated registration. */
    public byte[] getPublicKeySpki() throws Exception {
        byte[] encoded = getPublicKey().getEncoded();
        if (encoded == null || encoded.length == 0) {
            throw new IllegalStateException("Recipient public key encoding is unavailable.");
        }
        return encoded.clone();
    }

    /** SHA-256 fingerprint of the exact SPKI bytes. */
    public byte[] getPublicKeyFingerprint() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(getPublicKeySpki());
    }

    /** Wraps exactly one 256-bit DEK for this device's public key. */
    public byte[] wrapDek(byte[] dek) throws Exception {
        validateDek(dek);
        return wrapWithPublicKey(getPublicKey(), dek);
    }

    /** Opens a wrapped 256-bit DEK using this device's non-exportable private key. */
    public byte[] unwrapDek(byte[] wrappedDek) throws Exception {
        if (wrappedDek == null || wrappedDek.length != 384) {
            throw new IllegalArgumentException("Invalid RSA-3072 wrapped DEK length.");
        }
        byte[] dek = unwrapWithPrivateKey(getPrivateKey(), wrappedDek);
        if (dek == null || dek.length != DEK_LENGTH_BYTES) {
            if (dek != null) Arrays.fill(dek, (byte) 0);
            throw new IllegalStateException("Unwrapped content key has an invalid length.");
        }
        return dek;
    }

    private byte[] wrapWithPublicKey(PublicKey publicKey, byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(OAEP_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC);
        return cipher.doFinal(plaintext);
    }

    private byte[] unwrapWithPrivateKey(PrivateKey privateKey, byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance(OAEP_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC);
        return cipher.doFinal(ciphertext);
    }

    private PrivateKey getPrivateKey() throws Exception {
        KeyStore keyStore = loadKeyStore();
        requireExistingKeyPair(keyStore);
        java.security.Key key = keyStore.getKey(alias, null);
        if (!(key instanceof PrivateKey)) {
            throw new IllegalStateException("Recipient private key is unavailable.");
        }
        if (key.getEncoded() != null) {
            throw new IllegalStateException("Recipient private key must be non-exportable.");
        }
        return (PrivateKey) key;
    }

    private void requireExistingKeyPair(KeyStore keyStore) throws Exception {
        if (!keyStore.containsAlias(alias)) {
            throw new IllegalStateException("Recipient encryption key is not enrolled on this device.");
        }
        if (keyStore.getCertificate(alias) == null || keyStore.getKey(alias, null) == null) {
            throw new IllegalStateException("Recipient encryption key entry is incomplete.");
        }
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_NAME);
        keyStore.load(null);
        return keyStore;
    }

    private static void validateDek(byte[] dek) {
        if (dek == null || dek.length != DEK_LENGTH_BYTES) {
            throw new IllegalArgumentException("Content key must be exactly 256 bits.");
        }
    }
}
