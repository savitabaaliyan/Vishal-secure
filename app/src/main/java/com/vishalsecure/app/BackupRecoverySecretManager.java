package com.vishalsecure.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Manages the independent 256-bit Backup Recovery Secret.
 *
 * The local copy is encrypted by a device-bound Android Keystore key.
 * A user must export the Base64 secret once and keep it offline; the encrypted
 * local copy alone cannot be moved to another phone.
 */
public final class BackupRecoverySecretManager {
    private static final String PREFS = "VishalSecureBackupRecovery";
    private static final String KEY_CIPHERTEXT = "secret_ciphertext";
    private static final String KEY_IV = "secret_iv";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "VishalSecureBackupWrapKey";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int SECRET_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final byte[] AAD = "VishalSecure Backup Recovery Secret v1"
            .getBytes(StandardCharsets.UTF_8);

    private final Context appContext;

    public BackupRecoverySecretManager(Context context) {
        appContext = context.getApplicationContext();
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

    /** Imports a previously exported secret on a replacement phone. */
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

    /** Returns a short-lived in-memory copy for a backup/restore operation. Caller must clear it. */
    public synchronized byte[] getSecretForOperation() throws Exception {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String encodedIv = prefs.getString(KEY_IV, null);
        String encodedCiphertext = prefs.getString(KEY_CIPHERTEXT, null);
        if (encodedIv == null || encodedCiphertext == null) {
            throw new IllegalStateException("Backup Recovery Secret is not configured.");
        }
        byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP);
        if (iv.length != IV_BYTES || ciphertext.length != SECRET_BYTES + 16) {
            throw new IllegalStateException("Stored Backup Recovery Secret is invalid.");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        byte[] secret = cipher.doFinal(ciphertext);
        if (secret.length != SECRET_BYTES) {
            Arrays.fill(secret, (byte) 0);
            throw new IllegalStateException("Stored Backup Recovery Secret is invalid.");
        }
        return secret;
    }

    public boolean hasSecret() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.contains(KEY_IV) && prefs.contains(KEY_CIPHERTEXT);
    }

    private void saveEncrypted(byte[] secret) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey());
        cipher.updateAAD(AAD);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(secret);
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean saved = prefs.edit()
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .commit();
        Arrays.fill(ciphertext, (byte) 0);
        if (!saved) throw new IllegalStateException("Could not securely save Backup Recovery Secret.");
    }

    private SecretKey getOrCreateWrappingKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            throw new IllegalStateException("Backup wrapping key is invalid; refusing to replace it.");
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
