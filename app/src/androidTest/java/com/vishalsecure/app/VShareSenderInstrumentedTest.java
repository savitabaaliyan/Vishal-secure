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
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** End-to-end sender tests using synthetic content and isolated Keystore aliases only. */
@RunWith(AndroidJUnit4.class)
public class VShareSenderInstrumentedTest {
    private static final String TEST_ALIAS = "VishalSecureVShareSenderTestOnly";

    private static VShareCodec.Metadata metadata(byte[] fingerprint) {
        return new VShareCodec.Metadata(
                "sender-uid", "recipient-uid",
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222",
                "33333333-3333-4333-8333-333333333333",
                UUID.randomUUID().toString(),
                RecipientEncryptionKeyManager.KEY_VERSION, fingerprint);
    }

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

    private static void writeAll(File file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
            out.flush();
            out.getFD().sync();
        }
    }

    @Test
    public void syntheticVsecCreatesRecipientBoundVshareAndRoundTrips() throws Exception {
        File dir = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir();
        String id = UUID.randomUUID().toString();
        File clearFile = new File(dir, "vshare-sender-clear-" + id + ".synthetic");
        File sourceVsec = new File(dir, "vshare-sender-source-" + id + ".vsec");
        File destination = new File(dir, "vshare-sender-destination-" + id + ".vshare");
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(TEST_ALIAS);

        byte[] clear = new byte[131_117];
        byte[] recovered = null;
        RecipientEncryptionKeyManager recipient =
                new RecipientEncryptionKeyManager(TEST_ALIAS);
        try {
            new SecureRandom().nextBytes(clear);
            writeAll(clearFile, clear);
            assertTrue("Synthetic source must encrypt to the legacy .vsec format",
                    CryptoManager.encryptFile(clearFile, sourceVsec));
            clearFile.delete();

            recipient.ensureKeyPair();
            byte[] fingerprint = recipient.getPublicKeyFingerprint();
            try {
                VShareSender.create(sourceVsec, destination, metadata(fingerprint), recipient);
            } finally {
                Arrays.fill(fingerprint, (byte) 0);
            }
            assertTrue("Completed encrypted package must be published", destination.isFile());
            assertFalse("Plaintext fixture must be deleted", clearFile.exists());

            final ByteArrayOutputStream received = new ByteArrayOutputStream();
            final boolean[] committed = new boolean[1];
            final boolean[] aborted = new boolean[1];
            VShareCodec.readForRecipient(new FileInputStream(destination),
                    new VShareCodec.OutputSink() {
                        @Override public void writeAuthenticatedChunk(byte[] chunk) {
                            received.write(chunk, 0, chunk.length);
                        }
                        @Override public void commit() { committed[0] = true; }
                        @Override public void abort() { aborted[0] = true; received.reset(); }
                    }, "sender-uid", "recipient-uid",
                    "11111111-1111-4111-8111-111111111111",
                    "22222222-2222-4222-8222-222222222222",
                    "33333333-3333-4333-8333-333333333333",
                    // The share ID is validated against the authenticated package header.
                    readShareId(destination), recipient);

            recovered = received.toByteArray();
            assertArrayEquals("Recipient must recover exactly the synthetic source bytes", clear, recovered);
            assertTrue("Recipient sink must commit after footer authentication", committed[0]);
            assertFalse("Valid package must not abort recipient sink", aborted[0]);
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (recovered != null) Arrays.fill(recovered, (byte) 0);
            clearFile.delete();
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
        RecipientEncryptionKeyManager recipient =
                new RecipientEncryptionKeyManager(TEST_ALIAS);
        try {
            new SecureRandom().nextBytes(clear);
            writeAll(clearFile, clear);
            assertTrue(CryptoManager.encryptFile(clearFile, sourceVsec));
            clearFile.delete();

            encrypted = readAll(sourceVsec);
            encrypted[encrypted.length - 1] ^= 0x01;
            writeAll(sourceVsec, encrypted);
            recipient.ensureKeyPair();
            byte[] fingerprint = recipient.getPublicKeyFingerprint();
            try {
                VShareSender.create(sourceVsec, destination, metadata(fingerprint), recipient);
                fail("A source .vsec with a corrupted GCM tag must be rejected.");
            } catch (Exception expected) {
                // Source authentication must fail before destination publication.
            } finally {
                Arrays.fill(fingerprint, (byte) 0);
            }
            assertFalse("Failed source authentication must not publish a .vshare", destination.exists());
            File[] leftovers = dir.listFiles((parent, name) ->
                    name.startsWith(".vshare-send-") && name.endsWith(".tmp"));
            if (leftovers != null) {
                for (File leftover : leftovers) {
                    assertFalse("Sender staging file must be cleaned", leftover.exists()
                            && leftover.getName().contains(id));
                }
            }
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
            clearFile.delete();
            sourceVsec.delete();
            destination.delete();
            keyStore.deleteEntry(TEST_ALIAS);
        }
    }

    /** Extracts only the authenticated share ID field from the bounded header. */
    private static String readShareId(File packageFile) throws Exception {
        byte[] prefix = new byte[14];
        try (FileInputStream in = new FileInputStream(packageFile)) {
            int offset = 0;
            while (offset < prefix.length) {
                int count = in.read(prefix, offset, prefix.length - offset);
                if (count < 0) throw new java.io.EOFException("Truncated package header.");
                offset += count;
            }
            java.nio.ByteBuffer p = java.nio.ByteBuffer.wrap(prefix)
                    .order(java.nio.ByteOrder.BIG_ENDIAN);
            p.position(10);
            int headerLength = p.getInt();
            if (headerLength < prefix.length || headerLength > VShareCodec.MAX_HEADER_SIZE) {
                throw new java.io.IOException("Invalid package header length.");
            }
            byte[] header = new byte[headerLength];
            System.arraycopy(prefix, 0, header, 0, prefix.length);
            offset = prefix.length;
            while (offset < header.length) {
                int count = in.read(header, offset, header.length - offset);
                if (count < 0) throw new java.io.EOFException("Truncated package header.");
                offset += count;
            }
            // Layout tail contains four length-prefixed UUID strings and then
            // wrapped DEK; reuse codec parser through the public recipient path
            // by reading share ID at the known canonical UUID field offset.
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(header)
                    .order(java.nio.ByteOrder.BIG_ENDIAN);
            b.position(14 + 2 + 4 + 2 + 4 + 8 + 4 + 4 + 32 + 8);
            skipUtf8(b);
            skipUtf8(b);
            b.position(b.position() + 36 * 4);
            int wrappedLength = b.getShort() & 0xffff;
            if (wrappedLength != VShareCodec.WRAPPED_DEK_BYTES) {
                throw new java.io.IOException("Invalid wrapped key length.");
            }
            // shareId is the fourth UUID in the fixed UUID sequence.
            int uuidStart = b.position() - 2 - 36 * 4 + 36 * 3;
            return new String(header, uuidStart, 36, java.nio.charset.StandardCharsets.US_ASCII);
        }
    }

    private static void skipUtf8(java.nio.ByteBuffer buffer) throws Exception {
        int length = buffer.getShort() & 0xffff;
        if (length < 1 || length > 1024 || length > buffer.remaining()) {
            throw new java.io.IOException("Invalid UTF-8 metadata length.");
        }
        buffer.position(buffer.position() + length);
    }
}
