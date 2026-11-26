package com.vishalsecure.app;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class RecipientOaepCompatibilityTest {
    private static final String TEST_ALIAS = "VishalSecureRecipientOaepTestOnly";
    private static final OAEPParameterSpec OAEP_SHA256_MGF1_SHA1 =
            new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA1,
                    PSource.PSpecified.DEFAULT);

    @Test
    public void androidKeystoreRsa3072ExplicitOaepProfileRoundTripsSyntheticDek()
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(TEST_ALIAS);

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");

            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    TEST_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(3072)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setUserAuthenticationRequired(false);

            // This key-generation constraint was added in API 35.
            // On API 24-34, the test still passes the MGF1 digest explicitly
            // to Cipher and requires a successful exact-profile round trip.
            if (Build.VERSION.SDK_INT >= 35) {
                builder.setMgf1Digests(KeyProperties.DIGEST_SHA1);
            }

            generator.initialize(builder.build());
            java.security.KeyPair pair = generator.generateKeyPair();

            KeyStore.Entry entry = keyStore.getEntry(TEST_ALIAS, null);
            assertNotNull("Android Keystore test key entry must exist", entry);
            java.security.PrivateKey privateKey =
                    (java.security.PrivateKey) keyStore.getKey(TEST_ALIAS, null);
            assertNotNull("Private key must remain available through Keystore handle", privateKey);
            assertNull("Private key must not be exportable", privateKey.getEncoded());

            byte[] syntheticDek = new byte[32];
            new SecureRandom().nextBytes(syntheticDek);

            Cipher encrypt = Cipher.getInstance("RSA/ECB/OAEPPadding");
            encrypt.init(Cipher.ENCRYPT_MODE, pair.getPublic(), OAEP_SHA256_MGF1_SHA1);
            byte[] wrapped = encrypt.doFinal(syntheticDek);

            Cipher decrypt = Cipher.getInstance("RSA/ECB/OAEPPadding");
            decrypt.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256_MGF1_SHA1);
            byte[] unwrapped = decrypt.doFinal(wrapped);

            assertArrayEquals("Explicit OAEP SHA-256 / MGF1-SHA-1 round trip must match",
                    syntheticDek, unwrapped);
        } finally {
            keyStore.deleteEntry(TEST_ALIAS);
        }
    }
}
