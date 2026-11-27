package com.vishalsecure.app;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class VShareCodecTest {
    private static VShareCodec.Metadata metadata(byte[] fingerprint) {
        return new VShareCodec.Metadata("sender-uid", "recipient-uid",
                UUID.fromString("11111111-1111-4111-8111-111111111111").toString(),
                UUID.fromString("22222222-2222-4222-8222-222222222222").toString(),
                UUID.fromString("33333333-3333-4333-8333-333333333333").toString(),
                UUID.fromString("44444444-4444-4444-8444-444444444444").toString(),
                1, fingerprint);
    }

    private static final class MemorySink implements VShareCodec.OutputSink {
        final ByteArrayOutputStream clear = new ByteArrayOutputStream();
        boolean committed;
        boolean aborted;
        @Override public void writeAuthenticatedChunk(byte[] chunk) {
            clear.write(chunk, 0, chunk.length);
        }
        @Override public void commit() { committed = true; }
        @Override public void abort() { aborted = true; clear.reset(); }
    }

    @Test
    public void roundTripsMultiChunkPackageAndCommitsOnlyAfterFooter() throws Exception {
        byte[] clear = new byte[VShareCodec.CHUNK_SIZE + 137];
        byte[] dek = new byte[32];
        byte[] wrapped = new byte[VShareCodec.WRAPPED_DEK_BYTES];
        byte[] fingerprint = new byte[32];
        new SecureRandom().nextBytes(clear);
        new SecureRandom().nextBytes(dek);
        VShareCodec.Metadata m = metadata(fingerprint);
        ByteArrayOutputStream packageBytes = new ByteArrayOutputStream();
        VShareCodec.write(new ByteArrayInputStream(clear), packageBytes, clear.length, m, dek, wrapped);

        MemorySink sink = new MemorySink();
        VShareCodec.read(new ByteArrayInputStream(packageBytes.toByteArray()), sink,
                m.senderUid, m.recipientUid, m.senderDeviceId, m.recipientDeviceId,
                m.assignmentId, m.shareId, m.recipientKeyVersion, fingerprint, dek);
        assertArrayEquals(clear, sink.clear.toByteArray());
        assertEquals(true, sink.committed);
        assertEquals(false, sink.aborted);
        Arrays.fill(clear, (byte) 0);
        Arrays.fill(dek, (byte) 0);
    }

    @Test
    public void rejectsTamperedFooterAndAbortsSink() throws Exception {
        byte[] clear = new byte[] {1, 2, 3, 4, 5};
        byte[] dek = new byte[32];
        byte[] wrapped = new byte[VShareCodec.WRAPPED_DEK_BYTES];
        byte[] fingerprint = new byte[32];
        new SecureRandom().nextBytes(dek);
        VShareCodec.Metadata m = metadata(fingerprint);
        ByteArrayOutputStream packageBytes = new ByteArrayOutputStream();
        VShareCodec.write(new ByteArrayInputStream(clear), packageBytes, clear.length, m, dek, wrapped);
        byte[] tampered = packageBytes.toByteArray();
        tampered[tampered.length - 1] ^= 0x01;

        MemorySink sink = new MemorySink();
        try {
            VShareCodec.read(new ByteArrayInputStream(tampered), sink,
                    m.senderUid, m.recipientUid, m.senderDeviceId, m.recipientDeviceId,
                    m.assignmentId, m.shareId, m.recipientKeyVersion, fingerprint, dek);
            fail("Tampered footer must be rejected");
        } catch (Exception expected) {
            assertEquals(false, sink.committed);
            assertEquals(true, sink.aborted);
            assertEquals(0, sink.clear.size());
        }
        Arrays.fill(dek, (byte) 0);
    }

    @Test
    public void rejectsTrailingBytesAndIdentityMismatch() throws Exception {
        byte[] clear = new byte[] {9, 8, 7};
        byte[] dek = new byte[32];
        byte[] wrapped = new byte[VShareCodec.WRAPPED_DEK_BYTES];
        byte[] fingerprint = new byte[32];
        new SecureRandom().nextBytes(dek);
        VShareCodec.Metadata m = metadata(fingerprint);
        ByteArrayOutputStream packageBytes = new ByteArrayOutputStream();
        VShareCodec.write(new ByteArrayInputStream(clear), packageBytes, clear.length, m, dek, wrapped);
        byte[] trailing = Arrays.copyOf(packageBytes.toByteArray(), packageBytes.size() + 1);
        trailing[trailing.length - 1] = 0x55;
        MemorySink sink = new MemorySink();
        try {
            VShareCodec.read(new ByteArrayInputStream(trailing), sink,
                    m.senderUid, m.recipientUid, m.senderDeviceId, m.recipientDeviceId,
                    m.assignmentId, m.shareId, m.recipientKeyVersion, fingerprint, dek);
            fail("Trailing bytes must be rejected");
        } catch (Exception expected) {
            assertEquals(true, sink.aborted);
        }

        MemorySink identitySink = new MemorySink();
        try {
            VShareCodec.read(new ByteArrayInputStream(packageBytes.toByteArray()), identitySink,
                    "wrong-sender", m.recipientUid, m.senderDeviceId, m.recipientDeviceId,
                    m.assignmentId, m.shareId, m.recipientKeyVersion, fingerprint, dek);
            fail("Identity mismatch must be rejected");
        } catch (SecurityException expected) {
            assertEquals(true, identitySink.aborted);
        }
        Arrays.fill(dek, (byte) 0);
    }
}
