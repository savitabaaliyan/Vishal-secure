package com.vishalsecure.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.crypto.Cipher;

/** Re-encrypts authenticated .vshare chunks into the unchanged local .vsec format. */
public final class VShareEncryptedFileSink implements VShareCodec.OutputSink {
    private final File destination;
    private final File stagingFile;
    private final Cipher cipher;
    private FileOutputStream output;
    private boolean finished;

    public VShareEncryptedFileSink(File destination) throws Exception {
        if (destination == null) throw new IllegalArgumentException("Destination is required.");
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null) throw new IOException("Destination parent is missing.");
        if (!parent.exists() && !parent.mkdirs() && !parent.exists()) throw new IOException("Cannot create destination directory.");
        if (destination.exists()) throw new IOException("Destination already exists.");
        this.destination = destination.getAbsoluteFile();
        this.stagingFile = File.createTempFile(".vshare-import-", ".vsec.tmp", parent);
        Cipher created = null;
        FileOutputStream stream = null;
        try {
            created = CryptoManager.createLocalEncryptionCipher();
            byte[] iv = created.getIV();
            if (iv == null || iv.length != CryptoManager.getLocalIvLength()) throw new IOException("Invalid local encryption IV.");
            stream = new FileOutputStream(stagingFile, false);
            try { stream.write(iv); } finally { Arrays.fill(iv, (byte) 0); }
            this.cipher = created;
            this.output = stream;
        } catch (Exception failure) {
            if (stream != null) try { stream.close(); } catch (Exception ignored) { }
            if (stagingFile.exists()) stagingFile.delete();
            throw failure;
        }
    }

    @Override public synchronized void writeAuthenticatedChunk(byte[] plaintextChunk) throws Exception {
        requireOpen();
        if (plaintextChunk == null || plaintextChunk.length == 0) throw new IOException("Empty authenticated chunk.");
        byte[] encrypted = cipher.update(plaintextChunk);
        if (encrypted != null) {
            try { if (encrypted.length > 0) output.write(encrypted); }
            finally { Arrays.fill(encrypted, (byte) 0); }
        }
    }

    @Override public synchronized void commit() throws Exception {
        requireOpen();
        try {
            byte[] finalBytes = cipher.doFinal();
            try { if (finalBytes != null && finalBytes.length > 0) output.write(finalBytes); }
            finally { if (finalBytes != null) Arrays.fill(finalBytes, (byte) 0); }
            output.flush();
            output.getFD().sync();
            output.close();
            output = null;
            if (destination.exists() || !stagingFile.renameTo(destination)) throw new IOException("Could not publish encrypted import.");
            finished = true;
        } catch (Exception failure) {
            abort();
            throw failure;
        }
    }

    @Override public synchronized void abort() throws Exception {
        if (finished) return;
        finished = true;
        if (output != null) { try { output.close(); } finally { output = null; } }
        if (stagingFile.exists() && !stagingFile.delete()) throw new IOException("Could not delete failed encrypted staging file.");
    }

    private void requireOpen() throws IOException {
        if (finished || output == null) throw new IOException("Encrypted import sink is closed.");
    }

    File getStagingFileForTest() { return stagingFile; }
}
