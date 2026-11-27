package com.vishalsecure.app;


import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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

    @Test
    public void recipientReaderUnwrapsHeaderDekAndAuthenticatesPackage() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(TEST_ALIAS);
        RecipientEncryptionKeyManager manager =
                new RecipientEncryptionKeyManager(TEST_ALIAS);
        byte[] clear = new byte[] {11, 22, 33, 44, 55};
        byte[] dek = new byte[RecipientEncryptionKeyManager.DEK_LENGTH_BYTES];
        byte[] wrapped = null;
        byte[] fingerprint = null;
        try {
            new SecureRandom().nextBytes(dek);
            manager.ensureKeyPair();
            wrapped = manager.wrapDek(dek);
            fingerprint = manager.getPublicKeyFingerprint();
            String senderDevice = "11111111-1111-4111-8111-111111111111";
            String recipientDevice = "22222222-2222-4222-8222-222222222222";
            String assignment = "33333333-3333-4333-8333-333333333333";
            String share = "44444444-4444-4444-8444-444444444444";
            VShareCodec.Metadata metadata = new VShareCodec.Metadata(
                    "sender-uid", "recipient-uid", senderDevice, recipientDevice,
                    assignment, share, RecipientEncryptionKeyManager.KEY_VERSION, fingerprint);
            ByteArrayOutputStream packageBytes = new ByteArrayOutputStream();
            VShareCodec.write(new ByteArrayInputStream(clear), packageBytes,
                    clear.length, metadata, dek, wrapped);

            final ByteArrayOutputStream received = new ByteArrayOutputStream();
            final boolean[] committed = new boolean[1];
            VShareCodec.readForRecipient(new ByteArrayInputStream(packageBytes.toByteArray()),
                    new VShareCodec.OutputSink() {
                        @Override public void writeAuthenticatedChunk(byte[] chunk) {
                            received.write(chunk, 0, chunk.length);
                        }
                        @Override public void commit() { committed[0] = true; }
                        @Override public void abort() { received.reset(); }
                    }, "sender-uid", "recipient-uid", senderDevice, recipientDevice,
                    assignment, share, manager);

            assertArrayEquals(clear, received.toByteArray());
            assertEquals(true, committed[0]);
        } finally {
            Arrays.fill(dek, (byte) 0);
            if (wrapped != null) Arrays.fill(wrapped, (byte) 0);
            if (fingerprint != null) Arrays.fill(fingerprint, (byte) 0);
            keyStore.deleteEntry(TEST_ALIAS);
        }
    }
}
