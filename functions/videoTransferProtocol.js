'use strict';

const crypto = require('node:crypto');

const MAX_CIPHERTEXT_BYTES = 500 * 1024 * 1024;
const OPERATIONS = new Set([
  'beginVideoUpload',
  'beginVideoDownload',
  'uploadVideoChunk',
  'downloadVideoChunk',
  'completeVideoUpload',
  'completeVideoDownload',
  'cancelVideoTransfer'
]);

function canonicalJson(value) {
  if (value === null || typeof value === 'string' || typeof value === 'boolean') {
    return JSON.stringify(value);
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) throw new TypeError('Only finite JSON numbers are allowed.');
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) return '[' + value.map(canonicalJson).join(',') + ']';
  if (value && typeof value === 'object') {
    if (Object.getPrototypeOf(value) !== Object.prototype && Object.getPrototypeOf(value) !== null) {
      throw new TypeError('Only plain JSON objects are allowed.');
    }
    return '{' + Object.keys(value).sort().map((key) =>
      JSON.stringify(key) + ':' + canonicalJson(value[key])
    ).join(',') + '}';
  }
  throw new TypeError('Payload must contain JSON values only.');
}

function sha256Canonical(value) {
  return crypto.createHash('sha256').update(canonicalJson(value), 'utf8').digest('hex');
}

function isUuid(value) {
  return typeof value === 'string'
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function requireKeys(payload, keys) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new TypeError('A JSON object payload is required.');
  }
  const actual = Object.keys(payload).sort();
  const expected = [...keys].sort();
  if (actual.length !== expected.length || actual.some((key, i) => key !== expected[i])) {
    throw new TypeError('Payload fields do not match the operation schema.');
  }
}

function requireHash(value, name) {
  if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) {
    throw new TypeError(name + ' must be a lowercase SHA-256 hex digest.');
  }
}

function requireSessionId(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{20,128}$/.test(value)) {
    throw new TypeError('Invalid transfer session ID.');
  }
}

function validateTransferProofPayload(operation, payload) {
  if (!OPERATIONS.has(operation)) throw new TypeError('Unsupported transfer operation.');
  if (!payload || payload.version !== 1) throw new TypeError('Unsupported transfer protocol version.');

  switch (operation) {
    case 'beginVideoUpload':
      requireKeys(payload, ['version', 'operation', 'assignmentId', 'direction', 'ciphertextLength', 'sha256', 'idempotencyKey']);
      if (payload.operation !== operation || payload.direction !== 'upload' || !isUuid(payload.assignmentId)
          || !isUuid(payload.idempotencyKey) || !Number.isSafeInteger(payload.ciphertextLength)
          || payload.ciphertextLength < 1 || payload.ciphertextLength > MAX_CIPHERTEXT_BYTES) {
        throw new TypeError('Invalid upload session payload.');
      }
      requireHash(payload.sha256, 'sha256');
      break;
    case 'beginVideoDownload':
      requireKeys(payload, ['version', 'operation', 'assignmentId', 'direction', 'idempotencyKey']);
      if (payload.operation !== operation || payload.direction !== 'download' || !isUuid(payload.assignmentId)
          || !isUuid(payload.idempotencyKey)) throw new TypeError('Invalid download session payload.');
      break;
    case 'uploadVideoChunk':
      requireKeys(payload, ['version', 'sessionId', 'assignmentId', 'direction', 'chunkIndex', 'chunkLength', 'chunkSha256']);
      if (!isUuid(payload.assignmentId) || payload.direction !== 'upload'
          || !Number.isSafeInteger(payload.chunkIndex) || payload.chunkIndex < 0
          || !Number.isSafeInteger(payload.chunkLength) || payload.chunkLength < 1
          || payload.chunkLength > 4 * 1024 * 1024) throw new TypeError('Invalid upload chunk payload.');
      requireSessionId(payload.sessionId);
      requireHash(payload.chunkSha256, 'chunkSha256');
      break;
    case 'downloadVideoChunk':
      requireKeys(payload, ['version', 'sessionId', 'assignmentId', 'direction', 'chunkIndex', 'rangeStart', 'rangeLength']);
      if (!isUuid(payload.assignmentId) || payload.direction !== 'download'
          || !Number.isSafeInteger(payload.chunkIndex) || payload.chunkIndex < 0
          || !Number.isSafeInteger(payload.rangeStart) || payload.rangeStart < 0
          || !Number.isSafeInteger(payload.rangeLength) || payload.rangeLength < 1
          || payload.rangeLength > 4 * 1024 * 1024) throw new TypeError('Invalid download chunk payload.');
      requireSessionId(payload.sessionId);
      break;
    case 'completeVideoUpload':
    case 'completeVideoDownload':
      requireKeys(payload, ['version', 'sessionId', 'assignmentId', 'direction', 'ciphertextLength', 'sha256', 'chunkCount']);
      if (!isUuid(payload.assignmentId) || payload.direction !== (operation === 'completeVideoUpload' ? 'upload' : 'download')
          || !Number.isSafeInteger(payload.ciphertextLength) || payload.ciphertextLength < 1
          || payload.ciphertextLength > MAX_CIPHERTEXT_BYTES
          || !Number.isSafeInteger(payload.chunkCount) || payload.chunkCount < 1
          || payload.chunkCount > Math.ceil(MAX_CIPHERTEXT_BYTES / (4 * 1024 * 1024))) {
        throw new TypeError('Invalid transfer completion payload.');
      }
      requireSessionId(payload.sessionId);
      requireHash(payload.sha256, 'sha256');
      break;
    case 'cancelVideoTransfer':
      requireKeys(payload, ['version', 'sessionId', 'assignmentId', 'direction']);
      if (!isUuid(payload.assignmentId) || !['upload', 'download'].includes(payload.direction)) {
        throw new TypeError('Invalid transfer cancellation payload.');
      }
      requireSessionId(payload.sessionId);
      break;
  }
  return true;
}

module.exports = {
  MAX_CIPHERTEXT_BYTES,
  TRANSFER_OPERATIONS: OPERATIONS,
  canonicalJson,
  sha256Canonical,
  validateTransferProofPayload
};
