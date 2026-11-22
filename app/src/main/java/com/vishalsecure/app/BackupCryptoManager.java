package com.vishalsecure.app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Separate versioned backup format. Does not read or write legacy .vsec files.
 *
 * The caller must supply a cryptographically random 32-byte Backup Recovery
 * Secret and keep it offline. It is intentionally distinct from the
 * one-time Owner Device Recovery Code.
 */
public final class BackupCryptoManager {
    private static final byte[] MAGIC = new byte[] {'V','S','B','A','C','K','U','P'};
    private static final int VERSION = 1;
    private static final int CHUNK_SIZE = 1024 * 1024;
    private static final int KEY_BYTES = 32;
    private static final int SALT_BYTES = 16;
    private static final int GCM_IV_BYTES = 12;
    private static final int NONCE_PREFIX_BYTES = 8;
    private static final int GCM_TAG_BYTES = 16;
    private static final int MAX_ID_BYTES = 1024;
    private static final int FOOTER_MARKER = -1;
    private static final byte[] FOOTER_TEXT = new byte[] {'V','S','E','N','D','1'};

    private BackupCryptoManager() { }

    /**
     * Encrypts a source file into a new .vsb backup object.
     * outputFile must not already exist. recoverySecret must be 32 random bytes.
     */
    public static boolean encryptFile(File inputFile, File outputFile,
                                      String ownerUid, String objectId,
                                      byte[] recoverySecret) {
        File temp = null;
        try {
            validateArguments(inputFile, outputFile, ownerUid, objectId, recoverySecret);
            if (outputFile.exists()) throw new IOException("Output already exists");
            File parent = outputFile.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw new IOException("Cannot create output directory");
            }
            temp = new File(parent, outputFile.getName() + ".partial");
            if (temp.exists() && !temp.delete()) throw new IOException("Cannot clear partial output");

            SecureRandom random = new SecureRandom();
            byte[] dek = new byte[KEY_BYTES];
            byte[] salt = new byte[SALT_BYTES];
            byte[] wrapIv = new byte[GCM_IV_BYTES];
            byte[] noncePrefix = new byte[NONCE_PREFIX_BYTES];
            random.nextBytes(dek);
            random.nextBytes(salt);
            random.nextBytes(wrapIv);
            random.nextBytes(noncePrefix);

            byte[] uidBytes = ownerUid.getBytes(StandardCharsets.UTF_8);
            byte[] objectBytes = objectId.getBytes(StandardCharsets.UTF_8);
            byte[] wrapAad = wrapAad(uidBytes, objectBytes);
            byte[] wrappingKey = hkdfSha256(recoverySecret, salt, wrapInfo(uidBytes, objectBytes), KEY_BYTES);
            byte[] wrappedDek = aesGcmEncrypt(wrappingKey, wrapIv, wrapAad, dek);
            byte[] header = makeHeader(salt, wrapIv, noncePrefix, uidBytes, objectBytes, wrappedDek);

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(inputFile)));
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
                out.write(header);
                byte[] buffer = new byte[CHUNK_SIZE];
                int chunkCount = 0;
                long totalBytes = 0;
                int read;
                while ((read = readChunk(in, buffer)) > 0) {
                    if (chunkCount == Integer.MAX_VALUE) throw new IOException("Backup has too many chunks");
                    byte[] plain = Arrays.copyOf(buffer, read);
                    byte[] encrypted = aesGcmEncrypt(dek, chunkNonce(noncePrefix, chunkCount),
                            chunkAad(header, chunkCount, read), plain);
                    out.writeInt(read);
                    out.write(encrypted);
                    totalBytes += read;
                    chunkCount++;
                    Arrays.fill(plain, (byte) 0);
                }
                byte[] footerPlain = footerPlain(chunkCount, totalBytes);
                byte[] footerCipher = aesGcmEncrypt(dek, chunkNonce(noncePrefix, chunkCount),
                        footerAad(header), footerPlain);
                out.writeInt(FOOTER_MARKER);
                out.writeInt(chunkCount);
                out.writeLong(totalBytes);
                out.write(footerCipher);
                out.flush();
                Arrays.fill(footerPlain, (byte) 0);
            }
            if (!temp.renameTo(outputFile)) throw new IOException("Cannot finalize backup file");
            Arrays.fill(dek, (byte) 0);
            Arrays.fill(wrappingKey, (byte) 0);
            return true;
        } catch (Exception e) {
            if (temp != null && temp.exists()) temp.delete();
            return false;
        }
    }

    /**
     * Authenticates and decrypts a .vsb object. A failed/truncated/tampered
     * object is deleted from the temporary destination and never published.
     */
    public static boolean decryptFile(File encryptedFile, File outputFile,
                                      String expectedOwnerUid, String expectedObjectId,
                                      byte[] recoverySecret) {
        File temp = null;
        byte[] dek = null;
        byte[] wrappingKey = null;
        try {
            if (encryptedFile == null || !encryptedFile.isFile() || outputFile == null
                    || expectedOwnerUid == null || expectedObjectId == null
                    || recoverySecret == null || recoverySecret.length != KEY_BYTES
                    || outputFile.exists()) return false;
            File parent = outputFile.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) return false;
            temp = new File(parent, outputFile.getName() + ".partial");
            if (temp.exists() && !temp.delete()) return false;

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(encryptedFile)));
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
                Header h = readHeader(in);
                byte[] uidBytes = expectedOwnerUid.getBytes(StandardCharsets.UTF_8);
                byte[] objectBytes = expectedObjectId.getBytes(StandardCharsets.UTF_8);
                if (!Arrays.equals(uidBytes, h.uid) || !Arrays.equals(objectBytes, h.objectId)) {
                    throw new IOException("Backup identity mismatch");
                }
                wrappingKey = hkdfSha256(recoverySecret, h.salt,
                        wrapInfo(h.uid, h.objectId), KEY_BYTES);
                dek = aesGcmDecrypt(wrappingKey, h.wrapIv, wrapAad(h.uid, h.objectId), h.wrappedDek);
                if (dek.length != KEY_BYTES) throw new IOException("Invalid data key");

                int expectedIndex = 0;
                long totalBytes = 0;
                while (true) {
                    int length = in.readInt();
                    if (length == FOOTER_MARKER) {
                        int declaredChunks = in.readInt();
                        long declaredBytes = in.readLong();
                        byte[] footerCipher = new byte[FOOTER_TEXT.length + 4 + 8 + GCM_TAG_BYTES];
                        in.readFully(footerCipher);
                        byte[] footer = aesGcmDecrypt(dek, chunkNonce(h.noncePrefix, expectedIndex),
                                footerAad(h.encoded), footerCipher);
                        if (!Arrays.equals(footer, footerPlain(declaredChunks, declaredBytes))
                                || declaredChunks != expectedIndex || declaredBytes != totalBytes
                                || in.read() != -1) throw new IOException("Invalid backup footer");
                        Arrays.fill(footer, (byte) 0);
                        break;
                    }
                    if (length < 1 || length > CHUNK_SIZE || expectedIndex == Integer.MAX_VALUE) {
                        throw new IOException("Invalid chunk length");
                    }
                    byte[] cipherText = new byte[length + GCM_TAG_BYTES];
                    in.readFully(cipherText);
                    byte[] plain = aesGcmDecrypt(dek, chunkNonce(h.noncePrefix, expectedIndex),
                            chunkAad(h.encoded, expectedIndex, length), cipherText);
                    if (plain.length != length) throw new IOException("Invalid chunk");
                    out.write(plain);
                    totalBytes += plain.length;
                    expectedIndex++;
                    Arrays.fill(plain, (byte) 0);
                }
                out.flush();
            }
            if (!temp.renameTo(outputFile)) throw new IOException("Cannot publish restored file");
            return true;
        } catch (Exception e) {
            if (temp != null && temp.exists()) temp.delete();
            return false;
        } finally {
            if (dek != null) Arrays.fill(dek, (byte) 0);
            if (wrappingKey != null) Arrays.fill(wrappingKey, (byte) 0);
        }
    }

    private static void validateArguments(File input, File output, String uid, String objectId, byte[] secret)
            throws IOException {
        if (input == null || !input.isFile() || output == null || uid == null || uid.isEmpty()
                || objectId == null || objectId.isEmpty() || secret == null || secret.length != KEY_BYTES) {
            throw new IOException("Invalid backup arguments");
        }
        if (uid.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES
                || objectId.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES) {
            throw new IOException("Backup identity is too long");
        }
    }

    private static int readChunk(DataInputStream in, byte[] buffer) throws IOException {
        int count = 0;
        while (count < buffer.length) {
            int n = in.read(buffer, count, buffer.length - count);
            if (n < 0) break;
            if (n == 0) continue;
            count += n;
        }
        return count;
    }

    private static byte[] makeHeader(byte[] salt, byte[] wrapIv, byte[] noncePrefix,
                                     byte[] uid, byte[] objectId, byte[] wrappedDek) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(CHUNK_SIZE);
        out.write(salt);
        out.write(wrapIv);
        out.write(noncePrefix);
        out.writeShort(uid.length);
        out.write(uid);
        out.writeShort(objectId.length);
        out.write(objectId);
        out.writeInt(wrappedDek.length);
        out.write(wrappedDek);
        out.flush();
        return bytes.toByteArray();
    }

    private static Header readHeader(DataInputStream in) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        DataOutputStream copy = new DataOutputStream(bytes);
        byte[] magic = new byte[MAGIC.length]; in.readFully(magic); copy.write(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("Unknown backup format");
        int version = in.readInt(); copy.writeInt(version);
        int chunkSize = in.readInt(); copy.writeInt(chunkSize);
        if (version != VERSION || chunkSize != CHUNK_SIZE) throw new IOException("Unsupported backup version");
        byte[] salt = new byte[SALT_BYTES]; in.readFully(salt); copy.write(salt);
        byte[] wrapIv = new byte[GCM_IV_BYTES]; in.readFully(wrapIv); copy.write(wrapIv);
        byte[] prefix = new byte[NONCE_PREFIX_BYTES]; in.readFully(prefix); copy.write(prefix);
        int uidLength = in.readUnsignedShort(); copy.writeShort(uidLength);
        if (uidLength < 1 || uidLength > MAX_ID_BYTES) throw new IOException("Invalid owner id");
        byte[] uid = new byte[uidLength]; in.readFully(uid); copy.write(uid);
        int objectLength = in.readUnsignedShort(); copy.writeShort(objectLength);
        if (objectLength < 1 || objectLength > MAX_ID_BYTES) throw new IOException("Invalid object id");
        byte[] objectId = new byte[objectLength]; in.readFully(objectId); copy.write(objectId);
        int wrappedLength = in.readInt(); copy.writeInt(wrappedLength);
        if (wrappedLength != KEY_BYTES + GCM_TAG_BYTES) throw new IOException("Invalid wrapped key");
        byte[] wrapped = new byte[wrappedLength]; in.readFully(wrapped); copy.write(wrapped);
        copy.flush();
        Header h = new Header();
        h.salt = salt; h.wrapIv = wrapIv; h.noncePrefix = prefix;
        h.uid = uid; h.objectId = objectId; h.wrappedDek = wrapped; h.encoded = bytes.toByteArray();
        return h;
    }

    private static byte[] wrapAad(byte[] uid, byte[] objectId) throws IOException {
        return concat(MAGIC, intBytes(VERSION), uid, new byte[] {0}, objectId);
    }
    private static byte[] wrapInfo(byte[] uid, byte[] objectId) {
        return concat("VishalSecure Backup DEK v1".getBytes(StandardCharsets.UTF_8),
                new byte[] {0}, uid, new byte[] {0}, objectId);
    }
    private static byte[] chunkAad(byte[] header, int index, int length) {
        return concat(header, intBytes(index), intBytes(length));
    }
    private static byte[] footerAad(byte[] header) {
        return concat(header, intBytes(FOOTER_MARKER));
    }
    private static byte[] footerPlain(int chunks, long bytes) {
        return concat(FOOTER_TEXT, intBytes(chunks), longBytes(bytes));
    }
    private static byte[] chunkNonce(byte[] prefix, int index) {
        return concat(prefix, intBytes(index));
    }
    private static byte[] intBytes(int value) {
        return new byte[] {(byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte)value};
    }
    private static byte[] longBytes(long value) {
        return new byte[] {(byte)(value >>> 56), (byte)(value >>> 48), (byte)(value >>> 40), (byte)(value >>> 32),
                (byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte)value};
    }
    private static byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] a : arrays) size += a.length;
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] a : arrays) { System.arraycopy(a, 0, result, offset, a.length); offset += a.length; }
        return result;
    }

    private static byte[] aesGcmEncrypt(byte[] key, byte[] iv, byte[] aad, byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(plain);
    }
    private static byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] aad, byte[] encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(encrypted);
    }

    /** RFC 5869 HKDF-SHA-256. */
    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        byte[] result = new byte[length];
        byte[] previous = new byte[0];
        int offset = 0;
        int counter = 1;
        while (offset < length) {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(previous);
            mac.update(info);
            mac.update((byte) counter++);
            previous = mac.doFinal();
            int copy = Math.min(previous.length, length - offset);
            System.arraycopy(previous, 0, result, offset, copy);
            offset += copy;
        }
        Arrays.fill(prk, (byte) 0);
        Arrays.fill(previous, (byte) 0);
        return result;
    }

    private static final class Header {
        byte[] salt, wrapIv, noncePrefix, uid, objectId, wrappedDek, encoded;
    }
}
