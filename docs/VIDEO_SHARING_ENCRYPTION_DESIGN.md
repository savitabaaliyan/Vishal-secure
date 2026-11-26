# Vishal Secure — Recipient-Bound Video Sharing Encryption Design

Status: design proposal only. No Android implementation, Firebase deployment, Storage Rules change, or production key enrollment is authorized by this document.

Branch: `vs-4-owner-migration`

## 1. Goals and invariants

1. Preserve the existing local `.vsec` format and its `VishalSecureVideoKey` Android Keystore alias exactly.
2. Keep the existing owner-device P-256 signing key dedicated to signatures and device authorization. Do not reuse an ECDSA signing key as an encryption/agreement key.
3. Ensure the transfer service stores and transports ciphertext only. It must never receive plaintext video or a reusable content-encryption key (DEK).
4. Bind each shared package to one verified recipient account and that account's currently authorized recipient encryption key/device.
5. Verify package version, recipient identity, object identity, ciphertext size, chunk ordering, and integrity before publishing a completed download.
6. Keep video sharing separate from backup recovery. The Backup Recovery Secret and Owner Device Recovery Code must not decrypt shared videos or authorize device replacement.
7. No automatic enrollment, Firebase deployment, public object URLs, signing changes, stable-branch changes, or release actions.

## 2. Why a second encrypted format is necessary

Legacy `.vsec` uses AES-GCM with a key held in the sender phone's Android Keystore. That key is device-local and is not available on the recipient phone. Uploading the existing bytes unchanged would therefore create a file the recipient cannot decrypt.

The legacy format must not be silently reinterpreted or modified. Sharing will use a distinct, explicitly versioned package (working name: `.vshare`). The sender's existing local `.vsec` remains untouched.

## 3. Proposed cryptographic envelope

### 3.1 Separate recipient encryption key

- Each enrolled device receives a separate recipient-encryption key pair, distinct from the existing P-256 signing key.
- Initial compatibility candidate: RSA-3072 with RSA-OAEP using SHA-256 and MGF1-SHA-256, generated and held by Android Keystore; only the public key is registered with the authenticated server.
- This is a candidate, not yet an approved implementation. The exact Android API 24+ provider behavior, Keystore support, OAEP parameters, backup/restore behavior, and emulator/device compatibility must be proven by tests before adoption.
- The server stores the public key, key version, owning Firebase UID, device ID, status, and creation/revocation timestamps. It never stores the private key.
- Public-key registration must be authenticated and bound to the same active-device enrollment/replacement protocol. A recipient cannot substitute another user's key.

### 3.2 Per-share content encryption

For each new share:
1. Generate a fresh cryptographically random 256-bit DEK on the sender phone.
2. Stream-decrypt the sender's local `.vsec` and stream-encrypt the video using the DEK with AES-256-GCM in independently authenticated chunks. Do not create a plaintext video file on disk.
3. Use a unique nonce for every chunk under that DEK. Bind package version, assignment ID, recipient UID, sender UID, chunk index, plaintext length, and immutable header digest as authenticated additional data (AAD).
4. Wrap the DEK with the recipient's registered RSA public key using the exact RSA-OAEP-SHA-256/MGF1-SHA-256 parameters.
5. Put only the wrapped DEK, public metadata, chunk parameters, and encrypted video chunks in the `.vshare` package.
6. Clear transient key and plaintext buffers as soon as practical. Never log keys, plaintext, OTPs, recovery codes, or private-key material.

The package must have a strict versioned binary format, bounded lengths, authenticated header, explicit chunk count/total plaintext size, and an authenticated terminal record. Parsing must reject unknown versions, duplicate/invalid fields, truncation, reordered/duplicated chunks, oversized lengths, trailing bytes, and authentication failures. Exact byte-level format and test vectors are a prerequisite to coding.

### 3.3 Recipient open flow

- Recipient signs in to the verified Firebase account and must be the exact intended UID.
- Server confirms the current active device and returns the recipient-bound encrypted package plus wrapped DEK and metadata.
- Recipient unwraps the DEK using its own Keystore private key, authenticates every chunk, and writes the result only to a private temporary encrypted/local representation.
- The app must not publish a partial or unauthenticated result. On any failure, delete temporary output and fail closed.
- Playback integration must be designed so the recipient can play the shared content without changing legacy `.vsec` behavior. A recipient-local re-encryption step using the existing local `.vsec` key is a candidate; it must be tested for atomic file handling and must not leave plaintext on disk.

## 4. Device replacement and recovery implications

Recipient-bound envelopes are bound to a particular device encryption public key. Replacing or losing that device can make previously downloaded or cloud-stored shares inaccessible unless a deliberate rewrap/recovery mechanism exists.

Before implementation, decide and threat-model one of these policies:
- **Strict device binding:** shares encrypted for the old device are not recoverable after device loss; sender must re-share to the new device.
- **User-controlled rewrap:** while the old device is available, it unwraps and rewraps the DEK for the new device after the normal old-device-approved replacement. For a lost device, a separately designed, user-held recovery mechanism is required; do not reuse either existing recovery code/secret without a separate security review.

No server-side key escrow or server-side plaintext decryption is allowed.

## 5. Server and transfer authorization

- Continue the server-mediated gateway design in `ACTIVE_DEVICE_VIDEO_TRANSFER_DESIGN.md`; private object storage only.
- Every begin, chunk, completion, and cancellation operation requires a fresh, payload-bound proof from the currently active device, plus Firebase authentication.
- Recheck active-device status on every chunk, not just when a session begins.
- Bind assignment to sender UID, recipient UID, recipient encryption-key ID/version, expiry, package version, ciphertext byte count, and SHA-256 digest.
- No public bucket, long-lived bearer download URL, direct client Storage path, or reusable transfer token.
- A revoked device must fail the next operation even if an assignment remains unexpired.
- Preserve and separately threat-model the existing legacy assignment flow; do not claim its current Storage Rules enforce active-device proof.

## 6. Required test gates before Android UI work

### Cryptographic unit tests
- RSA key generation and OAEP wrap/unwrap round trip using the exact declared parameters.
- Wrong key, modified wrapped DEK, modified header/AAD, modified ciphertext, bad tag, duplicate nonce rejection, missing/extra chunks, reordering, truncation, trailing bytes.
- Empty file, one-byte file, exact chunk boundary, boundary plus one byte, maximum permitted file, and integer/length overflow.
- Cross-version rejection and deterministic published test vectors for the package parser/serializer.
- Ensure no plaintext or key material appears in logs or server payloads.

### Android compatibility tests
- Minimum supported API 24 emulator and current supported Android emulator.
- Keystore key persistence across process death/reboot; behavior after app data clear/uninstall; hardware-backed status where available.
- Exact OAEP provider/parameter compatibility; do not silently fall back to weaker padding/digests.
- Recipient key rotation/revocation and replacement-device behavior.
- Crash-safe temporary-file cleanup and no partially published media.

### End-to-end tests (synthetic content only)
1. Sender signs in, selects a synthetic local `.vsec`, and creates a recipient-bound `.vshare`.
2. Gateway receives ciphertext only and stores it privately.
3. Wrong UID, wrong device, revoked device, expired assignment, altered payload, replayed proof, and invalid chunk are rejected.
4. Correct recipient downloads, verifies, unwraps, decrypts, and plays the synthetic clip.
5. Confirm sender's original `.vsec` is byte-for-byte unchanged.
6. Confirm the server never sees plaintext video or the DEK.
7. Confirm cancellation, expiry, interrupted upload/download, and retry behavior.

## 7. Implementation order and approval gates

1. Complete threat model and choose the lost-device policy in Section 4.
2. Freeze the binary `.vshare` format and cryptographic test vectors.
3. Implement and test the isolated Android crypto package on the feature branch only.
4. Implement server-side transfer gateway and authorization tests.
5. Implement sender/recipient Android screens and integration.
6. Run CI emulator tests using synthetic files.
7. Review all results before asking the user to perform physical-device tests.
8. Obtain explicit approval before any Firebase deployment, Storage Rules change, signing change, merge, or release.

## 8. Current status

This document records a proposed design and test plan only. It does not mean video sharing, recipient key enrollment, cloud transfer, recovery, or end-to-end playback has been implemented or tested.
