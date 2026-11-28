package com.vishalsecure.app;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class LegacyVsecKeystoreDiagnosticTest {
    private static final String TEST_ALIAS = "VishalSecureGcmDiagnosticOnly";

    private static byte[] encrypt(Cipher cipher, byte[] clear) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] update = cipher.update(clear);
        if (update != null) output.write(update);
        byte[] finish = cipher.doFinal();
        if (finish != null) output.write(finish);
        return output.toByteArray();
    }

    @Test
    public void softwareAesGcmControlRoundTrips() throws Exception {
        byte[] keyBytes = new byte[32];
        byte[] clear = new byte[131117];
        byte[] iv = new byte[12];
        byte[] encrypted = null;
        byte[] recovered = null;
        try {
            SecureRandom random = new SecureRandom();
            random.nextBytes(keyBytes);
            random.nextBytes(clear);
            random.nextBytes(iv);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
            enc.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            encrypted = encrypt(enc, clear);
            Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
            dec.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            recovered = dec.doFinal(encrypted);
            assertArrayEquals(clear, recovered);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
            Arrays.fill(clear, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
            if (recovered != null) Arrays.fill(recovered, (byte) 0);
        }
    }

    @Test
    public void androidKeystoreSameKeyObjectDecryptsItsOwnCiphertext() throws Exception {
        runAndroidKeystoreRoundTrip(false);
    }

    @Test
    public void androidKeystoreReloadedKeyDecryptsItsOwnCiphertext() throws Exception {
        runAndroidKeystoreRoundTrip(true);
    }

    private void runAndroidKeystoreRoundTrip(boolean reloadKey) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        store.deleteEntry(TEST_ALIAS);
        byte[] clear = new byte[131117];
        byte[] iv = null;
        byte[] encrypted = null;
        byte[] recovered = null;
        try {
            new SecureRandom().nextBytes(clear);
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(TEST_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build());
            SecretKey generated = generator.generateKey();
            assertNotNull(generated);
            Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
            enc.init(Cipher.ENCRYPT_MODE, generated);
            iv = enc.getIV();
            assertNotNull(iv);
            assertEquals(12, iv.length);
            encrypted = encrypt(enc, clear);

            SecretKey decryptKey = generated;
            if (reloadKey) {
                KeyStore.Entry entry = store.getEntry(TEST_ALIAS, null);
                assertNotNull(entry);
                decryptKey = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }

            Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
            decrypt.init(Cipher.DECRYPT_MODE, decryptKey,
                    new GCMParameterSpec(128, iv));
            recovered = decrypt.doFinal(encrypted);
            assertArrayEquals(reloadKey
                            ? "Reloaded Keystore key must decrypt its own GCM ciphertext"
                            : "Same key object must decrypt its own GCM ciphertext",
                    clear, recovered);
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (iv != null) Arrays.fill(iv, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
            if (recovered != null) Arrays.fill(recovered, (byte) 0);
            store.deleteEntry(TEST_ALIAS);
        }
    }
}
