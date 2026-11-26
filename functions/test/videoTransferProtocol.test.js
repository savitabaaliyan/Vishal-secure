'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
  canonicalJson,
  sha256Canonical,
  validateTransferProofPayload,
  MAX_CIPHERTEXT_BYTES
} = require('../videoTransferProtocol');

const assignmentId = '123e4567-e89b-42d3-a456-426614174000';
const idempotencyKey = '123e4567-e89b-42d3-a456-426614174001';
const sessionId = 'session_identifier_1234567890';
const digest = 'a'.repeat(64);

test('canonical JSON sorts keys recursively and preserves array order', () => {
  assert.equal(canonicalJson({ z: 1, a: { y: 2, b: 3 }, list: [2, 1] }),
    '{"a":{"b":3,"y":2},"list":[2,1],"z":1}');
  assert.equal(sha256Canonical({ b: 2, a: 1 }), sha256Canonical({ a: 1, b: 2 }));
});

test('canonical JSON rejects non-JSON values and non-finite numbers', () => {
  assert.throws(() => canonicalJson({ n: Number.NaN }));
  assert.throws(() => canonicalJson({ fn() {} }));
});

test('valid upload session payload is accepted', () => {
  assert.equal(validateTransferProofPayload('beginVideoUpload', {
    version: 1, operation: 'beginVideoUpload', assignmentId, direction: 'upload',
    ciphertextLength: 500, sha256: digest, idempotencyKey
  }), true);
});

test('upload session rejects oversize and unknown fields', () => {
  assert.throws(() => validateTransferProofPayload('beginVideoUpload', {
    version: 1, operation: 'beginVideoUpload', assignmentId, direction: 'upload',
    ciphertextLength: MAX_CIPHERTEXT_BYTES + 1, sha256: digest, idempotencyKey
  }));
  assert.throws(() => validateTransferProofPayload('beginVideoUpload', {
    version: 1, operation: 'beginVideoUpload', assignmentId, direction: 'upload',
    ciphertextLength: 500, sha256: digest, idempotencyKey, storagePath: 'public/path'
  }));
});

test('valid download session payload is accepted', () => {
  assert.equal(validateTransferProofPayload('beginVideoDownload', {
    version: 1, operation: 'beginVideoDownload', assignmentId,
    direction: 'download', idempotencyKey
  }), true);
});

test('chunk schemas enforce direction, bounded size and digest', () => {
  assert.equal(validateTransferProofPayload('uploadVideoChunk', {
    version: 1, sessionId, assignmentId, direction: 'upload',
    chunkIndex: 0, chunkLength: 1024, chunkSha256: digest
  }), true);
  assert.throws(() => validateTransferProofPayload('uploadVideoChunk', {
    version: 1, sessionId, assignmentId, direction: 'upload',
    chunkIndex: 0, chunkLength: 5 * 1024 * 1024, chunkSha256: digest
  }));
  assert.equal(validateTransferProofPayload('downloadVideoChunk', {
    version: 1, sessionId, assignmentId, direction: 'download',
    chunkIndex: 0, rangeStart: 0, rangeLength: 1024
  }), true);
});

test('completion and cancellation payloads are validated strictly', () => {
  assert.equal(validateTransferProofPayload('completeVideoUpload', {
    version: 1, sessionId, assignmentId, direction: 'upload',
    ciphertextLength: 1024, sha256: digest, chunkCount: 1
  }), true);
  assert.equal(validateTransferProofPayload('cancelVideoTransfer', {
    version: 1, sessionId, assignmentId, direction: 'download'
  }), true);
  assert.throws(() => validateTransferProofPayload('completeVideoDownload', {
    version: 1, sessionId, assignmentId, direction: 'upload',
    ciphertextLength: 1024, sha256: digest, chunkCount: 1
  }));
});

test('unknown operations and protocol versions are rejected', () => {
  assert.throws(() => validateTransferProofPayload('arbitraryOperation', {}));
  assert.throws(() => validateTransferProofPayload('beginVideoDownload', {
    version: 2, operation: 'beginVideoDownload', assignmentId,
    direction: 'download', idempotencyKey
  }));
});
