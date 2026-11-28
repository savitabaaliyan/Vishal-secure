package com.vishalsecure.app;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** End-to-end sender tests using synthetic content and isolated Keystore aliases only. */
@RunWith(AndroidJUnit4.class)
public class VShareSenderInstrumentedTest {
    private static final String TEST_ALIAS = "VishalSecureVShareSenderTestOnly";
    private static final String SENDER_DEVICE = "11111111-1111-4111-8111-111111111111";
    private static final String RECIPIENT_DEVICE = "22222222-2222-4222-8222-222222222222";
    private static final String ASSIGNMENT = "33333333-3333-4333-8333-333333333333";

    private static byte[] readAll(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = in.read(bytes, offset, bytes.length - offset);
                if (count < 0) throw new java.io.EOFException("Unexpected test file EOF.");
                offset += count;
            }
        }
        return bytes;
    }

    /** Diagnostic-only direct GCM check; reports exception class/message, never key or data. */
    private static void assertDirectLegacyVsecDecrypts(File encryptedFile, byte[] expected)
            throws Exception {
        byte[] encoded = readAll(encryptedFile);
        byte[] iv = null;
        byte[] ciphertextAndTag = null;
        byte[] plaintext = null;
        try {
            if (encoded.length <= CryptoManager.getLocalIvLength() + 16) {
                fail("Direct .vsec diagnostic: encrypted fixture is too short.");
            }
            iv = Arrays.copyOfRange(encoded, 0, CryptoManager.getLocalIvLength());
            ciphertextAndTag = Arrays.copyOfRange(
                    encoded, CryptoManager.getLocalIvLength(), encoded.length);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                    CryptoManager.getExistingKeyForSharing(),
                    new javax.crypto.spec.GCMParameterSpec(128, iv));
            plaintext = cipher.doFinal(ciphertextAndTag);
            assertArrayEquals("Direct .vsec GCM diagnostic must reproduce synthetic bytes",
                    expected, plaintext);
        } catch (Exception error) {
            String detail = error.getClass().getName();
            String message = error.getMessage();
            if (message != null && !message.isEmpty()) detail += ": " + message;
            fail("Direct .vsec GCM diagnostic failed (" + detail + ").");
        } finally {
            Arrays.fill(encoded, (byte) 0);
            if (iv != null) Arrays.fill(iv, (byte) 0);
            if (ciphertextAndTag != null) Arrays.fill(ciphertextAndTag, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static void writeAll(File file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
            out.flush();
            out.getFD().sync();
        }
    }

    private static Set<String> stagingNames(File dir) {
        Set<String> names = new HashSet<>();
        File[] files = dir.listFiles((parent, name) ->
                name.startsWith(".vshare-send-") && name.endsWith(".tmp"));
        if (files != null) for (File file : files) names.add(file.getName());
        return names;
    }

    @Test
    public void syntheticVsecCreatesRecipientBoundVshareAndRoundTrips() throws Exception {
        File dir = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir();
        String id = UUID.randomUUID().toString();
        File clearFile = new File(dir, "vshare-sender-clear-" + id + ".synthetic");
        File sourceVsec = new File(dir, "vshare-sender-source-" + id + ".vsec");
        File diagnosticPlain = new File(dir, "vshare-sender-diagnostic-" + id + ".synthetic");
        File destination = new File(dir, "vshare-sender-destination-" + id + ".vshare");
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(TEST_ALIAS);

        byte[] clear = new byte[131_117];
        byte[] recovered = null;
        byte[] fingerprint = null;
        RecipientEncryptionKeyManager recipient =
                new RecipientEncryptionKeyManager(TEST_ALIAS);
        String shareId = UUID.randomUUID().toString();
        try {
            new SecureRandom().nextBytes(clear);
            writeAll(clearFile, clear);
            assertTrue("Synthetic source must encrypt to legacy .vsec",
                    CryptoManager.encryptFile(clearFile, sourceVsec));

            // First isolate the Android Keystore/GCM operation using a full-buffer
            // read. The diagnostic reports only exception class/message, never secrets.
            assertDirectLegacyVsecDecrypts(sourceVsec, clear);

            // Then verify the existing non-streaming file decrypt path.
            assertTrue("Baseline CryptoManager .vsec decryption must succeed",
                    CryptoManager.decryptFile(sourceVsec, diagnosticPlain));
            byte[] diagnosticBytes = readAll(diagnosticPlain);
            try {
                assertArrayEquals("Baseline .vsec decryption must reproduce synthetic bytes",
                        clear, diagnosticBytes);
            } finally {
                Arrays.fill(diagnosticBytes, (byte) 0);
                diagnosticPlain.delete();
            }
            assertTrue("Plaintext fixture cleanup", clearFile.delete());

            recipient.ensureKeyPair();
            fingerprint = recipient.getPublicKeyFingerprint();
            VShareCodec.Metadata metadata = new VShareCodec.Metadata(
                    "sender-uid", "recipient-uid", SENDER_DEVICE, RECIPIENT_DEVICE,
                    ASSIGNMENT, shareId, RecipientEncryptionKeyManager.KEY_VERSION, fingerprint);
            VShareSender.create(sourceVsec, destination, metadata, recipient);
            assertTrue("Completed encrypted package must be published", destination.isFile());
            assertFalse("Plaintext fixture must remain absent", clearFile.exists());

            final ByteArrayOutputStream received = new ByteArrayOutputStream();
            final boolean[] committed = new boolean[1];
            final boolean[] aborted = new boolean[1];
            try (FileInputStream packageInput = new FileInputStream(destination)) {
                VShareCodec.readForRecipient(packageInput, new VShareCodec.OutputSink() {
                    @Override public void writeAuthenticatedChunk(byte[] chunk) {
                        received.write(chunk, 0, chunk.length);
                    }
                    @Override public void commit() { committed[0] = true; }
                    @Override public void abort() { aborted[0] = true; received.reset(); }
                }, "sender-uid", "recipient-uid", SENDER_DEVICE, RECIPIENT_DEVICE,
                        ASSIGNMENT, shareId, recipient);
            }

            recovered = received.toByteArray();
            assertArrayEquals("Recipient must recover exact synthetic source bytes", clear, recovered);
            assertTrue("Recipient sink commits only after footer authentication", committed[0]);
            assertFalse("Valid package must not abort recipient sink", aborted[0]);
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (recovered != null) Arrays.fill(recovered, (byte) 0);
            if (fingerprint != null) Arrays.fill(fingerprint, (byte) 0);
            clearFile.delete();
            diagnosticPlain.delete();
            sourceVsec.delete();
            destination.delete();
            keyStore.deleteEntry(TEST_ALIAS);
        }
    }

    @Test
    public void corruptedSourceVsecTagNeverPublishesVshareAndCleansStaging() throws Exception {
        File dir = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir();
        String id = UUID.randomUUID().toString();
        File clearFile = new File(dir, "vshare-sender-bad-clear-" + id + ".synthetic");
        File sourceVsec = new File(dir, "vshare-sender-bad-source-" + id + ".vsec");
        File destination = new File(dir, "vshare-sender-bad-destination-" + id + ".vshare");
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(TEST_ALIAS);
        byte[] clear = new byte[8193];
        byte[] encrypted = null;
        byte[] fingerprint = null;
        RecipientEncryptionKeyManager recipient =
                new RecipientEncryptionKeyManager(TEST_ALIAS);
        Set<String> stagingBefore = stagingNames(dir);
        try {
            new SecureRandom().nextBytes(clear);
            writeAll(clearFile, clear);
            assertTrue(CryptoManager.encryptFile(clearFile, sourceVsec));
            assertTrue(clearFile.delete());

            encrypted = readAll(sourceVsec);
            encrypted[encrypted.length - 1] ^= 0x01;
            writeAll(sourceVsec, encrypted);
            recipient.ensureKeyPair();
            fingerprint = recipient.getPublicKeyFingerprint();
            VShareCodec.Metadata metadata = new VShareCodec.Metadata(
                    "sender-uid", "recipient-uid", SENDER_DEVICE, RECIPIENT_DEVICE,
                    ASSIGNMENT, UUID.randomUUID().toString(),
                    RecipientEncryptionKeyManager.KEY_VERSION, fingerprint);
            try {
                VShareSender.create(sourceVsec, destination, metadata, recipient);
                fail("A source .vsec with a corrupted GCM tag must be rejected.");
            } catch (Exception expected) {
                // Expected: source GCM authentication fails before publication.
            }
            assertFalse("Failed source authentication must not publish .vshare", destination.exists());
            assertTrue("All newly-created sender staging files must be cleaned",
                    stagingNames(dir).equals(stagingBefore));
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
            if (fingerprint != null) Arrays.fill(fingerprint, (byte) 0);
            clearFile.delete();
            sourceVsec.delete();
            destination.delete();
            keyStore.deleteEntry(TEST_ALIAS);
        }
    }
}
