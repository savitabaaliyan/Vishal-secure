'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');

const MAGIC = Buffer.from('VSHARE01', 'ascii');
const VERSION = 1;
const CHUNK_SIZE = 1024 * 1024;
const MAX_PLAINTEXT = 500 * 1024 * 1024;
const CHUNK_AAD = Buffer.from('VishalSecure.vshare.chunk.v1', 'ascii');
const FOOTER_AAD = Buffer.from('VishalSecure.vshare.footer.v1', 'ascii');
const FOOTER_TEXT = Buffer.from('VSHAREEND1', 'ascii');

const FIXTURE = Object.freeze({
  key: Buffer.from(Array.from({ length: 32 }, (_, i) => i)),
  noncePrefix: Buffer.from('0102030405060708', 'hex'),
  senderUid: 'sender-test-uid',
  recipientUid: 'recipient-test-uid',
  senderDevice: '123e4567-e89b-42d3-a456-426614174000',
  recipientDevice: '123e4567-e89b-42d3-a456-426614174001',
  assignment: '123e4567-e89b-42d3-a456-426614174002',
  share: '123e4567-e89b-42d3-a456-426614174003',
  keyVersion: 1,
  fingerprint: Buffer.from(Array.from({ length: 32 }, (_, i) => 0xa0 + i)),
  wrappedDek: Buffer.alloc(384, 0x5a),
  plaintext: Buffer.concat([Buffer.from('Vishal Secure vshare multi-chunk test vector 1:', 'utf8'), Buffer.alloc(CHUNK_SIZE + 17, 0x6b)])
});

function u16(n) { const b = Buffer.alloc(2); b.writeUInt16BE(n); return b; }
function u32(n) { const b = Buffer.alloc(4); b.writeUInt32BE(n); return b; }
function u64(n) { const b = Buffer.alloc(8); b.writeBigUInt64BE(BigInt(n)); return b; }
function nonce(prefix, index) { return Buffer.concat([prefix, u32(index)]); }
function encryptGcm(key, iv, aad, plaintext) {
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
  cipher.setAAD(aad);
  return Buffer.concat([cipher.update(plaintext), cipher.final(), cipher.getAuthTag()]);
}
function decryptGcm(key, iv, aad, ciphertextAndTag) {
  if (ciphertextAndTag.length < 16) throw new Error('Truncated GCM record');
  const body = ciphertextAndTag.subarray(0, -16);
  const tag = ciphertextAndTag.subarray(-16);
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
  decipher.setAAD(aad);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(body), decipher.final()]);
}
function encodeHeader(f) {
  const sender = Buffer.from(f.senderUid, 'utf8');
  const recipient = Buffer.from(f.recipientUid, 'utf8');
  const fixed = [
    MAGIC, u16(VERSION), u32(0), u16(0), u32(CHUNK_SIZE),
    u64(f.plaintext.length), u32(Math.ceil(f.plaintext.length / CHUNK_SIZE)),
    u32(f.keyVersion), f.fingerprint, f.noncePrefix,
    u16(sender.length), sender, u16(recipient.length), recipient,
    Buffer.from(f.senderDevice, 'ascii'), Buffer.from(f.recipientDevice, 'ascii'),
    Buffer.from(f.assignment, 'ascii'), Buffer.from(f.share, 'ascii'),
    u16(f.wrappedDek.length), f.wrappedDek
  ];
  const headerLength = fixed.reduce((n, part) => n + part.length, 0);
  fixed[2] = u32(headerLength);
  return Buffer.concat(fixed);
}
function buildFixture() {
  const header = encodeHeader(FIXTURE);
  const headerHash = crypto.createHash('sha256').update(header).digest();
  const chunkCount = Math.ceil(FIXTURE.plaintext.length / CHUNK_SIZE);
  const chunks = [];
  for (let i = 0; i < chunkCount; i++) {
    const start = i * CHUNK_SIZE;
    const plain = FIXTURE.plaintext.subarray(start, Math.min(start + CHUNK_SIZE, FIXTURE.plaintext.length));
    const aad = Buffer.concat([CHUNK_AAD, headerHash, u32(i), u32(plain.length)]);
    const encrypted = encryptGcm(FIXTURE.key, nonce(FIXTURE.noncePrefix, i), aad, plain);
    chunks.push(Buffer.concat([Buffer.from([0x01]), u32(i), u32(plain.length), encrypted]));
  }
  const footerPlain = Buffer.concat([FOOTER_TEXT, u32(chunkCount), u64(FIXTURE.plaintext.length)]);
  const footerEncrypted = encryptGcm(
    FIXTURE.key, nonce(FIXTURE.noncePrefix, chunkCount),
    Buffer.concat([FOOTER_AAD, headerHash]), footerPlain
  );
  const footer = Buffer.concat([Buffer.from([0xff]), u32(chunkCount), u64(FIXTURE.plaintext.length), footerEncrypted]);
  return Buffer.concat([header, ...chunks, footer]);
}

function parsePackage(input, expected = {}) {
  const b = Buffer.from(input);
  let p = 0;
  function take(n) {
    if (!Number.isSafeInteger(n) || n < 0 || p + n > b.length) throw new Error('Truncated package');
    const v = b.subarray(p, p + n); p += n; return v;
  }
  function readU16() { const v = take(2).readUInt16BE(); return v; }
  function readU32() { const v = take(4).readUInt32BE(); return v; }
  function readU64() {
    const v = take(8).readBigUInt64BE();
    if (v > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('Unsafe integer');
    return Number(v);
  }
  function readText(n) {
    const bytes = take(n);
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  }

  if (!take(8).equals(MAGIC)) throw new Error('Bad magic');
  if (readU16() !== VERSION) throw new Error('Unsupported version');
  const headerLength = readU32();
  if (headerLength < 1 || headerLength > 8192 || headerLength > b.length) throw new Error('Invalid header length');
  if (readU16() !== 0) throw new Error('Unsupported flags');
  if (readU32() !== CHUNK_SIZE) throw new Error('Invalid chunk size');
  const plaintextSize = readU64();
  if (plaintextSize < 1 || plaintextSize > MAX_PLAINTEXT) throw new Error('Invalid plaintext size');
  const chunkCount = readU32();
  if (chunkCount !== Math.ceil(plaintextSize / CHUNK_SIZE) || chunkCount < 1 || chunkCount > 500) throw new Error('Invalid chunk count');
  const keyVersion = readU32();
  if (keyVersion < 1) throw new Error('Invalid key version');
  const fingerprint = take(32);
  const prefix = take(8);
  const senderLength = readU16();
  if (senderLength < 1 || senderLength > 1024) throw new Error('Invalid sender UID length');
  const senderUid = readText(senderLength);
  const recipientLength = readU16();
  if (recipientLength < 1 || recipientLength > 1024) throw new Error('Invalid recipient UID length');
  const recipientUid = readText(recipientLength);
  const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
  const senderDevice = take(36).toString('ascii');
  const recipientDevice = take(36).toString('ascii');
  const assignment = take(36).toString('ascii');
  const share = take(36).toString('ascii');
  for (const id of [senderDevice, recipientDevice, assignment, share]) {
    if (!uuidPattern.test(id)) throw new Error('Invalid UUID');
  }
  const wrappedLength = readU16();
  if (wrappedLength !== 384) throw new Error('Invalid wrapped DEK length');
  const wrappedDek = take(wrappedLength);
  if (p !== headerLength) throw new Error('Header length mismatch');
  const headerHash = crypto.createHash('sha256').update(b.subarray(0, headerLength)).digest();

  if (expected.senderUid !== undefined && senderUid !== expected.senderUid) throw new Error('Sender identity mismatch');
  if (expected.recipientUid !== undefined && recipientUid !== expected.recipientUid) throw new Error('Recipient identity mismatch');
  if (expected.recipientDevice !== undefined && recipientDevice !== expected.recipientDevice) throw new Error('Recipient device mismatch');
  if (expected.keyVersion !== undefined && keyVersion !== expected.keyVersion) throw new Error('Recipient key version mismatch');
  if (expected.fingerprint && !fingerprint.equals(expected.fingerprint)) throw new Error('Recipient key fingerprint mismatch');

  const key = expected.key;
  if (!Buffer.isBuffer(key) || key.length !== 32) throw new Error('Test DEK required');
  const plaintextParts = [];
  let total = 0;
  for (let i = 0; i < chunkCount; i++) {
    if (take(1)[0] !== 0x01) throw new Error('Invalid chunk record type');
    const index = readU32();
    if (index !== i) throw new Error('Chunk order mismatch');
    const length = readU32();
    const expectedLength = i === chunkCount - 1 ? plaintextSize - i * CHUNK_SIZE : CHUNK_SIZE;
    if (length < 1 || length > CHUNK_SIZE || length !== expectedLength) throw new Error('Invalid chunk length');
    const encrypted = take(length + 16);
    const aad = Buffer.concat([CHUNK_AAD, headerHash, u32(i), u32(length)]);
    plaintextParts.push(decryptGcm(key, nonce(prefix, i), aad, encrypted));
    total += length;
  }
  if (take(1)[0] !== 0xff) throw new Error('Missing footer');
  if (readU32() !== chunkCount) throw new Error('Footer chunk count mismatch');
  if (readU64() !== plaintextSize || total !== plaintextSize) throw new Error('Footer size mismatch');
  const footerEncrypted = take(38);
  const footerPlain = decryptGcm(key, nonce(prefix, chunkCount), Buffer.concat([FOOTER_AAD, headerHash]), footerEncrypted);
  const expectedFooter = Buffer.concat([FOOTER_TEXT, u32(chunkCount), u64(plaintextSize)]);
  if (!footerPlain.equals(expectedFooter)) throw new Error('Footer authentication payload mismatch');
  if (p !== b.length) throw new Error('Trailing bytes');
  return Buffer.concat(plaintextParts);
}

test('deterministic vshare v1 fixture round-trips with exact recipient binding', () => {
  const packageBytes = buildFixture();
  const opened = parsePackage(packageBytes, {
    key: FIXTURE.key,
    senderUid: FIXTURE.senderUid,
    recipientUid: FIXTURE.recipientUid,
    recipientDevice: FIXTURE.recipientDevice,
    keyVersion: FIXTURE.keyVersion,
    fingerprint: FIXTURE.fingerprint
  });
  assert.deepEqual(opened, FIXTURE.plaintext);
  assert.equal(packageBytes.readUInt32BE(10), packageBytes.subarray(0, packageBytes.readUInt32BE(10)).length);
  assert.equal(packageBytes.subarray(-38).length, 38);
});

test('vshare rejects modified ciphertext and footer authentication tags', () => {
  const original = buildFixture();
  const headerLength = original.readUInt32BE(10);
  const chunkCiphertextOffset = headerLength + 9;
  const badChunk = Buffer.from(original);
  badChunk[chunkCiphertextOffset] ^= 0x01;
  assert.throws(() => parsePackage(badChunk, { key: FIXTURE.key }));
  const badFooter = Buffer.from(original);
  badFooter[badFooter.length - 1] ^= 0x01;
  assert.throws(() => parsePackage(badFooter, { key: FIXTURE.key }));
});

test('vshare rejects wrong identities, unsupported version, and invalid header length', () => {
  const original = buildFixture();
  assert.throws(() => parsePackage(original, { key: FIXTURE.key, recipientUid: 'other-user' }), /identity mismatch/);
  const badVersion = Buffer.from(original);
  badVersion.writeUInt16BE(2, 8);
  assert.throws(() => parsePackage(badVersion, { key: FIXTURE.key }), /version/);
  const badHeaderLength = Buffer.from(original);
  badHeaderLength.writeUInt32BE(8193, 10);
  assert.throws(() => parsePackage(badHeaderLength, { key: FIXTURE.key }), /header length/);
});

test('vshare rejects reordered chunks, missing footer, and trailing bytes', () => {
  const original = buildFixture();
  const headerLength = original.readUInt32BE(10);
  const badIndex = Buffer.from(original);
  badIndex.writeUInt32BE(1, headerLength + 1);
  assert.throws(() => parsePackage(badIndex, { key: FIXTURE.key }), /order/);
  assert.throws(() => parsePackage(original.subarray(0, -43), { key: FIXTURE.key }));
  assert.throws(() => parsePackage(Buffer.concat([original, Buffer.from([0])]), { key: FIXTURE.key }), /Trailing/);
});

test('vshare rejects truncated records and invalid chunk lengths', () => {
  const original = buildFixture();
  assert.throws(() => parsePackage(original.subarray(0, 20), { key: FIXTURE.key }));
  const headerLength = original.readUInt32BE(10);
  const badLength = Buffer.from(original);
  badLength.writeUInt32BE(0, headerLength + 5);
  assert.throws(() => parsePackage(badLength, { key: FIXTURE.key }), /chunk length/);
});

test('vshare rejects a different recipient device even for the same account', () => {
  const original = buildFixture();
  assert.throws(() => parsePackage(original, {
    key: FIXTURE.key,
    recipientUid: FIXTURE.recipientUid,
    recipientDevice: '123e4567-e89b-42d3-a456-426614174099'
  }), /device mismatch/);
});


test('vshare rejects wrong recipient key version and public-key fingerprint', () => {
  const original = buildFixture();
  assert.throws(() => parsePackage(original, {
    key: FIXTURE.key,
    keyVersion: FIXTURE.keyVersion + 1
  }), /key version mismatch/);
  const wrongFingerprint = Buffer.from(FIXTURE.fingerprint);
  wrongFingerprint[0] ^= 0xff;
  assert.throws(() => parsePackage(original, {
    key: FIXTURE.key,
    fingerprint: wrongFingerprint
  }), /fingerprint mismatch/);
});

test('vshare rejects invalid magic, flags, and chunk size', () => {
  const original = buildFixture();
  const badMagic = Buffer.from(original);
  badMagic[0] ^= 0x01;
  assert.throws(() => parsePackage(badMagic, { key: FIXTURE.key }), /magic/);
  const badFlags = Buffer.from(original);
  badFlags.writeUInt16BE(1, 14);
  assert.throws(() => parsePackage(badFlags, { key: FIXTURE.key }), /flags/);
  const badChunkSize = Buffer.from(original);
  badChunkSize.writeUInt32BE(CHUNK_SIZE / 2, 16);
  assert.throws(() => parsePackage(badChunkSize, { key: FIXTURE.key }), /chunk size/);
});

test('vshare multi-chunk vector authenticates both chunks and footer', () => {
  const packageBytes = buildFixture();
  const headerLength = packageBytes.readUInt32BE(10);
  assert.equal(packageBytes.readUInt32BE(28), 2);
  assert.deepEqual(parsePackage(packageBytes, { key: FIXTURE.key }), FIXTURE.plaintext);
  const secondChunkIndexOffset = headerLength + 9 + FIXTURE.plaintext.length - 17 + 17;
  const reordered = Buffer.from(packageBytes);
  reordered.writeUInt32BE(0, secondChunkIndexOffset);
  assert.throws(() => parsePackage(reordered, { key: FIXTURE.key }), /order/);
});
