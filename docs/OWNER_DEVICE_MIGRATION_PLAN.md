# Vishal Secure — Owner Account & Device Migration Plan

Status: Design baseline only. No production behavior is changed by this document.
Base branch: `vs-4-EMD`
Work branch: `vs-4-owner-migration`

## Goal

Keep the owner's identity and authority stable while allowing the owner to replace a lost, damaged, or upgraded Android phone. The first release should allow one active primary owner device at a time. Replacing a device must be deliberate, authenticated, auditable, and recoverable without weakening recipient/video authorization.

## Existing implementation facts

- Firebase Authentication is used for email/password and phone providers.
- `functions/index.js` has a `bindDevice` callable that creates `deviceBindings/{uid}` on first binding and rejects a different device hash thereafter.
- The current binding is based on a client-supplied SHA-256 fingerprint. The function itself notes that this is basic binding, not high-assurance device attestation.
- `MainActivity` currently keeps receiver details, expiry, and video URI/name lists in local SharedPreferences.
- `CryptoManager` uses an AES/GCM key held in Android Keystore. That key is not automatically transferable to a replacement phone.
- Existing video assignment functions require verified sender email and linked phone, and recipient email/phone must resolve to the same Firebase UID.
- Current Firestore rules deny client writes and allow assignment reads only to sender/recipient UIDs.

## Target owner experience

1. Owner signs in to the same permanent Firebase account on a replacement phone.
2. Owner completes fresh identity verification (email/password plus verified phone OTP; recovery route is separately designed for a lost phone/number).
3. App creates a new installation identity and registers its public key/device metadata.
4. Server verifies eligibility and starts a device-replacement transaction.
5. The prior primary device is marked revoked and the new device becomes the only active primary device.
6. Every privileged callable and protected data access checks the active device record on the server; UI-only checks are insufficient.
7. Owner's cloud backup metadata and encrypted video/key material are restored only after authorization and successful key recovery.

## Security and data rules

- Firebase UID is the stable owner identity; never use email address, phone number, or Android ID as the permanent owner identifier.
- Never send or store a raw Android ID as a security credential. A client-generated random installation ID is an identifier, not proof of device integrity.
- Prefer a per-installation asymmetric key pair generated in Android Keystore. Register the public key; require signed, nonce-bound requests for sensitive device operations where practical.
- Treat Play Integrity/App Check as defense-in-depth, not as the sole account recovery method.
- Device replacement must require recent re-authentication and a server-verified challenge. A lost-phone recovery path must not depend solely on receiving an OTP on the lost phone.
- Maintain an auditable device history (created, last seen, replaced/revoked timestamps, reason, actor UID); do not expose secrets in logs.
- Enforce device status in Cloud Functions and Storage/Firestore authorization. Revoking a device must block future privileged requests even if its Firebase ID token remains valid.
- Do not rely only on `revokeRefreshTokens` for one-device revocation because that can affect all sessions for the UID; use server-side active-device authorization for per-device control.
- Do not upload plaintext videos or plaintext encryption keys.
- The current Android Keystore AES key cannot simply be copied to a new phone. Design envelope encryption: a random data-encryption key (DEK) encrypts each video; the DEK is wrapped for the active device and separately protected by a user-controlled recovery mechanism. The server stores only encrypted backup objects and wrapped key material.
- Recovery design must avoid storing a reusable plaintext recovery secret on the server. Define user experience and recovery threat model before implementation.
- Keep existing local-only VS-3 playback behavior intact until migration and backup paths are implemented and tested.

## Proposed server data model

- `ownerDevices/{uid}/devices/{deviceId}`
  - `status`: `pending` | `active` | `revoked`
  - `platform`: `android`
  - `publicKey`: installation public key (never private key)
  - `createdAt`, `lastSeenAt`, `revokedAt`
  - `deviceLabel` and app version (display metadata only)
  - `keyVersion` and supported crypto suite
- `ownerDevices/{uid}`
  - `activeDeviceId`
  - `bindingVersion`
  - `updatedAt`
- `ownerAudit/{uid}/events/{eventId}`
  - replacement/revocation event metadata; no OTPs, tokens, private keys, or recovery secrets.
- Backup metadata references encrypted Storage objects and records format/version, checksum, and wrapped-key version. Storage paths must be authorized through server-issued assignments or short-lived access, not guessed by clients.

## Callable workflow (design; not implemented yet)

1. `beginOwnerDeviceEnrollment`: authenticated account, recent re-authentication, verified identity, App Check/Integrity signals where available; returns a short-lived nonce/challenge.
2. Client generates a new Keystore key pair and signs the nonce.
3. `completeOwnerDeviceEnrollment`: verifies challenge/signature and policy; first device can be activated if no active device exists.
4. `beginOwnerDeviceReplacement`: requires stronger step-up verification and explicit confirmation; records pending replacement, never immediately discards the old device.
5. `completeOwnerDeviceReplacement`: atomically revokes old device, activates new device, increments binding version, writes audit event.
6. `getOwnerDeviceStatus`: returns only safe device status for the authenticated owner.
7. `revokeOwnerDevice`: owner-initiated revocation with step-up verification; emergency recovery policy is separate.
8. All privileged callables verify that the calling device is the current active device and that the request is bound to its registered key/challenge.

## Delivery phases

### Phase A — design and inventory
- Record existing local data, encryption, authentication, backend, and rules behavior.
- Confirm what data must follow the owner to a new phone.
- Define normal phone replacement, lost phone, lost SIM/number, and account recovery cases.
- No app code changes.

### Phase B — server-side device lifecycle
- Implement enrollment, replacement, revocation, audit, and active-device enforcement on a dedicated branch.
- Add emulator/unit tests for first enrollment, same-device check-in, replacement, replay, stale challenge, unauthorized caller, and revoked-device denial.
- Keep existing `bindDevice` behavior until a tested migration path exists.

### Phase C — backup and cryptographic recovery
- Finalize envelope-encryption format and recovery UX.
- Implement encrypted metadata/video backup and restore with versioning, integrity checks, and size limits.
- Prove a new device can decrypt a test backup without access to the old device's Keystore key.

### Phase D — Android owner UI and end-to-end tests
- Add device status, replace-phone, revoke, and restore screens.
- Test on two physical Android devices, including lost-old-device simulation.
- Confirm old device is denied after successful replacement and new device can restore.
- Regression-test all VS-3 local video functions.

### Phase E — authentication integration and release
- Resolve current OTP crash and account setup after the rest of the system is ready, as requested by the owner.
- Complete end-to-end security tests and issue a release APK only after acceptance.

## Acceptance criteria

- Same Firebase UID retains owner identity across phones.
- Only one primary owner device is active at a time (unless a later product decision explicitly changes this).
- Replacement requires explicit owner approval and step-up verification.
- Revoked device cannot call privileged backend functions or access protected cloud content.
- New phone restores only encrypted backups and can decrypt them through the designed recovery flow.
- No plaintext video or reusable secret is stored server-side.
- All VS-3 features pass regression tests.
- No signing key or GitHub Secrets changes are made as part of this design phase.

## Open decisions to settle before implementation

1. Should the owner always have exactly one active phone, or allow a small number of trusted devices?
2. For recovery, should the owner use a user-held recovery code, a separate recovery passphrase, or both?
3. Which items must sync: video files, receiver contacts, expiry settings, assignment history, or all of these?
4. What is the policy if both the old phone and registered mobile number are unavailable?

No secrets, passwords, OTP values, private keys, or recovery codes should be placed in this repository document.


## Implementation progress — 2026-11-22

- Added `functions/ownerDevices.js` with callable endpoints for initial enrollment, old-device replacement approval, recovery-code issuance, recovery, and safe device-status lookup.
- Exported the module from `functions/index.js`.
- These endpoints are scaffolding and are not deployed or connected to Android yet.
- Important remaining work before any production use:
  - Complete and verify the two-stage replacement proof protocol so both old and new installation keys sign distinct, server-issued challenges.
  - Add active-device checks to every privileged callable and protected Storage access, with a deliberate compatibility/migration policy for existing installations.
  - Add rate limits/abuse controls for recovery attempts and challenge creation, plus App Check/Play Integrity integration.
  - Add automated tests and run Firebase Emulator tests.
  - Integrate Android Keystore key generation/signing and recovery UI.
- Do not deploy this branch until the above security gates and end-to-end tests pass.


### Follow-up hardening — 2026-11-22

- Corrected replacement so the old installation approves a challenge first, then the new installation receives and signs a separate short-lived challenge before the atomic switch.
- Added a five-attempt limit for recovery-code verification; attempts are committed independently so an incorrect code cannot roll back the counter.
- Confirmed by source inspection that the legacy `bindDevice` callable remains present.
- These are source-level checks only. No Node syntax test, Firebase Emulator test, Android integration test, or deployment has been performed.
- Remaining release blockers include server-side active-device enforcement on all privileged callables and Storage access, per-request proof-of-possession, recovery issuance rate controls, and end-to-end testing.
