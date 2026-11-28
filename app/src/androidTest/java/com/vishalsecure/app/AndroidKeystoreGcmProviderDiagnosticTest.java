package com.vishalsecure.app;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class AndroidKeystoreGcmProviderDiagnosticTest {
    private static final String ALIAS = "VishalSecureGcmProviderDiagnosticOnly";

    @Test
    public void oneShotGcmRoundTripWithOneByte() throws Exception {
        runOneShotRoundTrip(1);
    }

    @Test
    public void oneShotGcmRoundTripWithLargePayload() throws Exception {
        runOneShotRoundTrip(131117);
    }

    private void runOneShotRoundTrip(int size) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        store.deleteEntry(ALIAS);
        byte[] clear = new byte[size];
        byte[] encrypted = null;
        byte[] recovered = null;
        byte[] iv = null;
        try {
            new SecureRandom().nextBytes(clear);
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build());
            SecretKey key = generator.generateKey();

            Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
            enc.init(Cipher.ENCRYPT_MODE, key);
            iv = enc.getIV();
            assertNotNull(iv);
            assertEquals(12, iv.length);
            encrypted = enc.doFinal(clear);

            Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
            dec.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            recovered = dec.doFinal(encrypted);
            assertArrayEquals("GCM one-shot round-trip failed; provider="
                    + enc.getProvider().getName() + ", payloadBytes=" + size,
                    clear, recovered);
        } finally {
            java.util.Arrays.fill(clear, (byte) 0);
            if (encrypted != null) java.util.Arrays.fill(encrypted, (byte) 0);
            if (recovered != null) java.util.Arrays.fill(recovered, (byte) 0);
            if (iv != null) java.util.Arrays.fill(iv, (byte) 0);
            store.deleteEntry(ALIAS);
        }
    }
}
