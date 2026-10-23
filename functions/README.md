# Vishal Secure server authorization foundation

This is a **backend foundation**, not a complete video-transfer release. The Android client still needs recipient registration/login, email verification, phone OTP linking, encrypted upload/download, and cross-device key management.

## Current callable functions

- `createVideoAssignment`: requires the sender to be signed in, email-verified, and to have a linked verified phone provider. It resolves the recipient by email and phone and requires both to resolve to the same Firebase UID, with verified email and linked phone provider. It creates a `pending_upload` assignment with a server-generated Storage path and expiry.
- `finalizeVideoUpload`: only the sender can finalize a pending assignment after the object exists in Storage. It marks the assignment active, which is required before the recipient can read the object.
- `bindDevice`: first verified call binds a Firebase UID to a SHA-256 device fingerprint; later calls from the same fingerprint update last-seen time, while a different fingerprint is denied. The raw Android ID must not be sent or stored. This is a basic server-side binding control, not strong device attestation.

A recipient who has not registered and verified both identifiers must first complete in-app registration. The callable function deliberately does not create an account merely because someone typed an email address and phone number.

## Required Firebase setup

1. Confirm the Firebase project is the intended project (`vishal-eb984`) before deployment.
2. Ensure Firebase Authentication has Email/Password and Phone providers enabled.
3. Set up a Firebase Storage bucket and billing/plan requirements as applicable.
4. Install/configure the Firebase CLI and select the intended project.
5. Deploy only after reviewing rules and testing with the Firebase Emulator Suite:
   ```sh
   cd functions
   npm install
   npm run lint
   cd ..
   firebase emulators:start --only functions,firestore,storage
   ```
6. Test sender, recipient, wrong email/phone pair, unverified email, expired assignment, unauthorized user, and oversized/missing upload cases before deploying.
7. Deploy reviewed rules and functions:
   ```sh
   firebase deploy --only functions,firestore:rules,storage
   ```

## Important limitations / next implementation steps

- These rules and functions have not yet been emulator-tested or deployed. Do not treat them as production-verified.
- Existing Android `CryptoManager` uses an AndroidKeyStore key local to one device. A sender's current encrypted file cannot simply be transferred and decrypted on another phone. Cross-device encryption and secure recipient key delivery must be implemented before recipient playback can work.
- The Android app must call `bindDevice` at the authenticated entry gate before granting access; it must calculate and send only a SHA-256 fingerprint. A different device is denied until an administrator performs a verified recovery by reviewing/removing the binding document. The client does not yet call this function, so the new binding is not active in the APK until client integration and backend deployment are completed.
- The Android app must upload the encrypted file to the exact `storagePath` returned by `createVideoAssignment`, then call `finalizeVideoUpload`. The recipient app must request only their own assignments and download only while active and unexpired.
- Client-side expiry alone is not trusted; Storage Rules check server-side expiry. A future download URL should not be shared as a long-lived bearer link.
- App Check, rate limiting/abuse protection, audit logging, account recovery, and a robust device-binding/App Attest or Play Integrity policy still need configuration and testing.
- These additions are scoped to backend files and do not intentionally alter existing VS-3 Android playback behavior.
