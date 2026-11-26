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
        boolean outputReserved = false;
        byte[] dek = null;
        byte[] wrappingKey = null;
        byte[] plaintextBuffer = null;
        byte[] salt = null, wrapIv = null, noncePrefix = null;
        byte[] uidBytes = null, objectBytes = null, wrapAadBytes = null, wrapInfoBytes = null;
        byte[] wrappedDek = null, header = null;
        try {
            validateArguments(inputFile, outputFile, ownerUid, objectId, recoverySecret);
            File parent = outputFile.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw new IOException("Cannot create output directory");
            }
            if (!outputFile.createNewFile()) throw new IOException("Output already exists");
            outputReserved = true;
            temp = File.createTempFile("vsb-", ".partial", parent);

            SecureRandom random = new SecureRandom();
            dek = new byte[KEY_BYTES];
            salt = new byte[SALT_BYTES];
            wrapIv = new byte[GCM_IV_BYTES];
            noncePrefix = new byte[NONCE_PREFIX_BYTES];
            random.nextBytes(dek);
            random.nextBytes(salt);
            random.nextBytes(wrapIv);
            random.nextBytes(noncePrefix);

            uidBytes = ownerUid.getBytes(StandardCharsets.UTF_8);
            objectBytes = objectId.getBytes(StandardCharsets.UTF_8);
            wrapAadBytes = wrapAad(uidBytes, objectBytes);
            wrapInfoBytes = wrapInfo(uidBytes, objectBytes);
            wrappingKey = hkdfSha256(recoverySecret, salt, wrapInfoBytes, KEY_BYTES);
            wrappedDek = aesGcmEncrypt(wrappingKey, wrapIv, wrapAadBytes, dek);
            header = makeHeader(salt, wrapIv, noncePrefix, uidBytes, objectBytes, wrappedDek);

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(inputFile)));
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
                out.write(header);
                plaintextBuffer = new byte[CHUNK_SIZE];
                int chunkCount = 0;
                long totalBytes = 0;
                int read;
                while ((read = readChunk(in, plaintextBuffer)) > 0) {
                    if (chunkCount == Integer.MAX_VALUE) throw new IOException("Backup has too many chunks");
                    byte[] plain = Arrays.copyOf(plaintextBuffer, read);
                    byte[] encrypted = null;
                    try {
                        byte[] nonce = chunkNonce(noncePrefix, chunkCount);
                        byte[] aad = chunkAad(header, chunkCount, read);
                        try {
                            encrypted = aesGcmEncrypt(dek, nonce, aad, plain);
                        } finally {
                            Arrays.fill(nonce, (byte) 0);
                            Arrays.fill(aad, (byte) 0);
                        }
                        out.writeInt(read);
                        out.write(encrypted);
                        if (totalBytes > Long.MAX_VALUE - read) {
                            throw new IOException("Backup size overflow");
                        }
                        totalBytes += read;
                        chunkCount++;
                    } finally {
                        Arrays.fill(plain, (byte) 0);
                        if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
                    }
                }
                byte[] footerPlain = footerPlain(chunkCount, totalBytes);
                byte[] footerCipher = null;
                try {
                    byte[] nonce = chunkNonce(noncePrefix, chunkCount);
                    byte[] aad = footerAad(header);
                    try {
                        footerCipher = aesGcmEncrypt(dek, nonce, aad, footerPlain);
                    } finally {
                        Arrays.fill(nonce, (byte) 0);
                        Arrays.fill(aad, (byte) 0);
                    }
                    out.writeInt(FOOTER_MARKER);
                    out.writeInt(chunkCount);
                    out.writeLong(totalBytes);
                    out.write(footerCipher);
                    out.flush();
                } finally {
                    Arrays.fill(footerPlain, (byte) 0);
                    if (footerCipher != null) Arrays.fill(footerCipher, (byte) 0);
                }
            }
            if (!temp.renameTo(outputFile)) throw new IOException("Cannot finalize backup file");
            outputReserved = false;
            return true;
        } catch (Exception e) {
            if (temp != null && temp.exists()) temp.delete();
            if (outputReserved && outputFile.exists()) outputFile.delete();
            return false;
        } finally {
            if (dek != null) Arrays.fill(dek, (byte) 0);
            if (wrappingKey != null) Arrays.fill(wrappingKey, (byte) 0);
            if (plaintextBuffer != null) Arrays.fill(plaintextBuffer, (byte) 0);
            if (salt != null) Arrays.fill(salt, (byte) 0);
            if (wrapIv != null) Arrays.fill(wrapIv, (byte) 0);
            if (noncePrefix != null) Arrays.fill(noncePrefix, (byte) 0);
            if (uidBytes != null) Arrays.fill(uidBytes, (byte) 0);
            if (objectBytes != null) Arrays.fill(objectBytes, (byte) 0);
            if (wrapAadBytes != null) Arrays.fill(wrapAadBytes, (byte) 0);
            if (wrapInfoBytes != null) Arrays.fill(wrapInfoBytes, (byte) 0);
            if (wrappedDek != null) Arrays.fill(wrappedDek, (byte) 0);
            if (header != null) Arrays.fill(header, (byte) 0);
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
        boolean outputReserved = false;
        byte[] dek = null;
        byte[] wrappingKey = null;
        Header h = null;
        byte[] uidBytes = null, objectBytes = null, wrapInfoBytes = null, wrapAadBytes = null;
        try {
            if (encryptedFile == null || !encryptedFile.isFile() || outputFile == null
                    || expectedOwnerUid == null || expectedOwnerUid.isEmpty()
                    || expectedObjectId == null || expectedObjectId.isEmpty()
                    || expectedOwnerUid.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES
                    || expectedObjectId.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES
                    || recoverySecret == null || recoverySecret.length != KEY_BYTES
                    || encryptedFile.getCanonicalFile().equals(outputFile.getCanonicalFile())) return false;
            File parent = outputFile.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) return false;
            if (!outputFile.createNewFile()) return false;
            outputReserved = true;
            temp = File.createTempFile("vsb-", ".partial", parent);

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(encryptedFile)));
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
                h = readHeader(in);
                uidBytes = expectedOwnerUid.getBytes(StandardCharsets.UTF_8);
                objectBytes = expectedObjectId.getBytes(StandardCharsets.UTF_8);
                if (!Arrays.equals(uidBytes, h.uid) || !Arrays.equals(objectBytes, h.objectId)) {
                    throw new IOException("Backup identity mismatch");
                }
                wrapInfoBytes = wrapInfo(h.uid, h.objectId);
                wrapAadBytes = wrapAad(h.uid, h.objectId);
                wrappingKey = hkdfSha256(recoverySecret, h.salt, wrapInfoBytes, KEY_BYTES);
                dek = aesGcmDecrypt(wrappingKey, h.wrapIv, wrapAadBytes, h.wrappedDek);
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
                        byte[] footer = null;
                        byte[] expectedFooter = footerPlain(declaredChunks, declaredBytes);
                        byte[] nonce = chunkNonce(h.noncePrefix, expectedIndex);
                        byte[] aad = footerAad(h.encoded);
                        try {
                            footer = aesGcmDecrypt(dek, nonce, aad, footerCipher);
                            if (!Arrays.equals(footer, expectedFooter)
                                    || declaredChunks != expectedIndex || declaredBytes != totalBytes
                                    || in.read() != -1) throw new IOException("Invalid backup footer");
                        } finally {
                            if (footer != null) Arrays.fill(footer, (byte) 0);
                            Arrays.fill(expectedFooter, (byte) 0);
                            Arrays.fill(footerCipher, (byte) 0);
                            Arrays.fill(nonce, (byte) 0);
                            Arrays.fill(aad, (byte) 0);
                        }
                        break;
                    }
                    if (length < 1 || length > CHUNK_SIZE || expectedIndex == Integer.MAX_VALUE) {
                        throw new IOException("Invalid chunk length");
                    }
                    byte[] cipherText = new byte[length + GCM_TAG_BYTES];
                    byte[] plain = null;
                    try {
                        in.readFully(cipherText);
                        byte[] nonce = chunkNonce(h.noncePrefix, expectedIndex);
                        byte[] aad = chunkAad(h.encoded, expectedIndex, length);
                        try {
                            plain = aesGcmDecrypt(dek, nonce, aad, cipherText);
                        } finally {
                            Arrays.fill(nonce, (byte) 0);
                            Arrays.fill(aad, (byte) 0);
                        }
                        if (plain.length != length) throw new IOException("Invalid chunk");
                        if (totalBytes > Long.MAX_VALUE - plain.length) {
                            throw new IOException("Restored file size overflow");
                        }
                        out.write(plain);
                        totalBytes += plain.length;
                        expectedIndex++;
                    } finally {
                        if (plain != null) Arrays.fill(plain, (byte) 0);
                        Arrays.fill(cipherText, (byte) 0);
                    }
                }
                out.flush();
            }
            if (!temp.renameTo(outputFile)) throw new IOException("Cannot publish restored file");
            outputReserved = false;
            return true;
        } catch (Exception e) {
            if (temp != null && temp.exists()) temp.delete();
            if (outputReserved && outputFile.exists()) outputFile.delete();
            return false;
        } finally {
            if (dek != null) Arrays.fill(dek, (byte) 0);
            if (wrappingKey != null) Arrays.fill(wrappingKey, (byte) 0);
            if (uidBytes != null) Arrays.fill(uidBytes, (byte) 0);
            if (objectBytes != null) Arrays.fill(objectBytes, (byte) 0);
            if (wrapInfoBytes != null) Arrays.fill(wrapInfoBytes, (byte) 0);
            if (wrapAadBytes != null) Arrays.fill(wrapAadBytes, (byte) 0);
            if (h != null) h.clear();
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
        if (input.getCanonicalFile().equals(output.getCanonicalFile())) {
            throw new IOException("Input and output must be different files");
        }
    }

    private static int readChunk(DataInputStream in, byte[] buffer) throws IOException {
        int count = 0;
        while (count < buffer.length) {
            int n = in.read(buffer, count, buffer.length - count);
            if (n < 0) break;
            if (n == 0) {
                int one = in.read();
                if (one < 0) break;
                buffer[count++] = (byte) one;
            } else {
                count += n;
            }
        }
        return count;
    }

    private static byte[] makeHeader(byte[] salt, byte[] wrapIv, byte[] noncePrefix,
                                     byte[] uid, byte[] objectId, byte[] wrappedDek) throws IOException {
        WipingByteArrayOutputStream bytes = new WipingByteArrayOutputStream();
        try {
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
        } finally {
            bytes.clear();
        }
    }

    private static Header readHeader(DataInputStream in) throws Exception {
        WipingByteArrayOutputStream bytes = new WipingByteArrayOutputStream();
        byte[] magic = new byte[MAGIC.length];
        byte[] salt = null, wrapIv = null, prefix = null, uid = null, objectId = null, wrapped = null;
        boolean transferred = false;
        try {
            DataOutputStream copy = new DataOutputStream(bytes);
            in.readFully(magic); copy.write(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Unknown backup format");
            int version = in.readInt(); copy.writeInt(version);
            int chunkSize = in.readInt(); copy.writeInt(chunkSize);
            if (version != VERSION || chunkSize != CHUNK_SIZE) throw new IOException("Unsupported backup version");
            salt = new byte[SALT_BYTES]; in.readFully(salt); copy.write(salt);
            wrapIv = new byte[GCM_IV_BYTES]; in.readFully(wrapIv); copy.write(wrapIv);
            prefix = new byte[NONCE_PREFIX_BYTES]; in.readFully(prefix); copy.write(prefix);
            int uidLength = in.readUnsignedShort(); copy.writeShort(uidLength);
            if (uidLength < 1 || uidLength > MAX_ID_BYTES) throw new IOException("Invalid owner id");
            uid = new byte[uidLength]; in.readFully(uid); copy.write(uid);
            int objectLength = in.readUnsignedShort(); copy.writeShort(objectLength);
            if (objectLength < 1 || objectLength > MAX_ID_BYTES) throw new IOException("Invalid object id");
            objectId = new byte[objectLength]; in.readFully(objectId); copy.write(objectId);
            int wrappedLength = in.readInt(); copy.writeInt(wrappedLength);
            if (wrappedLength != KEY_BYTES + GCM_TAG_BYTES) throw new IOException("Invalid wrapped key");
            wrapped = new byte[wrappedLength]; in.readFully(wrapped); copy.write(wrapped);
            copy.flush();
            Header h = new Header();
            h.salt = salt; h.wrapIv = wrapIv; h.noncePrefix = prefix;
            h.uid = uid; h.objectId = objectId; h.wrappedDek = wrapped; h.encoded = bytes.toByteArray();
            transferred = true;
            return h;
        } finally {
            Arrays.fill(magic, (byte) 0);
            bytes.clear();
            if (!transferred) {
                if (salt != null) Arrays.fill(salt, (byte) 0);
                if (wrapIv != null) Arrays.fill(wrapIv, (byte) 0);
                if (prefix != null) Arrays.fill(prefix, (byte) 0);
                if (uid != null) Arrays.fill(uid, (byte) 0);
                if (objectId != null) Arrays.fill(objectId, (byte) 0);
                if (wrapped != null) Arrays.fill(wrapped, (byte) 0);
            }
        }
    }

    private static final class WipingByteArrayOutputStream extends java.io.ByteArrayOutputStream {
        void clear() {
            Arrays.fill(buf, (byte) 0);
            reset();
        }
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
        try {
            int offset = 0;
            int counter = 1;
            while (offset < length) {
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));
                mac.update(previous);
                mac.update(info);
                mac.update((byte) counter++);
                byte[] next = mac.doFinal();
                Arrays.fill(previous, (byte) 0);
                previous = next;
                int copy = Math.min(previous.length, length - offset);
                System.arraycopy(previous, 0, result, offset, copy);
                offset += copy;
            }
            return result;
        } catch (Exception e) {
            Arrays.fill(result, (byte) 0);
            throw e;
        } finally {
            Arrays.fill(prk, (byte) 0);
            Arrays.fill(previous, (byte) 0);
        }
    }

    private static final class Header {
        byte[] salt, wrapIv, noncePrefix, uid, objectId, wrappedDek, encoded;

        void clear() {
            if (salt != null) Arrays.fill(salt, (byte) 0);
            if (wrapIv != null) Arrays.fill(wrapIv, (byte) 0);
            if (noncePrefix != null) Arrays.fill(noncePrefix, (byte) 0);
            if (uid != null) Arrays.fill(uid, (byte) 0);
            if (objectId != null) Arrays.fill(objectId, (byte) 0);
            if (wrappedDek != null) Arrays.fill(wrappedDek, (byte) 0);
            if (encoded != null) Arrays.fill(encoded, (byte) 0);
        }
    }
}
