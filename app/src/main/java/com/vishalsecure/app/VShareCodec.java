package com.vishalsecure.app;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Strict streaming codec for the version-1 recipient-bound .vshare envelope.
 *
 * The Reader never writes plaintext itself. Its sink contract requires the
 * caller to encrypt each authenticated chunk at rest and to publish only from
 * commit(), which is called after footer authentication and exact EOF.
 */
public final class VShareCodec {
    public static final int CHUNK_SIZE = 1_048_576;
    public static final long MAX_PLAINTEXT_SIZE = 524_288_000L;
    public static final int MAX_CHUNKS = 500;
    public static final int MAX_HEADER_SIZE = 8192;
    public static final int GCM_TAG_BYTES = 16;
    public static final int WRAPPED_DEK_BYTES = 384;

    private static final byte[] MAGIC = "VSHARE01".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CHUNK_DOMAIN =
            "VishalSecure.vshare.chunk.v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FOOTER_DOMAIN =
            "VishalSecure.vshare.footer.v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FOOTER_TEXT =
            "VSHAREEND1".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final int FOOTER_CIPHERTEXT_BYTES = 38;

    private VShareCodec() { }

    /** Immutable, validated identity and key metadata for one share. */
    public static final class Metadata {
        public final String senderUid;
        public final String recipientUid;
        public final String senderDeviceId;
        public final String recipientDeviceId;
        public final String assignmentId;
        public final String shareId;
        public final int recipientKeyVersion;
        public final byte[] recipientKeyFingerprint;

        public Metadata(String senderUid, String recipientUid,
                        String senderDeviceId, String recipientDeviceId,
                        String assignmentId, String shareId,
                        int recipientKeyVersion, byte[] recipientKeyFingerprint) {
            this.senderUid = requireUid(senderUid, "senderUid");
            this.recipientUid = requireUid(recipientUid, "recipientUid");
            this.senderDeviceId = requireUuid(senderDeviceId, "senderDeviceId");
            this.recipientDeviceId = requireUuid(recipientDeviceId, "recipientDeviceId");
            this.assignmentId = requireUuid(assignmentId, "assignmentId");
            this.shareId = requireUuid(shareId, "shareId");
            if (recipientKeyVersion <= 0) throw new IllegalArgumentException("Invalid recipient key version.");
            if (recipientKeyFingerprint == null || recipientKeyFingerprint.length != 32) {
                throw new IllegalArgumentException("Recipient fingerprint must be 32 bytes.");
            }
            this.recipientKeyVersion = recipientKeyVersion;
            this.recipientKeyFingerprint = recipientKeyFingerprint.clone();
        }
    }

    /** Creates a complete package. Caller owns and must clear the DEK after use. */
    public static void write(InputStream plaintext, OutputStream packageOutput,
                             long plaintextSize, Metadata metadata,
                             byte[] dek, byte[] wrappedDek) throws Exception {
        if (plaintext == null || packageOutput == null || metadata == null) throw new IllegalArgumentException("Missing stream or metadata.");
        if (plaintextSize < 1 || plaintextSize > MAX_PLAINTEXT_SIZE) throw new IllegalArgumentException("Invalid plaintext size.");
        if (dek == null || dek.length != 32) throw new IllegalArgumentException("DEK must be 32 bytes.");
        if (wrappedDek == null || wrappedDek.length != WRAPPED_DEK_BYTES) throw new IllegalArgumentException("Wrapped DEK must be 384 bytes.");
        long countLong = (plaintextSize + CHUNK_SIZE - 1L) / CHUNK_SIZE;
        if (countLong < 1 || countLong > MAX_CHUNKS) throw new IllegalArgumentException("Invalid chunk count.");
        int chunkCount = (int) countLong;

        byte[] noncePrefix = new byte[8];
        new SecureRandom().nextBytes(noncePrefix);
        byte[] header = encodeHeader(plaintextSize, chunkCount, metadata, noncePrefix, wrappedDek);
        byte[] headerHash = sha256(header);
        DataOutputStream out = new DataOutputStream(packageOutput);
        out.write(header);

        byte[] buffer = new byte[CHUNK_SIZE];
        long total = 0;
        try {
            for (int index = 0; index < chunkCount; index++) {
                int length = (int) Math.min(CHUNK_SIZE, plaintextSize - total);
                readFully(plaintext, buffer, length);
                byte[] nonce = nonce(noncePrefix, index);
                byte[] aad = chunkAad(headerHash, index, length);
                byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, dek, nonce, aad, buffer, length);
                out.writeByte(0x01);
                out.writeInt(index);
                out.writeInt(length);
                out.write(encrypted);
                Arrays.fill(encrypted, (byte) 0);
                Arrays.fill(nonce, (byte) 0);
                Arrays.fill(aad, (byte) 0);
                Arrays.fill(buffer, 0, length, (byte) 0);
                total += length;
            }
            if (plaintext.read() != -1) throw new IOException("Plaintext input exceeds declared size.");
            byte[] footerPlain = ByteBuffer.allocate(22).order(ByteOrder.BIG_ENDIAN)
                    .put(FOOTER_TEXT).putInt(chunkCount).putLong(total).array();
            byte[] footerNonce = nonce(noncePrefix, chunkCount);
            byte[] footerAad = concat(FOOTER_DOMAIN, headerHash);
            byte[] footerEncrypted = crypt(Cipher.ENCRYPT_MODE, dek, footerNonce, footerAad, footerPlain, footerPlain.length);
            out.writeByte(0xFF);
            out.writeInt(chunkCount);
            out.writeLong(total);
            if (footerEncrypted.length != FOOTER_CIPHERTEXT_BYTES) throw new IllegalStateException("Unexpected footer size.");
            out.write(footerEncrypted);
            out.flush();
            Arrays.fill(footerPlain, (byte) 0);
            Arrays.fill(footerNonce, (byte) 0);
            Arrays.fill(footerAad, (byte) 0);
            Arrays.fill(footerEncrypted, (byte) 0);
        } finally {
            Arrays.fill(buffer, (byte) 0);
            Arrays.fill(headerHash, (byte) 0);
            Arrays.fill(noncePrefix, (byte) 0);
        }
    }

    /**
     * Reads and authenticates a package. The sink MUST encrypt chunks at rest;
     * it must not persist plaintext. abort() is called for every failure.
     */
    public static Metadata read(InputStream packageInput, OutputSink sink,
                                String expectedSenderUid, String expectedRecipientUid,
                                String expectedSenderDeviceId, String expectedRecipientDeviceId,
                                String expectedAssignmentId, String expectedShareId,
                                int expectedKeyVersion, byte[] expectedFingerprint,
                                byte[] dek) throws Exception {
        if (packageInput == null || sink == null || dek == null || dek.length != 32) throw new IllegalArgumentException("Missing input, sink, or DEK.");
        DataInputStream in = new DataInputStream(packageInput);
        boolean committed = false;
        try {
            byte[] prefix = new byte[14];
            in.readFully(prefix);
            ByteBuffer p = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN);
            byte[] magic = new byte[8]; p.get(magic);
            int version = p.getShort() & 0xffff;
            long headerLengthLong = p.getInt() & 0xffffffffL;
            if (!Arrays.equals(magic, MAGIC) || version != VERSION) throw new IOException("Unsupported .vshare format.");
            if (headerLengthLong < 100 || headerLengthLong > MAX_HEADER_SIZE) throw new IOException("Invalid header length.");
            byte[] header = new byte[(int) headerLengthLong];
            System.arraycopy(prefix, 0, header, 0, prefix.length);
            in.readFully(header, prefix.length, header.length - prefix.length);
            ParsedHeader parsed = parseHeader(header);
            Metadata m = parsed.metadata;
            requireEqual(m.senderUid, expectedSenderUid, "sender UID");
            requireEqual(m.recipientUid, expectedRecipientUid, "recipient UID");
            requireEqual(m.senderDeviceId, expectedSenderDeviceId, "sender device");
            requireEqual(m.recipientDeviceId, expectedRecipientDeviceId, "recipient device");
            requireEqual(m.assignmentId, expectedAssignmentId, "assignment");
            requireEqual(m.shareId, expectedShareId, "share");
            if (m.recipientKeyVersion != expectedKeyVersion ||
                    !MessageDigest.isEqual(m.recipientKeyFingerprint, expectedFingerprint)) {
                throw new SecurityException("Recipient key identity mismatch.");
            }
            byte[] headerHash = sha256(header);
            long total = 0;
            for (int index = 0; index < parsed.chunkCount; index++) {
                int type = in.readUnsignedByte();
                int recordIndex = in.readInt();
                long lengthLong = in.readInt() & 0xffffffffL;
                int expectedLength = (int) Math.min(CHUNK_SIZE, parsed.plaintextSize - total);
                if (type != 0x01 || recordIndex != index || lengthLong != expectedLength ||
                        lengthLong < 1 || lengthLong > CHUNK_SIZE) {
                    throw new IOException("Invalid chunk record.");
                }
                int length = (int) lengthLong;
                byte[] ciphertext = new byte[length + GCM_TAG_BYTES];
                in.readFully(ciphertext);
                byte[] nonce = nonce(parsed.noncePrefix, index);
                byte[] aad = chunkAad(headerHash, index, length);
                byte[] clear = crypt(Cipher.DECRYPT_MODE, dek, nonce, aad, ciphertext, ciphertext.length);
                try {
                    if (clear.length != length) throw new IOException("Invalid authenticated chunk length.");
                    sink.writeAuthenticatedChunk(clear);
                } finally {
                    Arrays.fill(ciphertext, (byte) 0);
                    Arrays.fill(nonce, (byte) 0);
                    Arrays.fill(aad, (byte) 0);
                    Arrays.fill(clear, (byte) 0);
                }
                total += length;
            }
            if (in.readUnsignedByte() != 0xFF) throw new IOException("Missing footer.");
            int footerCount = in.readInt();
            long footerTotal = in.readLong();
            if (footerCount != parsed.chunkCount || footerTotal != parsed.plaintextSize || total != parsed.plaintextSize) {
                throw new IOException("Footer totals do not match.");
            }
            byte[] footerCipher = new byte[FOOTER_CIPHERTEXT_BYTES];
            in.readFully(footerCipher);
            byte[] footerNonce = nonce(parsed.noncePrefix, parsed.chunkCount);
            byte[] footerAad = concat(FOOTER_DOMAIN, headerHash);
            byte[] footerClear = crypt(Cipher.DECRYPT_MODE, dek, footerNonce, footerAad, footerCipher, footerCipher.length);
            try {
                byte[] expectedFooter = ByteBuffer.allocate(22).order(ByteOrder.BIG_ENDIAN)
                        .put(FOOTER_TEXT).putInt(parsed.chunkCount).putLong(total).array();
                if (!MessageDigest.isEqual(footerClear, expectedFooter)) throw new SecurityException("Footer authentication content mismatch.");
                Arrays.fill(expectedFooter, (byte) 0);
            } finally {
                Arrays.fill(footerCipher, (byte) 0);
                Arrays.fill(footerNonce, (byte) 0);
                Arrays.fill(footerAad, (byte) 0);
                Arrays.fill(footerClear, (byte) 0);
                Arrays.fill(headerHash, (byte) 0);
            }
            if (in.read() != -1) throw new IOException("Trailing bytes after footer.");
            sink.commit();
            committed = true;
            return m;
        } finally {
            if (!committed) {
                try { sink.abort(); } catch (Exception ignored) { }
            }
        }
    }

    /** Encrypted-at-rest staging contract; implementations must never store clear chunks. */
    public interface OutputSink {
        void writeAuthenticatedChunk(byte[] plaintextChunk) throws Exception;
        void commit() throws Exception;
        void abort() throws Exception;
    }

    private static byte[] encodeHeader(long size, int count, Metadata m,
                                       byte[] noncePrefix, byte[] wrappedDek) throws Exception {
        byte[] sender = m.senderUid.getBytes(StandardCharsets.UTF_8);
        byte[] recipient = m.recipientUid.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(MAGIC); out.writeShort(VERSION); out.writeInt(0); // patched length below
        out.writeShort(0); out.writeInt(CHUNK_SIZE); out.writeLong(size); out.writeInt(count);
        out.writeInt(m.recipientKeyVersion); out.write(m.recipientKeyFingerprint); out.write(noncePrefix);
        out.writeShort(sender.length); out.write(sender);
        out.writeShort(recipient.length); out.write(recipient);
        out.write(m.senderDeviceId.getBytes(StandardCharsets.US_ASCII));
        out.write(m.recipientDeviceId.getBytes(StandardCharsets.US_ASCII));
        out.write(m.assignmentId.getBytes(StandardCharsets.US_ASCII));
        out.write(m.shareId.getBytes(StandardCharsets.US_ASCII));
        out.writeShort(wrappedDek.length); out.write(wrappedDek); out.flush();
        byte[] header = bytes.toByteArray();
        if (header.length > MAX_HEADER_SIZE) throw new IOException("Header too large.");
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).putInt(10, header.length);
        return header;
    }

    private static ParsedHeader parseHeader(byte[] header) throws Exception {
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(header));
        byte[] magic = new byte[8]; in.readFully(magic);
        int version = in.readUnsignedShort();
        long length = in.readInt() & 0xffffffffL;
        int flags = in.readUnsignedShort();
        long chunkSize = in.readInt() & 0xffffffffL;
        long size = in.readLong();
        long countLong = in.readInt() & 0xffffffffL;
        int keyVersion = in.readInt();
        byte[] fingerprint = new byte[32]; in.readFully(fingerprint);
        byte[] noncePrefix = new byte[8]; in.readFully(noncePrefix);
        String sender = readUid(in);
        String recipient = readUid(in);
        String senderDevice = readAsciiUuid(in);
        String recipientDevice = readAsciiUuid(in);
        String assignment = readAsciiUuid(in);
        String share = readAsciiUuid(in);
        int wrappedLength = in.readUnsignedShort();
        if (wrappedLength != WRAPPED_DEK_BYTES) throw new IOException("Invalid wrapped DEK length.");
        byte[] wrapped = new byte[wrappedLength]; in.readFully(wrapped);
        if (in.available() != 0 || length != header.length || version != VERSION || flags != 0 ||
                chunkSize != CHUNK_SIZE || size < 1 || size > MAX_PLAINTEXT_SIZE ||
                countLong != (size + CHUNK_SIZE - 1L) / CHUNK_SIZE || countLong > MAX_CHUNKS ||
                keyVersion <= 0 || !Arrays.equals(magic, MAGIC)) {
            throw new IOException("Invalid .vshare header.");
        }
        Metadata metadata = new Metadata(sender, recipient, senderDevice, recipientDevice,
                assignment, share, keyVersion, fingerprint);
        Arrays.fill(wrapped, (byte) 0);
        return new ParsedHeader(metadata, size, (int) countLong, noncePrefix);
    }

    private static String readUid(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length < 1 || length > 1024) throw new IOException("Invalid UID length.");
        byte[] value = new byte[length]; in.readFully(value);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid UTF-8 UID.", e);
        } finally { Arrays.fill(value, (byte) 0); }
    }

    private static String readAsciiUuid(DataInputStream in) throws IOException {
        byte[] value = new byte[36]; in.readFully(value);
        for (byte b : value) if (b < 0x20 || b > 0x7e) throw new IOException("Invalid UUID encoding.");
        String s = new String(value, StandardCharsets.US_ASCII);
        try { if (!UUID.fromString(s).toString().equals(s)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid canonical UUID.", e); }
        return s;
    }

    private static String requireUid(String value, String field) {
        if (value == null) throw new IllegalArgumentException("Missing " + field);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 1 || bytes.length > 1024) throw new IllegalArgumentException("Invalid " + field);
        return value;
    }

    private static String requireUuid(String value, String field) {
        if (value == null) throw new IllegalArgumentException("Missing " + field);
        try {
            if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid canonical " + field, e);
        }
        return value;
    }

    private static void requireEqual(String actual, String expected, String field) {
        if (expected == null || !actual.equals(expected)) throw new SecurityException("Unexpected " + field + ".");
    }

    private static byte[] crypt(int mode, byte[] dek, byte[] nonce, byte[] aad,
                                byte[] input, int length) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(input, 0, length);
    }

    private static byte[] nonce(byte[] prefix, int index) {
        return ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).put(prefix).putInt(index).array();
    }

    private static byte[] chunkAad(byte[] headerHash, int index, int length) {
        return concat(CHUNK_DOMAIN, headerHash,
                ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(index).putInt(length).array());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        return concat(concat(a, b), c);
    }

    private static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    private static void readFully(InputStream in, byte[] buffer, int length) throws IOException {
        int offset = 0;
        while (offset < length) {
            int n = in.read(buffer, offset, length - offset);
            if (n < 0) throw new EOFException("Plaintext input is truncated.");
            if (n == 0) continue;
            offset += n;
        }
    }

    private static final class ParsedHeader {
        final Metadata metadata;
        final long plaintextSize;
        final int chunkCount;
        final byte[] noncePrefix;
        ParsedHeader(Metadata metadata, long size, int count, byte[] prefix) {
            this.metadata = metadata; this.plaintextSize = size;
            this.chunkCount = count; this.noncePrefix = prefix;
        }
    }
}
