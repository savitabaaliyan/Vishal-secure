package com.vishalsecure.app;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.Assert.*;

public class BackupCryptoManagerTest {
    private File tempDir() throws Exception {
        File dir = File.createTempFile("vsbackup", "test");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        return dir;
    }

    private byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new SecureRandom().nextBytes(data);
        return data;
    }

    private void roundTrip(int size) throws Exception {
        File dir = tempDir();
        File source = new File(dir, "source.bin");
        File encrypted = new File(dir, "object.vsb");
        File restored = new File(dir, "restored.bin");
        byte[] original = randomBytes(size);
        byte[] secret = randomBytes(32);
        try (FileOutputStream out = new FileOutputStream(source)) {
            out.write(original);
        }

        assertTrue(BackupCryptoManager.encryptFile(source, encrypted, "owner-uid", "video-123", secret));
        assertTrue(encrypted.isFile() && encrypted.length() > 0);
        assertTrue(BackupCryptoManager.decryptFile(encrypted, restored, "owner-uid", "video-123", secret));
        byte[] actual = new byte[(int) restored.length()];
        try (FileInputStream in = new FileInputStream(restored)) {
            int offset = 0, read;
            while ((read = in.read(actual, offset, actual.length - offset)) > 0) offset += read;
            assertEquals(actual.length, offset);
        }
        assertArrayEquals(original, actual);
    }

    @Test public void roundTripsEmptyFile() throws Exception { roundTrip(0); }
    @Test public void roundTripsAcrossMultipleChunks() throws Exception { roundTrip(2 * 1024 * 1024 + 37); }

    @Test public void rejectsWrongRecoverySecretAndWrongIdentity() throws Exception {
        File dir = tempDir();
        File source = new File(dir, "source.bin");
        File encrypted = new File(dir, "object.vsb");
        File wrongSecretOutput = new File(dir, "wrong-secret.bin");
        File wrongOwnerOutput = new File(dir, "wrong-owner.bin");
        byte[] secret = randomBytes(32);
        try (FileOutputStream out = new FileOutputStream(source)) { out.write(randomBytes(128)); }
        assertTrue(BackupCryptoManager.encryptFile(source, encrypted, "owner-uid", "video-123", secret));
        assertFalse(BackupCryptoManager.decryptFile(encrypted, wrongSecretOutput, "owner-uid", "video-123", randomBytes(32)));
        assertFalse(wrongSecretOutput.exists());
        assertFalse(BackupCryptoManager.decryptFile(encrypted, wrongOwnerOutput, "another-owner", "video-123", secret));
        assertFalse(wrongOwnerOutput.exists());
    }

    @Test public void rejectsTamperedBackupAndTruncation() throws Exception {
        File dir = tempDir();
        File source = new File(dir, "source.bin");
        File encrypted = new File(dir, "object.vsb");
        File tampered = new File(dir, "tampered.vsb");
        File truncated = new File(dir, "truncated.vsb");
        File output1 = new File(dir, "tampered-output.bin");
        File output2 = new File(dir, "truncated-output.bin");
        byte[] secret = randomBytes(32);
        try (FileOutputStream out = new FileOutputStream(source)) { out.write(randomBytes(4096)); }
        assertTrue(BackupCryptoManager.encryptFile(source, encrypted, "owner-uid", "video-123", secret));

        byte[] bytes = new byte[(int) encrypted.length()];
        try (FileInputStream in = new FileInputStream(encrypted)) {
            int offset = 0, read;
            while ((read = in.read(bytes, offset, bytes.length - offset)) > 0) offset += read;
            assertEquals(bytes.length, offset);
        }
        bytes[bytes.length - 1] ^= 0x01;
        try (FileOutputStream out = new FileOutputStream(tampered)) { out.write(bytes); }
        try (FileOutputStream out = new FileOutputStream(truncated)) { out.write(bytes, 0, bytes.length - 3); }

        assertFalse(BackupCryptoManager.decryptFile(tampered, output1, "owner-uid", "video-123", secret));
        assertFalse(BackupCryptoManager.decryptFile(truncated, output2, "owner-uid", "video-123", secret));
        assertFalse(output1.exists());
        assertFalse(output2.exists());
        Arrays.fill(secret, (byte) 0);
    }
}
