package com.vishalsecure.app;

import android.system.Os;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Builds a recipient-bound .vshare package from an existing local .vsec file.
 * Plaintext is streamed only through bounded memory and immediately encrypted
 * into a private staging package. Publication occurs only after the source
 * .vsec GCM tag and the complete .vshare footer have both been authenticated.
 */
public final class VShareSender {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int IV_LENGTH = CryptoManager.getLocalIvLength();
    private static final int TAG_LENGTH = VShareCodec.GCM_TAG_BYTES;

    private VShareSender() { }

    /**
     * Creates a new .vshare file. The destination must not already exist.
     * The caller remains responsible for validating authorization/identity
     * with the backend before sharing this package.
     */
    public static void create(File sourceVsec, File destinationVshare,
                              VShareCodec.Metadata metadata,
                              RecipientEncryptionKeyManager recipientKeyManager)
            throws Exception {
        if (sourceVsec == null || destinationVshare == null ||
                metadata == null || recipientKeyManager == null) {
            throw new IllegalArgumentException("Missing sender input.");
        }
        if (!sourceVsec.isFile() || !sourceVsec.canRead()) {
            throw new IOException("Source .vsec file is unavailable.");
        }
        File source = sourceVsec.getCanonicalFile();
        File destination = destinationVshare.getAbsoluteFile();
        if (source.equals(destination)) throw new IOException("Source and destination must differ.");
        File parent = destination.getParentFile();
        if (parent == null) throw new IOException("Destination directory is missing.");
        if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Cannot create destination directory.");
        }
        if (destination.exists()) throw new IOException("Destination already exists.");

        long encryptedLength = source.length();
        long plaintextSize = encryptedLength - IV_LENGTH - TAG_LENGTH;
        if (plaintextSize < 1 || plaintextSize > VShareCodec.MAX_PLAINTEXT_SIZE) {
            throw new IOException("Invalid source .vsec length.");
        }

        File staging = File.createTempFile(".vshare-send-", ".tmp", parent);
        byte[] dek = new byte[RecipientEncryptionKeyManager.DEK_LENGTH_BYTES];
        byte[] wrappedDek = null;
        boolean published = false;
        try {
            new SecureRandom().nextBytes(dek);
            wrappedDek = recipientKeyManager.wrapDek(dek);
            if (wrappedDek == null || wrappedDek.length != VShareCodec.WRAPPED_DEK_BYTES) {
                throw new IOException("Recipient key returned an invalid wrapped DEK.");
            }

            try (AuthenticatedVsecInputStream plaintext =
                         new AuthenticatedVsecInputStream(source, plaintextSize);
                 FileOutputStream packageOutput = new FileOutputStream(staging, false)) {
                VShareCodec.write(plaintext, packageOutput, plaintextSize,
                        metadata, dek, wrappedDek);
                // VShareCodec.write reads once beyond the declared plaintext size;
                // that read forces source GCM doFinal() and tag verification.
                plaintext.requireAuthenticatedEnd();
                packageOutput.flush();
                packageOutput.getFD().sync();
            }

            if (!staging.isFile() || staging.length() <= 0) {
                throw new IOException("Encrypted staging package is empty.");
            }

            // link(2) creates the destination atomically and fails if it exists.
            // Both files are in the same directory/filesystem.
            Os.link(staging.getAbsolutePath(), destination.getAbsolutePath());
            published = true;
            if (!staging.delete()) throw new IOException("Could not remove encrypted staging link.");
        } catch (Exception failure) {
            if (published) {
                try { destination.delete(); } catch (Exception ignored) { }
            }
            throw failure;
        } finally {
            Arrays.fill(dek, (byte) 0);
            if (wrappedDek != null) Arrays.fill(wrappedDek, (byte) 0);
            if (staging.exists()) staging.delete();
        }
    }

    /**
     * Streaming .vsec decryptor. It never exposes EOF until GCM doFinal()
     * validates the original authentication tag and exact plaintext length.
     */
    private static final class AuthenticatedVsecInputStream extends InputStream {
        private final FileInputStream input;
        private final Cipher cipher;
        private final long expectedPlaintextSize;
        private byte[] pending = new byte[0];
        private int pendingOffset;
        private long emitted;
        private boolean finalized;
        private boolean closed;

        AuthenticatedVsecInputStream(File source, long plaintextSize) throws Exception {
            this.expectedPlaintextSize = plaintextSize;
            this.input = new FileInputStream(source);
            boolean success = false;
            try {
                byte[] iv = new byte[IV_LENGTH];
                readFully(input, iv);
                this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
                this.cipher.init(Cipher.DECRYPT_MODE, getExistingLocalKey(),
                        new GCMParameterSpec(128, iv));
                Arrays.fill(iv, (byte) 0);
                success = true;
            } finally {
                if (!success) input.close();
            }
        }

        private static SecretKey getExistingLocalKey() throws Exception {
            return CryptoManager.getExistingKeyForSharing();
        }

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override public int read(byte[] target, int offset, int length) throws IOException {
            if (closed) throw new IOException("Stream is closed.");
            if (target == null) throw new NullPointerException("target");
            if (offset < 0 || length < 0 || length > target.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) return 0;
            while (pendingOffset >= pending.length && !finalized) fill();
            if (pendingOffset >= pending.length) return -1;
            int count = Math.min(length, pending.length - pendingOffset);
            if (emitted + count > expectedPlaintextSize) {
                throw new IOException("Source .vsec plaintext exceeds its declared length.");
            }
            System.arraycopy(pending, pendingOffset, target, offset, count);
            pendingOffset += count;
            emitted += count;
            if (pendingOffset == pending.length) clearPending();
            return count;
        }

        private void fill() throws IOException {
            byte[] encrypted = new byte[BUFFER_SIZE];
            try {
                int count = input.read(encrypted);
                if (count >= 0) {
                    byte[] clear = cipher.update(encrypted, 0, count);
                    setPending(clear);
                    return;
                }
                try {
                    byte[] last = cipher.doFinal();
                    finalized = true;
                    setPending(last);
                    if (emitted + pending.length != expectedPlaintextSize) {
                        throw new IOException("Source .vsec plaintext length mismatch.");
                    }
                } catch (Exception authenticationFailure) {
                    throw new IOException("Source .vsec authentication failed.", authenticationFailure);
                }
            } finally {
                Arrays.fill(encrypted, (byte) 0);
            }
        }

        void requireAuthenticatedEnd() throws IOException {
            if (!finalized) {
                byte[] probe = new byte[1];
                if (read(probe, 0, 1) != -1) {
                    Arrays.fill(probe, (byte) 0);
                    throw new IOException("Unexpected extra source plaintext.");
                }
            }
            if (emitted != expectedPlaintextSize) {
                throw new IOException("Source .vsec authentication or length check incomplete.");
            }
        }

        private void setPending(byte[] value) {
            clearPending();
            pending = value == null ? new byte[0] : value;
            pendingOffset = 0;
        }

        private void clearPending() {
            Arrays.fill(pending, (byte) 0);
            pending = new byte[0];
            pendingOffset = 0;
        }

        @Override public void close() throws IOException {
            if (closed) return;
            closed = true;
            clearPending();
            input.close();
        }

        private static void readFully(InputStream input, byte[] value) throws IOException {
            int offset = 0;
            while (offset < value.length) {
                int count = input.read(value, offset, value.length - offset);
                if (count < 0) throw new IOException("Truncated .vsec IV.");
                offset += count;
            }
        }
    }
}
