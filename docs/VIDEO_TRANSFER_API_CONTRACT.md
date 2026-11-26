# Video Transfer HTTP API Contract

Status: implementation contract proposal; no gateway or Firebase deployment is included in this commit.
Branch: `vs-4-owner-migration`.
Related design: `docs/ACTIVE_DEVICE_VIDEO_TRANSFER_DESIGN.md`.

## 1. Non-negotiable invariants

- Transfer only the existing encrypted `.vsec` bytes. The gateway never decrypts video.
- Keep the current `.vsec` format and Android Keystore video key unchanged.
- Do not use Firebase Storage SDK, Firebase download-token URLs, public URLs, long-lived signed URLs, bucket credentials on Android, or resumable-session bearer URLs.
- The private GCS bucket is accessible only to the gateway service identity.
- Every control request and every data chunk requires a fresh Firebase ID token and a fresh, single-use active-device proof. A session ID is an identifier, not a bearer credential.
- Recheck current `ownerDevices/{uid}.activeDeviceId` and device status before accepting or emitting each chunk.
- Enrolled accounts without a valid active device are denied. Legacy non-enrolled compatibility must not be silently described as revocation-protected.
- Backup objects and Backup Recovery Secret are out of scope and must use a separate API and namespace.

## 2. Common request authentication

Android obtains a fresh Firebase ID token using Firebase Auth. For each HTTP operation, Android first calls the existing `beginOwnerSensitiveRequest` callable with:
- `deviceId`: installation ID;
- `operation`: exact operation name listed below;
- `payload`: exact canonical payload for that HTTP operation.

The callable returns `challengeId`, `nonce`, `purpose`, `requestHash`, and expiry. Android signs UTF-8:

```text
challengeId.nonce.uid.deviceId.purpose.requestHash
```

using the existing non-exportable P-256 Android Keystore key. It sends these headers to the gateway:

- `Authorization: Bearer <Firebase ID token>`
- `X-VS-Device-Id: <installation ID>`
- `X-VS-Challenge-Id: <challenge ID>`
- `X-VS-Device-Signature: <base64 ECDSA signature>`
- `X-VS-Protocol: 1`

The gateway loads the challenge and device record from Firestore, checks UID, device ID, operation/purpose, request hash, unused state and expiry, verifies the signature, and atomically consumes the challenge. The gateway must reject missing owner/device records; there is no legacy bypass on transfer endpoints.

Canonical JSON is UTF-8 JSON with object keys sorted lexicographically at every depth, arrays kept in order, no insignificant whitespace, and finite JSON numbers only. Android and server must share test vectors before enabling the feature. Hash is lowercase hex SHA-256 of canonical JSON. Never sign a payload assembled from untrusted server-returned display strings.

## 3. Endpoints

All endpoints are HTTPS, JSON control plane except chunk bodies. The base URL is environment-specific and must not be hard-coded to production in the client.

### POST /v1/transfers

Create a short-lived transfer session.

Upload payload:
```json
{
  "version": 1,
  "operation": "beginVideoUpload",
  "assignmentId": "UUID",
  "direction": "upload",
  "ciphertextLength": 123456,
  "sha256": "lowercase-hex-sha256",
  "idempotencyKey": "random-client-uuid"
}
```

Download payload:
```json
{
  "version": 1,
  "operation": "beginVideoDownload",
  "assignmentId": "UUID",
  "direction": "download",
  "idempotencyKey": "random-client-uuid"
}
```

The same exact JSON object is the signed challenge payload. For upload, enforce length > 0 and <= 500 MiB. The server independently checks assignment sender, pending status and expiry. For download, independently check recipient, active status, expiry and finalized object metadata.

Success response (no secret URL or bearer token):
```json
{
  "sessionId": "random-unguessable-id",
  "chunkSize": 4194304,
  "chunkCount": 1,
  "expiresAtMillis": 0,
  "direction": "upload",
  "ciphertextLength": 123456
}
```

The session ID is not sufficient to authorize any operation. Repeating the same idempotency key and identical request returns the same live session; reusing it with different request data is rejected.

### PUT /v1/transfers/{sessionId}/chunks/{index}

Upload exactly one bounded raw ciphertext chunk. The challenge operation is `uploadVideoChunk`. Signed payload:
```json
{
  "version": 1,
  "sessionId": "session-id",
  "assignmentId": "UUID",
  "direction": "upload",
  "chunkIndex": 0,
  "chunkLength": 123456,
  "chunkSha256": "lowercase-hex-sha256"
}
```

The request body is the raw bytes; `Content-Length` must equal `chunkLength`. Enforce the negotiated chunk size, exact expected index, per-session total length and digest. Duplicate chunks are rejected unless the server can prove the exact same chunk was already committed; never append duplicate bytes. A failed/partial chunk does not advance the session.

### GET /v1/transfers/{sessionId}/chunks/{index}

Download one bounded raw ciphertext chunk. The challenge operation is `downloadVideoChunk`. Signed payload:
```json
{
  "version": 1,
  "sessionId": "session-id",
  "assignmentId": "UUID",
  "direction": "download",
  "chunkIndex": 0,
  "rangeStart": 0,
  "rangeLength": 123456
}
```

Before each chunk, re-check session owner/device binding, expiry, current active device, device status, recipient role, assignment active state and expiry. Respond with raw bytes and `Cache-Control: no-store, private`. Never return a storage URL or token. Do not enable intermediary caching.

### POST /v1/transfers/{sessionId}/complete

Upload completion challenge operation: `completeVideoUpload`. Signed payload:
```json
{
  "version": 1,
  "sessionId": "session-id",
  "assignmentId": "UUID",
  "direction": "upload",
  "ciphertextLength": 123456,
  "sha256": "lowercase-hex-sha256",
  "chunkCount": 1
}
```

Server verifies all expected chunks, total length, and server-computed SHA-256. It publishes the immutable private object and atomically marks the assignment active only after validation. The existing `finalizeVideoUpload` callable must not be used as an alternate bypass for the new private-object flow; retain its legacy behavior only for legacy object paths until migration is separately approved.

Download completion challenge operation: `completeVideoDownload`. Signed payload:
```json
{
  "version": 1,
  "sessionId": "session-id",
  "assignmentId": "UUID",
  "direction": "download",
  "ciphertextLength": 123456,
  "sha256": "lowercase-hex-sha256",
  "chunkCount": 1
}
```

This closes the session after the client has verified the full downloaded ciphertext. It does not grant permission to keep or decrypt data after expiry/revocation.

### DELETE /v1/transfers/{sessionId}

Cancel a session. Challenge operation: `cancelVideoTransfer`. Signed payload:
```json
{
  "version": 1,
  "sessionId": "session-id",
  "assignmentId": "UUID",
  "direction": "upload"
}
```

Cancellation is idempotent. Delete temporary objects asynchronously and safely. Expired/revoked sessions are unusable even if cleanup is delayed.

## 4. Session and object state

Session states: `created`, `uploading`, `finalizing`, `complete`, `cancelled`, `expired`, `rejected`.

- Initial chunk size target: 4 MiB; confirm against Android memory/network tests before release.
- Maximum ciphertext length: 500 MiB, matching the existing server-side upload limit.
- Session TTL target: 15 minutes, configurable server-side; expiry is checked on every request.
- One active transfer session per assignment/direction/device unless a reviewed idempotent resume rule explicitly allows more.
- Upload bytes first land in a private temporary object. Publish to the final private path only after full server-side length/hash verification.
- Final path is generated server-side under a private sharing namespace, not accepted from the client.
- No client can list, read, write, or delete GCS objects directly.
- Temporary objects and stale sessions need an idempotent cleanup job.

## 5. Required challenge allowlist update

The existing `beginOwnerSensitiveRequest` allowlist currently covers only the existing assignment/finalize/recovery operations. Before implementing gateway calls, extend it to exactly:
- `beginVideoUpload`
- `beginVideoDownload`
- `uploadVideoChunk`
- `downloadVideoChunk`
- `completeVideoUpload`
- `completeVideoDownload`
- `cancelVideoTransfer`

Do not use a generic wildcard operation. Every operation above must have a dedicated schema and payload validation.

## 6. Error contract

Use stable machine-readable error codes in JSON:
- `UNAUTHENTICATED`: missing/invalid Firebase ID token;
- `DEVICE_PROOF_INVALID`: invalid, expired, replayed, or wrong-operation proof;
- `DEVICE_REVOKED`: device is no longer the active owner device;
- `ASSIGNMENT_FORBIDDEN`: wrong sender/recipient role;
- `ASSIGNMENT_EXPIRED`: assignment expired;
- `SESSION_EXPIRED`: transfer session expired;
- `CHUNK_INVALID`: wrong index, size, or digest;
- `TRANSFER_INCOMPLETE`: missing chunks or size mismatch;
- `RATE_LIMITED`: quota or rate limit exceeded.

Do not return raw Firestore/GCS errors, credentials, internal paths, public keys, or stack traces to Android.

## 7. Security and compatibility tests

Before any deployment, test:
1. Invalid Firebase token and wrong UID.
2. Non-enrolled account denied on gateway endpoints.
3. Active-device mismatch and revoked device denied.
4. Challenge replay, expiry, wrong operation and altered payload denied.
5. Sender-only upload and recipient-only download.
6. Pending/active/expired assignment state checks.
7. Chunk too large, truncated, duplicated, out-of-order and digest mismatch.
8. Device revoked between chunks: next chunk is denied.
9. Session ID alone cannot authorize any endpoint.
10. Interrupted upload never publishes a partial final object.
11. Interrupted download never publishes a partial local `.vsec` file.
12. No download-token URL, public URL, signed URL or bucket credential is issued.
13. Full sender upload -> server finalize -> recipient download -> local playback test using synthetic encrypted test data.

## 8. Implementation boundary

This contract does not implement or deploy the gateway, change Firebase Storage Rules, change the existing legacy object path, change Android local encryption/playback, or touch signing keys/secrets. Those are separate reviewed steps. No production deployment or stable-branch merge is authorized by this document.
