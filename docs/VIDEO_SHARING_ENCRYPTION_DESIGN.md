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
- Compatibility profile selected for investigation: RSA-3072 with RSA-OAEP using the main digest SHA-256 and MGF1 digest SHA-1 (OAEP SHA-256 / MGF1-SHA-1). This is deliberately explicit and avoids relying on provider defaults. The profile is a compatibility compromise, not an assertion that MGF1-SHA-1 is equally strong as SHA-256.
- Android Keystore's MGF1 digest configuration API is not uniformly available across the full API 24+ range. The implementation must explicitly request the selected OAEP parameters and prove that each supported API/provider actually honors them. Never silently fall back to a provider default or a different digest.
- If an API 24+ provider cannot generate/use this exact profile reliably, that Android version/device must fail closed; do not weaken parameters further. Revisit the minimum supported Android version only with the user's approval.
- RSA-3072 key generation, OAEP encryption/decryption, non-exportability, backup/restore behavior, and emulator plus physical-device compatibility remain unproven until tests pass.
- Only the public key is registered with the authenticated server. The server stores public key, key version, owning Firebase UID, device ID, status, and creation/revocation timestamps; it never stores the private key.
- Public-key registration must be authenticated and bound to the same active-device enrollment/replacement protocol. A recipient cannot substitute another user's key.

### 3.2 Per-share content encryption

For each new share:

1. Generate a fresh cryptographically random 256-bit DEK on the sender phone.
2. Stream-decrypt the sender's local `.vsec` and stream-encrypt the video using the DEK with AES-256-GCM in independently authenticated chunks. Do not create a plaintext video file on disk.
3. Use a unique nonce for every chunk under that DEK. Bind package version, assignment ID, recipient UID, sender UID, chunk index, plaintext length, and immutable header digest as authenticated additional data (AAD).
4. Wrap the DEK with the recipient's registered RSA public key using RSA-OAEP main digest SHA-256 and MGF1 digest SHA-1, with the exact OAEP parameters explicitly specified at both ends.
5. Put only the wrapped DEK, public metadata, chunk parameters, and encrypted video chunks in the `.vshare` package.
6. Clear transient key and plaintext buffers as soon as practical. Never log keys, plaintext, OTPs, recovery codes, or private-key material.

The package must have a strict versioned binary format, bounded lengths, authenticated header, explicit chunk count/total plaintext size, and an authenticated terminal record. Parsing must reject unknown versions, duplicate/invalid fields, truncation, reordered/duplicated chunks, oversized lengths, trailing bytes, and authentication failures. Exact byte-level format and test vectors are a prerequisite to coding.

### 3.3 Recipient open flow

- Recipient signs in to the verified Firebase account and must be the exact intended UID.
- Server confirms the current active device and returns the recipient-bound encrypted package plus wrapped DEK and metadata.
- Recipient unwraps the DEK using its own Keystore private key, authenticates every chunk, and writes the result only to a private temporary encrypted/local representation.
- The app must not publish a partial or unauthenticated result. On any failure, delete temporary output and fail closed.
- Playback integration must be designed so the recipient can play the shared content without changing legacy `.vsec` behavior. A recipient-local re-encryption step using the existing local `.vsec` key is a candidate; it must be tested for atomic file handling and must not leave plaintext on disk.

### 3.4 Legacy `.vsec` source handling — no plaintext staging

The current `CryptoManager.decryptFile()` implementation writes decrypted bytes to an output file while streaming and verifies the GCM tag only at `doFinal()`. It deletes that output on failure, but plaintext may exist temporarily on disk before final authentication. Therefore it must not be called as the sender-side sharing conversion path.

The sharing converter must instead:
- Read the legacy `.vsec` IV and initialize the existing Keystore AES-GCM decrypt cipher without changing the legacy format or key alias.
- Feed decrypted bytes from `Cipher.update()` directly in bounded memory buffers into the new `.vshare` encryption cipher; never write plaintext video to a file, cache, MediaStore, or cloud.
- Stage only encrypted `.vshare` bytes in a private temporary file. Do not make that file eligible for upload until the legacy source `doFinal()` authenticates successfully and the `.vshare` authenticated footer is complete.
- If either cipher authentication, I/O, or finalization fails, delete the staged encrypted output and clear transient buffers.
- Add tests proving no plaintext staging file is created, and that a tampered source `.vsec` cannot produce an accepted share.

This is a required design constraint. A bounded-memory implementation and tests must be completed before the sender UI or upload integration is added.

### 3.5 Exact `.vshare` version 1 binary layout (proposed)

All integer fields use unsigned big-endian/network byte order unless explicitly marked as signed. No padding or implicit alignment is permitted. The parser must enforce the exact field order and exact lengths below; it must not accept unknown fields or alternate encodings.

**Header (all bytes are authenticated as AAD by every chunk and the footer):**

| Field | Size | Required value / rule |
|---|---:|---|
| Magic | 8 bytes | ASCII `VSHARE01` |
| Format version | u16 | `1` |
| Header length | u32 | Exact total header byte length; maximum 8192 |
| Flags | u16 | `0` in version 1; reject any other value |
| Chunk size | u32 | Exactly 1,048,576 bytes (1 MiB) |
| Plaintext size | u64 | 1..524,288,000 bytes (500 MiB) |
| Chunk count | u32 | Exactly ceil(plaintext size / chunk size), maximum 500 |
| Recipient key version | u32 | Positive version from authenticated device-key registration |
| Recipient public-key fingerprint | 32 bytes | SHA-256 of canonical DER SubjectPublicKeyInfo |
| Nonce prefix | 8 bytes | Fresh random bytes for this share |
| Sender UID | u16 + bytes | UTF-8, 1..1024 bytes |
| Recipient UID | u16 + bytes | UTF-8, 1..1024 bytes |
| Sender device ID | 36 bytes | Lowercase canonical UUID ASCII |
| Recipient device ID | 36 bytes | Lowercase canonical UUID ASCII |
| Assignment ID | 36 bytes | Lowercase canonical UUID ASCII |
| Share ID | 36 bytes | Lowercase canonical UUID ASCII |
| Wrapped DEK length | u16 | Exactly 384 for RSA-3072 |
| Wrapped DEK | 384 bytes | RSA-OAEP main digest SHA-256, MGF1 digest SHA-1 |

The header length must equal the exact sum of these encoded fields. UID strings must be valid UTF-8, non-empty, and compared byte-for-byte with the authenticated expected identities. UUID fields must pass canonical lowercase UUID validation. The key fingerprint and key version must match the recipient device key registered by the trusted server; a package cannot select an arbitrary local key.

**Chunk records (exactly `chunkCount` records):**

| Field | Size | Rule |
|---|---:|---|
| Record type | u8 | `0x01` |
| Chunk index | u32 | Strictly 0, 1, 2, ... in order |
| Plaintext length | u32 | 1..1,048,576; all non-final chunks exactly 1 MiB |
| Ciphertext plus GCM tag | plaintext length + 16 | AES-256-GCM output |

Nonce for chunk `i` is the 8-byte header nonce prefix followed by the 4-byte big-endian chunk index. AAD is the fixed ASCII domain separator `VishalSecure.vshare.chunk.v1`, followed by SHA-256 of the exact encoded header bytes, then the 4-byte chunk index and 4-byte plaintext length. The final chunk length must make the sum of all plaintext lengths exactly equal the header plaintext size. The DEK is fresh for every share, so nonce reuse across different shares is not permitted or needed.

**Authenticated terminal record (exactly one, after all chunks):**

| Field | Size | Rule |
|---|---:|---|
| Record type | u8 | `0xFF` |
| Chunk count | u32 | Must equal header chunk count and parsed record count |
| Total plaintext size | u64 | Must equal header size and sum of authenticated chunk lengths |
| Footer ciphertext and tag | 28 bytes | AES-256-GCM encryption of ASCII `VSHAREEND1` (10 bytes) + u32 chunk count + u64 total plaintext size |

Footer nonce is the same 8-byte prefix followed by the 4-byte big-endian chunk count; this index is not used by any data chunk. Footer AAD is the fixed ASCII domain separator `VishalSecure.vshare.footer.v1` followed by SHA-256 of the exact encoded header bytes. The parser must require immediate EOF after the footer; trailing bytes are invalid. The footer authenticates even an otherwise empty record sequence, although version 1 disallows zero-length videos.

**Global bounds and rejection rules:**
- Maximum plaintext size: 500 MiB; maximum chunk count: 500; maximum header size: 8192 bytes.
- The ciphertext length for each chunk is derived from its validated plaintext length plus the 16-byte GCM tag; never allocate from an unvalidated length.
- Reject unsupported magic/version, non-zero flags, unexpected header length, invalid UTF-8/UUID, identity or key fingerprint mismatch, wrong chunk size/count, zero or oversized chunk length, wrong record type/index/order, truncated fields, any bad GCM tag, inconsistent byte totals, missing or duplicate footer, and trailing bytes.
- Check arithmetic for overflow before computing lengths or totals. Do not trust server-provided lengths in place of parsing and authenticating the package.
- A download is not eligible for local publication or playback until every chunk and the footer authenticate and the exact EOF check succeeds.

**Important implementation note:** this byte layout is a proposed v1 specification, not a claim of tested interoperability. Before implementing it, write independent golden test vectors and parser-negative tests, then validate the Android cryptographic provider behavior. Any change to this layout requires a new format version rather than silently changing version 1.

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
