# Vishal Secure — Recipient-Bound Video Sharing Encryption Design

Status: design proposal only. No Android implementation, Firebase deployment, Storage Rules change, or production key enrollment is authorized by this document.

Branch: `vs-4-owner-migration`

## 1. Goals and invariants

1. Preserve the existing local `.vsec` format and its `VishalSecureVideoKey` Android Keystore alias exactly.
2. Keep the existing owner-device P-256 signing key dedicated to signatures and device authorization. Do not reuse an ECDSA signing key as an encryption/agreement key.
3. Ensure the transfer service stores and transports ciphertext only. It must never receive plaintext video or a reusable content-encryption key (DEK).
4. Bind each shared package to the exact recipient phone/device selected at send time. Only that phone's non-exportable private encryption key can open it; another phone logged into the same Firebase account must not be able to open or download it.
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

## 4. Device replacement and recovery policy — strict device-only access

**User decision: a shared video must work only on the exact recipient phone to which it was sent.**

Therefore:
- The share is encrypted to the recipient phone's own non-exportable Keystore encryption private key.
- No other phone may open it, including another phone logged into the same Firebase account.
- Do not copy, export, sync, escrow, or rewrap the recipient private key to another device.
- If the recipient changes, resets, loses, or replaces that phone and its original private key is unavailable, that old share cannot be opened on the new phone.
- To use a new phone, the sender must create a fresh share addressed to the new phone's newly enrolled encryption public key. The old share is not migrated or recovered.
- Owner Device Recovery Code and Backup Recovery Secret do not override this rule and must not restore access to a recipient-bound share.

This is the strict device-binding policy. No server-side key escrow, cross-device key sync, recovery rewrap, or server-side plaintext decryption is allowed.

