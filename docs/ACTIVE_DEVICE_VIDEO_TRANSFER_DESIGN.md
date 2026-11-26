# Active-Device-Gated Video Transfer Architecture

Status: Engineering design proposal only; not implemented or deployed.
Branch: `vs-4-owner-migration`.
Compatibility: preserve existing VS-3/VS-4 local encryption, playback and legacy `.vsec` format.

## 1. Architecture decision

Preferred target: a server-mediated streaming gateway (Cloud Run or equivalent) backed by a private Google Cloud Storage bucket. Do not rely on Firebase Storage Rules to verify Android public-key signatures: Storage Rules cannot verify the app's challenge signature or atomically consume a one-time challenge.

Android must not receive Firebase download-token URLs, long-lived signed URLs, public object URLs, or bucket credentials. The gateway verifies Firebase ID token, current active-device state, and fresh one-time device proof, then streams ciphertext through a bounded chunk protocol.

A Firestore authorization grant plus direct Firebase Storage SDK access is not the strict-revocation design. Storage Rules cannot atomically consume a grant; it remains replayable until expiry unless revoked. A resumable GCS session URI may itself act as a bearer capability after initiation. This fallback is acceptable only if a bounded revocation window is explicitly accepted and documented.

## 2. Current repository findings

Reviewed on 2026-11-26 on `vs-4-owner-migration`:
- `functions/index.js` exports `createVideoAssignment` and `finalizeVideoUpload`.
- Both functions require active-device proof for enrolled owners, but there is a compatibility path for accounts without an `ownerDevices/{uid}` record.
- `finalizeVideoUpload` checks object existence and size before marking an assignment active.
- Current Storage Rules authorize direct access to `videos/{assignmentId}/payload.vsec`: sender create while pending/unexpired; recipient get while active/unexpired; list/update/delete denied.
- Those rules do not verify active-device proof or a server-created transfer session.
- `app/build.gradle` declares Firebase Auth, Firestore and Functions, but not Firebase Storage.
- Reviewed `MainActivity.java` and `VideoPlayerActivity.java` contain local selection/encryption/playback and security UI, not a complete cloud transfer implementation.
- The reviewed files do not establish the actual Android transfer entry point. Locate all source files and confirm whether a transfer client exists before implementing; do not invent the integration point.
- Cloud backup is a separate, unimplemented feature. Do not combine backup objects with sharing assignments.

## 3. Trust boundaries and storage layout

Keep sharing and backup separate:
- Sharing metadata: existing `videoAssignments/{assignmentId}`.
- New private sharing object path (illustrative; server generates final path): `private-video-objects/{senderUid}/{assignmentId}/payload.vsec`.
- Server-only session records: `transferSessions/{sessionId}`.
- Server-only audit records: `transferAudit/{eventId}`.
- Backup records/objects use their own owner-scoped namespace and separate design.

All objects remain private. Do not create Firebase download tokens or return reusable bearer URLs. Do not store secrets in Firestore metadata.

## 4. Protocol

### Begin

Android obtains a fresh Firebase ID token and a one-time challenge from the owner-device challenge mechanism. The signed canonical payload binds:
- operation (`beginVideoUpload` or `beginVideoDownload`);
- assignment ID and direction;
- expected ciphertext length for upload or requested range for download;
- client idempotency key and protocol version.

The gateway verifies token UID, current active-device record, challenge UID/device/purpose/request hash, expiry, unused state and signature. Challenge consumption is atomic. It creates a random, short-lived session bound to one UID, device, assignment, direction and object.

For upload, independently verify sender role, pending status, assignment expiry, quota and size. For download, verify recipient role, active status, expiry and object existence.

### Stream

- Android sends/receives the already-encrypted payload; plaintext never enters the gateway.
- Use bounded chunks (initial target 4 MiB; finalize after testing), monotonically indexed.
- Each chunk request binds session ID, operation, chunk index, length and digest in its proof.
- Before each chunk, check session state, UID/device binding, expiry, expected chunk index, size limits and current `ownerDevices/{uid}.activeDeviceId`.
- Upload to a private temporary object. On finalization, verify total size, chunk count and server-computed SHA-256, then publish immutable object and update assignment metadata.
- Download streams ciphertext only and rechecks authorization before each chunk.
- Resume only while session is valid and same device remains active. Expired/revoked sessions cannot resume.
- Enforce timeouts, concurrency, quotas, max size and rate limits. Never buffer a complete 500 MiB object in memory.

### Finish/cancel

Finalization is a separate authenticated operation. Recheck device proof, session ownership/state, assignment state, stored size and server digest. Mark assignment active only after success. Cancellation/expiry makes session unusable and triggers idempotent temporary-object cleanup. Partial uploads must never be downloadable.

## 5. Revocation semantics

A revoked device cannot start another transfer or pass the next gateway chunk check. A request already in flight may finish its current bounded chunk; exposure is bounded by chunk size/request duration, not literally zero bytes.

A device may retain ciphertext already downloaded; if it also has decryption capability or extracted plaintext, server revocation cannot erase that offline copy. Do not promise remote erasure or prevention of external-camera recording. Use private/no-store response headers and disable intermediary caching.

## 6. Android components

Implement only after actual transfer entry points are found:
1. `VideoTransferRepository`: begin/upload/chunk/finalize/download/cancel; UI never constructs storage paths.
2. `TransferAuthProvider`: fresh Firebase ID token and one-time challenge.
3. `OwnerDeviceProofSigner`: existing Keystore private key; canonical payload signing; key never exported.
4. `EncryptedFileSource`: streams existing encrypted `.vsec` or versioned backup object without decrypting for upload.
5. `TransferHttpClient`: TLS, bounded streaming, idempotent retries, strict response validation; no URL token persistence.
6. `TransferProgressState`: progress/cancel; never log tokens, signatures, recovery secrets or file bytes.
7. `TransferDownloadSink`: private temporary file, size/hash verification, publish only on success, delete partial output on failure.
8. Unit/instrumentation tests: interrupted transfer, duplicate/out-of-order chunk, replay, expiry, wrong UID/role, revoke between chunks, overflow, digest mismatch and cleanup.

Do not add Firebase Storage SDK unless a reviewed decision changes this gateway design.

## 7. Server components

1. Transfer gateway with Firebase ID token verification and optional App Check verification.
2. Shared owner-device proof verifier and atomic one-time challenge consumption.
3. Assignment authorization module checking sender/recipient and current state.
4. Server-only session repository with short TTL, idempotency and states: created, uploading, finalizing, complete, cancelled, expired, rejected.
5. Private GCS adapter using service identity/IAM; no public ACLs/download tokens.
6. Quota/rate limits and audit events without secrets or video bytes.
7. Cleanup for expired temporary objects/sessions.
8. Auth/Firestore emulator and gateway integration tests.

Callable Functions are suitable for small control-plane actions, not memory-buffered transfer of large video files.

## 8. Migration plan

1. Leave existing `/videos/{assignmentId}/payload.vsec` rules unchanged during design.
2. Locate Android transfer UI/client and document its current data flow. If none exists, design the first end-to-end flow explicitly.
3. Implement gateway/private namespace behind a test-only feature flag on this migration branch.
4. Test synthetic encrypted files with emulators/test project; no real owner accounts or production deployment.
5. Define enrolled/non-enrolled compatibility. Never describe legacy direct Storage access as protected by owner-device revocation.
6. Test sender upload -> server finalize -> recipient download -> local playback.
7. Verify revoked sender cannot upload/finalize and revoked recipient cannot start/resume download.
8. Only after tests, prepare a separate rules/object migration proposal. Production deployment requires explicit owner approval.

## 9. Required test matrix

| Scenario | Expected |
|---|---|
| Active sender, pending assignment | Upload session allowed |
| Enrolled account with no active device | Denied |
| Wrong sender UID / recipient uploads | Denied |
| Expired assignment | Denied |
| Replayed challenge | Denied |
| Duplicate/out-of-order chunk | Rejected or safely idempotent; no corruption |
| Device revoked between chunks | Next chunk denied; session invalidated |
| Active recipient, active assignment | Download session allowed |
| Non-recipient download | Denied |
| Truncated upload/digest mismatch | Finalize denied; temp object cleaned |
| Partial download | No final file published |
| Long-lived download token requested | No token created/returned |
| Legacy assignment in staged rollout | Explicit compatibility policy; no false revocation guarantee |

## 10. Release blockers / non-goals

Release blockers: locate Android transfer flow; implement gateway/private objects; enforce active-device checks for every session and bounded chunk; pass revocation tests; eliminate public/token URL bypass; pass end-to-end sender/finalize/recipient/playback test; document legacy compatibility.

Non-goals: change legacy `.vsec` format, upload plaintext, store reusable decryption keys or Backup Recovery Secret server-side, treat email/phone login alone as device proof, promise deletion of offline copies, or deploy Firebase/change stable branch/signing configuration.

## 11. Implementation order

1. Inventory all Android source files and locate transfer entry points.
2. Freeze API contract and canonical signed payloads.
3. Add server tests for proof, roles, session state, expiry and revocation.
4. Implement gateway in non-production test configuration.
5. Implement Android streaming repository and instrumentation tests.
6. Run CI emulator and gateway integration tests.
7. Review security findings and compatibility before proposing any production rules/deployment.
