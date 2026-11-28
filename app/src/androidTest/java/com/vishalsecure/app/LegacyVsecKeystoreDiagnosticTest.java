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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Isolates Android Keystore AES-GCM behavior without touching the production
 * VishalSecureVideoKey alias or changing the legacy .vsec format.
 */
@RunWith(AndroidJUnit4.class)
public class LegacyVsecKeystoreDiagnosticTest {
    private static final String TEST_ALIAS = "VishalSecureGcmDiagnosticOnly";

    @Test
    public void isolatedKeystoreAesGcmRoundTripsAcrossCipherInstances() throws Exception {
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
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    TEST_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build();
            generator.init(spec);
            SecretKey generated = generator.generateKey();
            assertNotNull(generated);

            Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
            encrypt.init(Cipher.ENCRYPT_MODE, generated);
            iv = encrypt.getIV();
            assertNotNull(iv);
            assertEquals(12, iv.length);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] first = encrypt.update(clear);
            if (first != null) output.write(first);
            byte[] last = encrypt.doFinal();
            if (last != null) output.write(last);
            encrypted = output.toByteArray();

            KeyStore.Entry entry = store.getEntry(TEST_ALIAS, null);
            assertNotNull(entry);
            SecretKey retrieved = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
            decrypt.init(Cipher.DECRYPT_MODE, retrieved, new GCMParameterSpec(128, iv));
            recovered = decrypt.doFinal(encrypted);
            assertArrayEquals(clear, recovered);
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (iv != null) Arrays.fill(iv, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
            if (recovered != null) Arrays.fill(recovered, (byte) 0);
            store.deleteEntry(TEST_ALIAS);
        }
    }

    @Test
    public void productionLegacyVsecAliasRoundTripsOnlySyntheticFixture() throws Exception {
        java.io.File dir = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir();
        String id = java.util.UUID.randomUUID().toString();
        java.io.File clearFile = new java.io.File(dir, "legacy-gcm-diag-" + id + ".clear");
        java.io.File encryptedFile = new java.io.File(dir, "legacy-gcm-diag-" + id + ".vsec");
        java.io.File recoveredFile = new java.io.File(dir, "legacy-gcm-diag-" + id + ".recovered");
        byte[] clear = new byte[131117];
        byte[] recovered = null;
        try {
            new SecureRandom().nextBytes(clear);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(clearFile)) {
                out.write(clear);
                out.getFD().sync();
            }
            assertEquals(true, CryptoManager.encryptFile(clearFile, encryptedFile));
            assertEquals(true, CryptoManager.decryptFile(encryptedFile, recoveredFile));
            recovered = new byte[(int) recoveredFile.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(recoveredFile)) {
                int offset = 0;
                while (offset < recovered.length) {
                    int count = in.read(recovered, offset, recovered.length - offset);
                    if (count < 0) throw new java.io.EOFException("Unexpected recovered fixture EOF.");
                    if (count == 0) continue;
                    offset += count;
                }
            }
            assertArrayEquals(clear, recovered);
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (recovered != null) Arrays.fill(recovered, (byte) 0);
            clearFile.delete();
            encryptedFile.delete();
            recoveredFile.delete();
        }
    }
}
