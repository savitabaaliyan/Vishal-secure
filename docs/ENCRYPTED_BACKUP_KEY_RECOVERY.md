# Vishal Secure — Encrypted Backup & Key Recovery Design

Status: Design proposal; not deployed and not yet connected to the Android app.
Work branch: `vs-4-owner-migration`
Compatibility baseline: preserve the existing local-only VS-3/VS-4 behavior until migration is explicitly completed.

## 1. Security objective

Allow an owner to restore their encrypted videos and owner metadata on a replacement Android phone, including when the old phone is unavailable, without uploading plaintext video or a reusable plaintext encryption key.

The Firebase UID remains the permanent owner identity. Device replacement authorization and backup decryption are separate controls:
- Owner-device recovery proves that the account owner may activate a new phone.
- Backup-key recovery proves that the owner possesses the separate backup recovery secret needed to unwrap video keys.

A successful device replacement must not, by itself, decrypt any backup.

## 2. Existing format and migration constraint

The current `CryptoManager` uses one AES/GCM key named `VishalSecureVideoKey` in Android Keystore. The encrypted file format is a 12-byte IV followed by AES-GCM ciphertext/tag. The key is device-bound and cannot be copied to a new phone.

Consequences:
- Existing `.vsec` files can only be migrated while the old installation still has access to its Keystore key.
- A new phone cannot decrypt existing files merely by signing into the same Firebase UID.
- If the old phone is already lost and there is no prior backup/key envelope, those legacy local-only encrypted files cannot be recovered by this new design. Never promise otherwise.

Do not change the existing `CryptoManager` format in place. Introduce a versioned backup format and a separate migration path.

## 3. Proposed cryptographic envelope

Use a random, independent 256-bit Data Encryption Key (DEK) per video (or per immutable backup object).

For each video:
1. Generate a cryptographically random 32-byte DEK on the Android device.
2. Encrypt the video with AES-256-GCM using a fresh random 12-byte IV. Authenticate a canonical header as AAD: format version, owner UID, object ID, plaintext size, and crypto suite.
3. Wrap the DEK locally using a key derived from the owner's offline Backup Recovery Secret. Use HKDF-SHA-256 with a fresh random salt and a domain-separation label such as `VishalSecure/backup-wrap/v1`; use AES-256-GCM with a separate random 12-byte wrapping IV. Bind UID, object ID, and format version as AAD.
4. Upload only the encrypted video object and the wrapped DEK envelope. Never upload the DEK, derived wrapping key, recovery secret, or plaintext video.
5. On restore, the owner enters/imports the backup recovery secret on the new phone. Derive the wrapping key locally, authenticate/decrypt the wrapped DEK, then decrypt the video locally. Clear transient byte buffers where practical and never log secrets.

The recovery secret must be independent of Firebase password, phone OTP, installation ID, and the one-time device-replacement recovery code. Generate it with at least 256 bits of cryptographic entropy and show it only once. Prefer a human-manageable encoding with a checksum (for example, a carefully specified word-list encoding); do not invent a weak memorable password or derive the key from a short PIN. A future design may support a user-selected passphrase only with a deliberately reviewed memory-hard KDF and clear strength warnings.

The existing device replacement Recovery Code remains a separate one-time authorization credential. It must not be reused as the video encryption key or as the backup recovery secret.

## 4. Versioned backup object

Proposed logical metadata (illustrative schema, not literal source code):
- `formatVersion`: integer, initially 1
- `ownerUid`: Firebase UID
- `objectId`: random immutable identifier
- `cipherSuite`: AES-256-GCM
- `contentIv`: 12-byte IV, base64url
- `wrappedDek`: ciphertext + GCM tag, base64url
- `wrapIv`: 12-byte IV, base64url
- `kdf`: HKDF-SHA-256
- `kdfSalt`: random salt, base64url
- `keyVersion`: integer for future rotation
- `ciphertextSize`, `plaintextSize`
- `ciphertextSha256`: digest of uploaded ciphertext for transport/integrity checking
- `createdAt`, `updatedAt`

The authenticated GCM tag is the cryptographic authenticity check. The SHA-256 digest is an additional transport/storage integrity check, not a substitute for GCM authentication. Reject unknown versions, invalid lengths, mismatched UID/object IDs, oversized objects, and failed authentication.

Large videos require streaming encryption/decryption and a defined chunked-AEAD format rather than loading an entire file into memory. Each chunk must have a unique nonce derived safely from a per-object random nonce prefix and monotonic chunk index, with object ID, version, and chunk index authenticated as AAD. Define crash-safe chunk ordering and final manifest authentication before implementing uploads. Do not improvise nonce construction in application code.

## 5. Backup scope and server authorization

Back up only owner-controlled data that the user expects to survive phone replacement:
- encrypted video objects and their wrapped DEK envelopes;
- receiver display name and mobile number, if the owner elects to sync them;
- video display names, expiry timestamps, and local/cloud object mapping;
- assignment identifiers and status/history needed to reconstruct the owner's list.

Do not back up login passwords, OTPs, Firebase tokens, Android IDs, private signing keys, raw Keystore keys, or plaintext video.

Use private Storage paths scoped to the Firebase UID, with server-created backup records and short-lived, narrowly scoped upload/download authorization. Do not rely on a guessed path or a long-lived download token. Every backup read/write must check:
- authenticated UID owns the backup record;
- current owner device is active and proves possession of its registered signing key;
- backup object belongs to that UID and is not expired/deleted;
- request size, format version, checksum, and quota constraints.

Storage Rules currently authorize video assignment uploads/downloads by assignment membership, but they do not check the new active owner-device key proof. Do not weaken or replace the existing assignment rules casually. Add a separate backup path and design server-mediated short-lived access before changing rules. Direct long-lived download URLs must not bypass revocation.

## 6. Legacy-video migration

When the old phone is available:
1. Require fresh owner authentication and active-device proof.
2. Read each legacy `.vsec` file using the existing legacy key without changing its format.
3. Decrypt locally as a stream; never create a plaintext file in shared storage or upload plaintext.
4. Re-encrypt directly as a new versioned backup object with a fresh per-object DEK.
5. Wrap that DEK using the offline Backup Recovery Secret.
6. Upload encrypted object + envelope, verify server checksum/size, then mark that object backed up.
7. Keep the original local file until the owner explicitly verifies restore. Do not delete originals automatically.

If any file fails, leave it unchanged and report the individual failure. Migration must be resumable and idempotent. Do not overwrite a previously verified backup object.

When the old phone is unavailable, restore only objects that were backed up before the loss. Legacy-only local files are not recoverable.

## 7. Recovery and rotation

- Device recovery code: authorizes replacing the active phone; one-time, short abuse limits, stored offline. It does not decrypt backup data.
- Backup Recovery Secret: decrypts wrapped DEKs; generated once, stored offline by the owner, and never sent to the server.
- Recovery secret rotation requires the old secret or an already-authorized active device to unwrap each DEK and re-wrap it under a new secret. Re-encrypting all large video ciphertext is not required if the DEKs remain unchanged.
- If the backup recovery secret is lost and no active old device can unwrap the DEKs, the server must not have a backdoor. The encrypted backups become unrecoverable. Explain this clearly before enabling backup.
- Revoking a phone blocks future server access but cannot erase data already downloaded or keys already extracted from a compromised device. Do not claim remote erasure of offline copies.

## 8. Rollout gates

Do not deploy or enable backup until all of the following are complete:
1. Agree on backup scope and recovery-secret presentation/storage UX.
2. Implement and review a versioned streaming AEAD format and test vectors.
3. Implement local DEK generation/wrapping/unwrapping and legacy migration without changing existing playback.
4. Implement private backup metadata and Storage authorization with active-device proof and revocation checks.
5. Add quotas, resumable upload, checksum verification, deletion/retention policy, and audit events.
6. Add unit tests for tampering, wrong secret, wrong UID/object AAD, nonce uniqueness, truncated files, interrupted uploads, replay, revoked device, and version rejection.
7. Test restore on a second physical Android device with the old phone unavailable.
8. Regression-test existing VS-3/VS-4 local selection, encryption, playback, expiry, and delete behavior.
9. Obtain explicit owner approval before any Firebase deployment or real-account enrollment.

## 9. Explicit non-goals / warnings

- This design does not make Android screen recording or filming with another camera impossible.
- A non-exportable Android Keystore key protects key extraction but does not guarantee a rooted/compromised device cannot access decrypted content while it is being played.
- Firebase Auth sign-in alone is not sufficient authorization for private backup access.
- A backup is not complete merely because Storage upload succeeded; the wrapped key, metadata, checksum, restore path, and recovery secret must all be tested together.
