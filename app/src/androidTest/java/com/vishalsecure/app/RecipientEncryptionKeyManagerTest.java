package com.vishalsecure.app;

import android.security.keystore.KeyStoreException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class RecipientEncryptionKeyManagerTest {
    private static final String TEST_ALIAS = "VishalSecureRecipientEncryptionManagerTestOnly";

    @Test
    public void createsNonExportableRecipientKeyAndRoundTripsSyntheticDek() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(TEST_ALIAS);
        RecipientEncryptionKeyManager manager =
                new RecipientEncryptionKeyManager(TEST_ALIAS);
        byte[] dek = new byte[RecipientEncryptionKeyManager.DEK_LENGTH_BYTES];
        byte[] wrapped = null;
        byte[] opened = null;
        try {
            new SecureRandom().nextBytes(dek);
            manager.ensureKeyPair();
            assertEquals(RecipientEncryptionKeyManager.RSA_KEY_SIZE_BITS,
                    ((java.security.interfaces.RSAPublicKey) manager.getPublicKey()).getModulus().bitLength());
            assertNotNull(manager.getPublicKeySpki());
            assertEquals(32, manager.getPublicKeyFingerprint().length);
            wrapped = manager.wrapDek(dek);
            assertEquals(384, wrapped.length);
            opened = manager.unwrapDek(wrapped);
            assertArrayEquals(dek, opened);

            java.security.Key privateKey = keyStore.getKey(TEST_ALIAS, null);
            assertNotNull(privateKey);
            assertNull("Private key must remain non-exportable", privateKey.getEncoded());
        } finally {
            Arrays.fill(dek, (byte) 0);
            if (opened != null) Arrays.fill(opened, (byte) 0);
            if (wrapped != null) Arrays.fill(wrapped, (byte) 0);
            keyStore.deleteEntry(TEST_ALIAS);
        }
    }

    @Test
    public void rejectsWrongDekAndWrappedLengths() throws Exception {
        RecipientEncryptionKeyManager manager =
                new RecipientEncryptionKeyManager(TEST_ALIAS);
        try {
            manager.wrapDek(new byte[31]);
            fail("31-byte DEK must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        try {
            manager.unwrapDek(new byte[383]);
            fail("Wrong RSA ciphertext length must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void existingKeyIsNeverSilentlyReplaced() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(TEST_ALIAS);
        RecipientEncryptionKeyManager manager =
                new RecipientEncryptionKeyManager(TEST_ALIAS);
        try {
            manager.ensureKeyPair();
            byte[] firstFingerprint = manager.getPublicKeyFingerprint();
            manager.ensureKeyPair();
            byte[] secondFingerprint = manager.getPublicKeyFingerprint();
            assertArrayEquals(firstFingerprint, secondFingerprint);
        } finally {
            keyStore.deleteEntry(TEST_ALIAS);
        }
    }
}
