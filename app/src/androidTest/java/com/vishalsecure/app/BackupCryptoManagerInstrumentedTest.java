package com.vishalsecure.app;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BackupCryptoManagerInstrumentedTest {
    private Context context;
    private File testDir;
    private byte[] secret;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testDir = new File(context.getCacheDir(), "backup-atomic-tests");
        deleteRecursively(testDir);
        assertTrue(testDir.mkdirs());
        secret = new byte[32];
        new SecureRandom().nextBytes(secret);
    }

    @After
    public void tearDown() {
        if (secret != null) Arrays.fill(secret, (byte) 0);
        deleteRecursively(testDir);
    }

    @Test
    public void publishesEncryptedAndRestoredFilesOnDevice() throws Exception {
        byte[] original = new byte[1024 * 1024 + 73];
        new SecureRandom().nextBytes(original);
        File source = file("source.bin");
        File encrypted = file("backup.vsb");
        File restored = file("restored.bin");
        write(source, original);

        assertTrue(BackupCryptoManager.encryptFile(source, encrypted, "owner-device-test", "video-test", secret));
        assertTrue(encrypted.isFile());
        assertTrue(encrypted.length() > 0);
        assertTrue(BackupCryptoManager.decryptFile(encrypted, restored,
                "owner-device-test", "video-test", secret));
        assertArrayEquals(original, read(restored));
    }

    @Test
    public void refusesExistingDestinationWithoutChangingIt() throws Exception {
        File source = file("source.bin");
        File encrypted = file("backup.vsb");
        File restoreTarget = file("restored.bin");
        byte[] original = new byte[] {1, 3, 5, 7};
        byte[] existing = new byte[] {9, 8, 7, 6};
        write(source, original);
        assertTrue(BackupCryptoManager.encryptFile(source, encrypted,
                "owner-device-test", "video-test", secret));
        write(restoreTarget, existing);

        assertFalse(BackupCryptoManager.decryptFile(encrypted, restoreTarget,
                "owner-device-test", "video-test", secret));
        assertArrayEquals(existing, read(restoreTarget));
    }

    @Test
    public void failedAuthenticationDoesNotPublishPartialRestore() throws Exception {
        File source = file("source.bin");
        File encrypted = file("backup.vsb");
        File tampered = file("tampered.vsb");
        File output = file("restored.bin");
        byte[] original = new byte[8192];
        new SecureRandom().nextBytes(original);
        write(source, original);
        assertTrue(BackupCryptoManager.encryptFile(source, encrypted,
                "owner-device-test", "video-test", secret));

        byte[] bytes = read(encrypted);
        bytes[bytes.length - 1] ^= 0x40;
        write(tampered, bytes);

        assertFalse(BackupCryptoManager.decryptFile(tampered, output,
                "owner-device-test", "video-test", secret));
        assertFalse(output.exists());
    }

    private File file(String name) {
        return new File(testDir, name);
    }

    private static void write(File file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    private static byte[] read(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = in.read(bytes, offset, bytes.length - offset);
                if (count < 0) throw new java.io.IOException("Unexpected end of file");
                if (count == 0) continue;
                offset += count;
            }
        }
        return bytes;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
