'use strict';

// Owner device lifecycle. This module is intentionally separate from the legacy
// bindDevice flow so existing installations remain compatible during migration.
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { getFirestore, FieldValue, Timestamp } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const crypto = require('node:crypto');

const db = getFirestore();
const CHALLENGE_TTL_MS = 5 * 60 * 1000;
const RECOVERY_CODE_TTL_MS = 365 * 24 * 60 * 60 * 1000;

function fail(code, message) {
  throw new HttpsError(code, message);
}
function requiredString(value, name, max = 256) {
  if (typeof value !== 'string' || !value.trim() || value.length > max) {
    fail('invalid-argument', `Invalid ${name}.`);
  }
  return value.trim();
}
async function requireOwnerAuth(request, requirePhone = true) {
  if (!request.auth) fail('unauthenticated', 'Sign in to your owner account.');
  const user = await getAuth().getUser(request.auth.uid);
  const providers = new Set((user.providerData || []).map((p) => p.providerId));
  if (!user.email || !user.emailVerified || !providers.has('password')) {
    fail('failed-precondition', 'Verify your owner email before managing devices.');
  }
  if (requirePhone && (!user.phoneNumber || !providers.has('phone'))) {
    fail('failed-precondition', 'Verify and link your mobile number before managing devices.');
  }
  const authTime = Number(request.auth.token.auth_time || 0) * 1000;
  if (!authTime || Date.now() - authTime > 5 * 60 * 1000) {
    fail('failed-precondition', 'Please sign in again before this sensitive action.');
  }
  return user;
}
function challengeMessage(challenge) {
  return [challenge.id, challenge.nonce, challenge.uid, challenge.deviceId, challenge.purpose].join('.');
}
function verifySignature(publicKey, message, signature) {
  try {
    const key = crypto.createPublicKey(publicKey);
    if (key.asymmetricKeyType !== 'ec' && key.asymmetricKeyType !== 'ed25519') return false;
    return crypto.verify(
      key.asymmetricKeyType === 'ed25519' ? null : 'sha256',
      Buffer.from(message, 'utf8'),
      key,
      Buffer.from(signature, 'base64')
    );
  } catch (_) {
    return false;
  }
}
async function consumeRateLimit(uid, action, limit, windowMs) {
  const ref = db.collection('ownerDeviceRateLimits').doc(uid + '_' + action);
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const current = snap.exists ? snap.data() : {};
    const windowStart = Number(current.windowStart || 0);
    const count = windowStart && now - windowStart < windowMs ? Number(current.count || 0) : 0;
    if (count >= limit) {
      fail('resource-exhausted', 'Too many ' + action.replace(/-/g, ' ') + ' requests. Please wait and try again.');
    }
    tx.set(ref, {
      uid,
      action,
      windowStart: count ? windowStart : now,
      count: count + 1,
      updatedAt: FieldValue.serverTimestamp()
    });
  });
}

async function createChallenge(uid, deviceId, purpose) {
  await consumeRateLimit(uid, 'challenge-' + purpose, 10, 10 * 60 * 1000);
  const id = crypto.randomUUID();
  const nonce = crypto.randomBytes(32).toString('base64url');
  const expiresAt = Timestamp.fromMillis(Date.now() + CHALLENGE_TTL_MS);
  const challenge = { id, uid, deviceId, purpose, nonce, expiresAt, used: false, createdAt: FieldValue.serverTimestamp() };
  await db.collection('ownerDeviceChallenges').doc(id).create(challenge);
  return { challengeId: id, nonce, purpose, expiresAtMillis: expiresAt.toMillis() };
}

exports.beginOwnerDeviceEnrollment = onCall(async (request) => {
  await requireOwnerAuth(request);
  const deviceId = requiredString(request.data && request.data.deviceId, 'installation ID', 80);
  const ownerRef = db.collection('ownerDevices').doc(request.auth.uid);
  const ownerSnap = await ownerRef.get();
  const owner = ownerSnap.exists ? ownerSnap.data() : {};
  if (owner.activeDeviceId) fail('already-exists', 'An owner device is already active. Use the replacement or recovery process.');
  return createChallenge(request.auth.uid, deviceId, 'initial-enrollment');
});

exports.completeOwnerDeviceEnrollment = onCall(async (request) => {
  await requireOwnerAuth(request);
  const uid = request.auth.uid;
  const deviceId = requiredString(request.data && request.data.deviceId, 'installation ID', 80);
  const publicKey = requiredString(request.data && request.data.publicKey, 'public key', 4096);
  const challengeId = requiredString(request.data && request.data.challengeId, 'challenge ID', 64);
  const signature = requiredString(request.data && request.data.signature, 'signature', 1024);
  const challengeRef = db.collection('ownerDeviceChallenges').doc(challengeId);
  const ownerRef = db.collection('ownerDevices').doc(uid);
  const deviceRef = ownerRef.collection('devices').doc(deviceId);

  await db.runTransaction(async (tx) => {
    const [challengeSnap, ownerSnap] = await Promise.all([tx.get(challengeRef), tx.get(ownerRef)]);
    if (!challengeSnap.exists) fail('not-found', 'Enrollment challenge not found.');
    const c = challengeSnap.data();
    if (c.uid !== uid || c.deviceId !== deviceId || c.purpose !== 'initial-enrollment'
        || c.used || !(c.expiresAt instanceof Timestamp) || c.expiresAt.toMillis() <= Date.now()) {
      fail('failed-precondition', 'Enrollment challenge is expired or invalid.');
    }
    if (!verifySignature(publicKey, challengeMessage(c), signature)) {
      fail('permission-denied', 'Device key proof could not be verified.');
    }
    const owner = ownerSnap.exists ? ownerSnap.data() : {};
    if (owner.activeDeviceId) fail('already-exists', 'An owner device became active. Start again.');
    tx.set(deviceRef, {
      deviceId, uid, publicKey, platform: 'android', status: 'active',
      createdAt: FieldValue.serverTimestamp(), lastSeenAt: FieldValue.serverTimestamp(),
      keyAlgorithm: crypto.createPublicKey(publicKey).asymmetricKeyType
    });
    tx.set(ownerRef, {
      uid, activeDeviceId: deviceId, bindingVersion: 1,
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
    tx.update(challengeRef, { used: true, usedAt: FieldValue.serverTimestamp() });
    const eventRef = db.collection('ownerAudit').doc(uid).collection('events').doc();
    tx.set(eventRef, { type: 'initial-enrollment', deviceId, actorUid: uid, createdAt: FieldValue.serverTimestamp() });
  });
  return { active: true, deviceId };
});

exports.beginOwnerDeviceReplacement = onCall(async (request) => {
  await requireOwnerAuth(request);
  const uid = request.auth.uid;
  const newDeviceId = requiredString(request.data && request.data.newDeviceId, 'new installation ID', 80);
  const ownerSnap = await db.collection('ownerDevices').doc(uid).get();
  if (!ownerSnap.exists || !ownerSnap.data().activeDeviceId) fail('failed-precondition', 'No active owner device exists.');
  const oldDeviceId = ownerSnap.data().activeDeviceId;
  if (oldDeviceId === newDeviceId) fail('invalid-argument', 'The new installation must be different.');
  const oldSnap = await db.collection('ownerDevices').doc(uid).collection('devices').doc(oldDeviceId).get();
  if (!oldSnap.exists || oldSnap.data().status !== 'active') fail('failed-precondition', 'The current device record is not active.');
  const challenge = await createChallenge(uid, oldDeviceId, 'approve-replacement');
  const replacementRef = db.collection('ownerDeviceReplacements').doc(challenge.challengeId);
  await replacementRef.create({
    uid, oldDeviceId, newDeviceId, challengeId: challenge.challengeId,
    status: 'awaiting-old-device-approval',
    expiresAt: Timestamp.fromMillis(challenge.expiresAtMillis),
    createdAt: FieldValue.serverTimestamp()
  });
  return { ...challenge, oldDeviceId, newDeviceId };
});

exports.approveOwnerDeviceReplacement = onCall(async (request) => {
  await requireOwnerAuth(request);
  const uid = request.auth.uid;
  const challengeId = requiredString(request.data && request.data.challengeId, 'challenge ID', 64);
  const signature = requiredString(request.data && request.data.signature, 'signature', 1024);
  const challengeRef = db.collection('ownerDeviceChallenges').doc(challengeId);
  const replacementRef = db.collection('ownerDeviceReplacements').doc(challengeId);
  await db.runTransaction(async (tx) => {
    const [cSnap, rSnap] = await Promise.all([tx.get(challengeRef), tx.get(replacementRef)]);
    if (!cSnap.exists || !rSnap.exists) fail('not-found', 'Replacement request not found.');
    const c = cSnap.data();
    const r = rSnap.data();
    if (c.uid !== uid || c.purpose !== 'approve-replacement' || c.used || r.status !== 'awaiting-old-device-approval'
        || !(c.expiresAt instanceof Timestamp) || c.expiresAt.toMillis() <= Date.now()) {
      fail('failed-precondition', 'Replacement approval has expired or is invalid.');
    }
    const deviceRef = db.collection('ownerDevices').doc(uid).collection('devices').doc(r.oldDeviceId);
    const deviceSnap = await tx.get(deviceRef);
    if (!deviceSnap.exists || deviceSnap.data().status !== 'active'
        || !verifySignature(deviceSnap.data().publicKey, challengeMessage(c), signature)) {
      fail('permission-denied', 'Approval from the active old device could not be verified.');
    }
    tx.update(challengeRef, { used: true, usedAt: FieldValue.serverTimestamp() });
    tx.update(replacementRef, { status: 'old-device-approved', approvedAt: FieldValue.serverTimestamp() });
  });
  return { approved: true };
});

exports.beginOwnerReplacementDeviceProof = onCall(async (request) => {
  await requireOwnerAuth(request);
  const uid = request.auth.uid;
  const replacementId = requiredString(request.data && request.data.replacementId, 'replacement ID', 64);
  const replacementRef = db.collection('ownerDeviceReplacements').doc(replacementId);
  const snap = await replacementRef.get();
  if (!snap.exists || snap.data().uid !== uid || snap.data().status !== 'old-device-approved'
      || !(snap.data().expiresAt instanceof Timestamp) || snap.data().expiresAt.toMillis() <= Date.now()) {
    fail('failed-precondition', 'Old-device approval is missing or expired.');
  }
  const result = await createChallenge(uid, snap.data().newDeviceId, 'replacement-new-device');
  await db.collection('ownerDeviceChallenges').doc(result.challengeId).update({ replacementId });
  return result;
});

exports.completeOwnerDeviceReplacement = onCall(async (request) => {
  await requireOwnerAuth(request);
  const uid = request.auth.uid;
  const replacementId = requiredString(request.data && request.data.replacementId, 'replacement ID', 64);
  const proofChallengeId = requiredString(request.data && request.data.proofChallengeId, 'proof challenge ID', 64);
  const deviceId = requiredString(request.data && request.data.deviceId, 'new installation ID', 80);
  const publicKey = requiredString(request.data && request.data.publicKey, 'new public key', 4096);
  const signature = requiredString(request.data && request.data.signature, 'new device signature', 1024);
  const replacementRef = db.collection('ownerDeviceReplacements').doc(replacementId);
  const challengeRef = db.collection('ownerDeviceChallenges').doc(proofChallengeId);
  await db.runTransaction(async (tx) => {
    const [rSnap, cSnap] = await Promise.all([tx.get(replacementRef), tx.get(challengeRef)]);
    if (!rSnap.exists || !cSnap.exists) fail('not-found', 'Replacement request or proof challenge not found.');
    const r = rSnap.data();
    const c = cSnap.data();
    if (r.uid !== uid || r.newDeviceId !== deviceId || r.status !== 'old-device-approved'
        || c.uid !== uid || c.deviceId !== deviceId || c.purpose !== 'replacement-new-device'
        || c.replacementId !== replacementId || c.used
        || !(c.expiresAt instanceof Timestamp) || c.expiresAt.toMillis() <= Date.now()
        || !(r.expiresAt instanceof Timestamp) || r.expiresAt.toMillis() <= Date.now()) {
      fail('failed-precondition', 'Replacement approval or new-device challenge is missing, expired, or invalid.');
    }
    if (!verifySignature(publicKey, challengeMessage(c), signature)) {
      fail('permission-denied', 'New device key proof could not be verified.');
    }
    const ownerRef = db.collection('ownerDevices').doc(uid);
    const oldRef = ownerRef.collection('devices').doc(r.oldDeviceId);
    const newRef = ownerRef.collection('devices').doc(deviceId);
    const ownerSnap = await tx.get(ownerRef);
    if (!ownerSnap.exists || ownerSnap.data().activeDeviceId !== r.oldDeviceId) {
      fail('aborted', 'The active device changed. Start replacement again.');
    }
    tx.update(oldRef, { status: 'revoked', revokedAt: FieldValue.serverTimestamp(), replacedBy: deviceId });
    tx.create(newRef, {
      uid, deviceId, publicKey, platform: 'android', status: 'active',
      createdAt: FieldValue.serverTimestamp(), lastSeenAt: FieldValue.serverTimestamp(),
      keyAlgorithm: crypto.createPublicKey(publicKey).asymmetricKeyType
    });
    tx.update(ownerRef, { activeDeviceId: deviceId, bindingVersion: FieldValue.increment(1), updatedAt: FieldValue.serverTimestamp() });
    tx.update(replacementRef, { status: 'completed', completedAt: FieldValue.serverTimestamp() });
    tx.update(challengeRef, { used: true, usedAt: FieldValue.serverTimestamp() });
    const eventRef = db.collection('ownerAudit').doc(uid).collection('events').doc();
    tx.set(eventRef, { type: 'device-replaced', oldDeviceId: r.oldDeviceId, newDeviceId: deviceId, actorUid: uid, createdAt: FieldValue.serverTimestamp() });
  });
  return { active: true, deviceId, oldDeviceRevoked: true };
});

exports.createOwnerRecoveryCode = onCall(async (request) => {
  await requireOwnerAuth(request);
  await consumeRateLimit(request.auth.uid, 'recovery-code-issue', 3, 24 * 60 * 60 * 1000);
  const uid = request.auth.uid;
  const ownerRef = db.collection('ownerDevices').doc(uid);
  const ownerSnap = await ownerRef.get();
  if (!ownerSnap.exists || !ownerSnap.data().activeDeviceId) fail('failed-precondition', 'Enroll an owner device before creating a recovery code.');
  const code = crypto.randomBytes(18).toString('base64url').toUpperCase();
  const salt = crypto.randomBytes(16).toString('hex');
  const digest = crypto.createHash('sha256').update(salt + code).digest('hex');
  await db.runTransaction(async (tx) => {
    tx.set(ownerRef.collection('recovery').doc('current'), {
      salt, digest, createdAt: FieldValue.serverTimestamp(),
      expiresAt: Timestamp.fromMillis(Date.now() + RECOVERY_CODE_TTL_MS),
      used: false, attempts: 0
    });
    const eventRef = db.collection('ownerAudit').doc(uid).collection('events').doc();
    tx.set(eventRef, { type: 'recovery-code-issued', actorUid: uid, createdAt: FieldValue.serverTimestamp() });
  });
  // The plaintext code is returned only once and is never persisted or logged.
  return { recoveryCode: code, expiresInDays: 365, storeOffline: true };
});

exports.recoverOwnerDevice = onCall(async (request) => {
  await requireOwnerAuth(request, false);
  const uid = request.auth.uid;
  const code = requiredString(request.data && request.data.recoveryCode, 'recovery code', 128).toUpperCase();
  const deviceId = requiredString(request.data && request.data.deviceId, 'new installation ID', 80);
  const publicKey = requiredString(request.data && request.data.publicKey, 'new public key', 4096);
  const challengeId = requiredString(request.data && request.data.challengeId, 'challenge ID', 64);
  const signature = requiredString(request.data && request.data.signature, 'new device signature', 1024);
  const challengeRef = db.collection('ownerDeviceChallenges').doc(challengeId);
  const ownerRef = db.collection('ownerDevices').doc(uid);
  const recoveryRef = ownerRef.collection('recovery').doc('current');
  // Count every recovery attempt in a separate committed transaction so a failed
  // code check cannot roll back the abuse-control counter.
  const attemptCount = await db.runTransaction(async (tx) => {
    const snap = await tx.get(recoveryRef);
    if (!snap.exists) fail('failed-precondition', 'Recovery information is unavailable.');
    const next = Number(snap.data().attempts || 0) + 1;
    tx.update(recoveryRef, { attempts: next, lastAttemptAt: FieldValue.serverTimestamp() });
    return next;
  });
  if (attemptCount > 5) fail('resource-exhausted', 'Too many recovery attempts. Issue a new recovery code after verifying your account.');
  await db.runTransaction(async (tx) => {
    const [rSnap, cSnap, ownerSnap] = await Promise.all([tx.get(recoveryRef), tx.get(challengeRef), tx.get(ownerRef)]);
    if (!rSnap.exists || !cSnap.exists || !ownerSnap.exists) fail('failed-precondition', 'Recovery information is unavailable.');
    const r = rSnap.data();
    const c = cSnap.data();
    const activeDeviceId = ownerSnap.data().activeDeviceId;
    if (r.used || !(r.expiresAt instanceof Timestamp) || r.expiresAt.toMillis() <= Date.now()
        || c.uid !== uid || c.deviceId !== deviceId || c.purpose !== 'recovery' || c.used
        || !(c.expiresAt instanceof Timestamp) || c.expiresAt.toMillis() <= Date.now()) {
      fail('failed-precondition', 'Recovery code or challenge is expired or already used.');
    }
    const expected = crypto.createHash('sha256').update(r.salt + code).digest('hex');
    if (!crypto.timingSafeEqual(Buffer.from(expected, 'hex'), Buffer.from(r.digest, 'hex'))) {
      fail('permission-denied', 'Recovery code is incorrect.');
    }
    if (!verifySignature(publicKey, challengeMessage(c), signature)) fail('permission-denied', 'New device key proof could not be verified.');
    const oldRef = ownerRef.collection('devices').doc(activeDeviceId);
    const newRef = ownerRef.collection('devices').doc(deviceId);
    tx.update(oldRef, { status: 'revoked', revokedAt: FieldValue.serverTimestamp(), replacedBy: deviceId, revocationReason: 'recovery' });
    tx.create(newRef, {
      uid, deviceId, publicKey, platform: 'android', status: 'active',
      createdAt: FieldValue.serverTimestamp(), lastSeenAt: FieldValue.serverTimestamp(),
      keyAlgorithm: crypto.createPublicKey(publicKey).asymmetricKeyType
    });
    tx.update(ownerRef, { activeDeviceId: deviceId, bindingVersion: FieldValue.increment(1), updatedAt: FieldValue.serverTimestamp() });
    tx.update(rSnap.ref, { used: true, usedAt: FieldValue.serverTimestamp() });
    tx.update(challengeRef, { used: true, usedAt: FieldValue.serverTimestamp() });
    const eventRef = db.collection('ownerAudit').doc(uid).collection('events').doc();
    tx.set(eventRef, { type: 'device-recovered', oldDeviceId: activeDeviceId, newDeviceId: deviceId, actorUid: uid, createdAt: FieldValue.serverTimestamp() });
  });
  return { active: true, deviceId, oldDeviceRevoked: true };
});

exports.beginOwnerDeviceRecovery = onCall(async (request) => {
  await requireOwnerAuth(request, false);
  const deviceId = requiredString(request.data && request.data.deviceId, 'new installation ID', 80);
  return createChallenge(request.auth.uid, deviceId, 'recovery');
});

exports.getOwnerDeviceStatus = onCall(async (request) => {
  if (!request.auth) fail('unauthenticated', 'Sign in first.');
  const ownerSnap = await db.collection('ownerDevices').doc(request.auth.uid).get();
  if (!ownerSnap.exists) return { enrolled: false, activeDeviceId: null };
  const owner = ownerSnap.data();
  return { enrolled: Boolean(owner.activeDeviceId), activeDeviceId: owner.activeDeviceId || null, bindingVersion: owner.bindingVersion || 0 };
});
