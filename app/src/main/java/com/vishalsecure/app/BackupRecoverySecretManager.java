package com.vishalsecure.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Manages an independent 256-bit Backup Recovery Secret for one Firebase UID.
 * The local copy is encrypted by a UID-specific, device-bound Android Keystore key.
 * The exported secret must be stored offline; this local copy cannot move to another phone.
 */
public final class BackupRecoverySecretManager {
    private static final String PREFS_PREFIX = "VishalSecureBackupRecovery_";
    private static final String KEY_CIPHERTEXT = "secret_ciphertext";
    private static final String KEY_IV = "secret_iv";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS_PREFIX = "VishalSecureBackupWrap_";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int SECRET_BYTES = 32;
    private static final int IV_BYTES = 12;

    private final Context appContext;
    private final String ownerUid;
    private final String prefsName;
    private final String keyAlias;
    private final byte[] aad;

    /** A verified, non-empty Firebase Authentication UID is required. */
    public BackupRecoverySecretManager(Context context, String firebaseUid) {
        if (context == null || firebaseUid == null || firebaseUid.trim().isEmpty()) {
            throw new IllegalArgumentException("A signed-in Firebase owner UID is required.");
        }
        appContext = context.getApplicationContext();
        ownerUid = firebaseUid;
        byte[] uidBytes = ownerUid.getBytes(StandardCharsets.UTF_8);
        String suffix = hex(sha256(uidBytes));
        prefsName = PREFS_PREFIX + suffix;
        keyAlias = KEY_ALIAS_PREFIX + suffix;
        aad = concat("VishalSecure Backup Recovery Secret v1".getBytes(StandardCharsets.UTF_8),
                new byte[] {0}, uidBytes);
    }

    /** Creates a new secret only when none is stored. Returns its one-time export form. */
    public synchronized String createAndExportOnce() throws Exception {
        if (hasSecret()) throw new IllegalStateException("A Backup Recovery Secret already exists.");
        byte[] secret = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(secret);
        try {
            saveEncrypted(secret);
            return Base64.encodeToString(secret, Base64.NO_WRAP);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    /** Imports a previously exported secret on a replacement phone for this same Firebase UID. */
    public synchronized void importSecret(String base64Secret) throws Exception {
        if (hasSecret()) throw new IllegalStateException("A Backup Recovery Secret is already stored.");
        if (base64Secret == null) throw new IllegalArgumentException("Recovery Secret is required.");
        byte[] secret;
        try {
            secret = Base64.decode(base64Secret.trim(), Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Recovery Secret format is invalid.");
        }
        try {
            if (secret.length != SECRET_BYTES) {
                throw new IllegalArgumentException("Recovery Secret must be exactly 32 bytes.");
            }
            saveEncrypted(secret);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    /** Returns a short-lived in-memory copy. Caller must clear it after use. */
    public synchronized byte[] getSecretForOperation() throws Exception {
        SharedPreferences prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        String encodedIv = prefs.getString(KEY_IV, null);
        String encodedCiphertext = prefs.getString(KEY_CIPHERTEXT, null);
        if (encodedIv == null || encodedCiphertext == null) {
            throw new IllegalStateException("Backup Recovery Secret is not configured for this owner.");
        }
        byte[] iv = null;
        byte[] ciphertext = null;
        try {
            iv = Base64.decode(encodedIv, Base64.NO_WRAP);
            ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP);
            if (iv.length != IV_BYTES || ciphertext.length != SECRET_BYTES + 16) {
                throw new IllegalStateException("Stored Backup Recovery Secret is invalid.");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad);
            byte[] secret = cipher.doFinal(ciphertext);
            if (secret.length != SECRET_BYTES) {
                Arrays.fill(secret, (byte) 0);
                throw new IllegalStateException("Stored Backup Recovery Secret is invalid.");
            }
            return secret;
        } finally {
            if (iv != null) Arrays.fill(iv, (byte) 0);
            if (ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
        }
    }

    public synchronized boolean hasSecret() {
        SharedPreferences prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        return prefs.contains(KEY_IV) && prefs.contains(KEY_CIPHERTEXT);
    }

    private void saveEncrypted(byte[] secret) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey());
        cipher.updateAAD(aad);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(secret);
        try {
            SharedPreferences prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            boolean saved = prefs.edit()
                    .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .commit();
            if (!saved) throw new IllegalStateException("Could not securely save Backup Recovery Secret.");
        } finally {
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    private SecretKey getOrCreateWrappingKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(keyAlias)) {
            KeyStore.Entry entry = keyStore.getEntry(keyAlias, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            throw new IllegalStateException("Backup wrapping key is invalid; refusing to replace it.");
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                keyAlias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    private static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    private static String hex(byte[] input) {
        StringBuilder out = new StringBuilder(input.length * 2);
        for (byte b : input) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        Arrays.fill(input, (byte) 0);
        return out.toString();
    }

    private static byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] a : arrays) size += a.length;
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, offset, a.length);
            offset += a.length;
        }
        return result;
    }
}
