'use strict';

const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore, Timestamp, FieldValue } = require('firebase-admin/firestore');
const { getStorage } = require('firebase-admin/storage');
const crypto = require('node:crypto');

initializeApp();

const db = getFirestore();
const MAX_TITLE_LENGTH = 120;
const MAX_FUTURE_DAYS = 365;

function cleanText(value, maxLength) {
  if (typeof value !== 'string') return '';
  return value.trim().slice(0, maxLength);
}

async function requireVerifiedSender(uid) {
  const user = await getAuth().getUser(uid);
  const providers = new Set((user.providerData || []).map((p) => p.providerId));
  if (!user.email || !user.emailVerified || !providers.has('password')) {
    throw new HttpsError(
      'failed-precondition',
      'Verify the sender email and sign in with email/password before sending.'
    );
  }
  if (!user.phoneNumber || !providers.has('phone')) {
    throw new HttpsError(
      'failed-precondition',
      'Link and verify the sender mobile number with phone OTP before sending.'
    );
  }
  return user;
}

async function findRecipientByBoth(email, phoneNumber) {
  let emailUser;
  let phoneUser;
  try {
    emailUser = await getAuth().getUserByEmail(email);
  } catch (_) {
    throw new HttpsError(
      'failed-precondition',
      'No account was found for this email. The recipient must register and verify it in the app first.'
    );
  }
  try {
    phoneUser = await getAuth().getUserByPhoneNumber(phoneNumber);
  } catch (_) {
    throw new HttpsError(
      'failed-precondition',
      'No account was found for this mobile number. The recipient must verify it with OTP in the app first.'
    );
  }

  if (emailUser.uid !== phoneUser.uid) {
    throw new HttpsError(
      'failed-precondition',
      'The email and mobile number belong to different accounts. Ask the recipient to correct and verify both.'
    );
  }

  const providers = new Set((emailUser.providerData || []).map((p) => p.providerId));
  if (!emailUser.emailVerified || !providers.has('password') || !providers.has('phone')) {
    throw new HttpsError(
      'failed-precondition',
      'The recipient must verify the email and link the verified mobile number to the same account.'
    );
  }
  return emailUser;
}

exports.createVideoAssignment = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Sign in before creating a video assignment.');
  }

  const sender = await requireVerifiedSender(request.auth.uid);
  const recipientEmail = cleanText(request.data && request.data.recipientEmail, 254).toLowerCase();
  const recipientPhone = cleanText(request.data && request.data.recipientPhone, 32);
  const title = cleanText(request.data && request.data.title, MAX_TITLE_LENGTH) || 'Secure video';
  const expiresAtMillis = Number(request.data && request.data.expiresAtMillis);

  if (!recipientEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(recipientEmail)) {
    throw new HttpsError('invalid-argument', 'Enter a valid recipient email address.');
  }
  if (!/^\+[1-9]\d{7,14}$/.test(recipientPhone)) {
    throw new HttpsError('invalid-argument', 'Enter the recipient mobile number in international format, e.g. +91XXXXXXXXXX.');
  }
  if (!Number.isFinite(expiresAtMillis)) {
    throw new HttpsError('invalid-argument', 'Provide the video expiry date and time.');
  }

  const now = Date.now();
  if (expiresAtMillis <= now || expiresAtMillis > now + MAX_FUTURE_DAYS * 24 * 60 * 60 * 1000) {
    throw new HttpsError('invalid-argument', 'Expiry must be in the future and no more than 365 days away.');
  }

  const recipient = await findRecipientByBoth(recipientEmail, recipientPhone);
  if (recipient.uid === sender.uid) {
    throw new HttpsError('invalid-argument', 'Choose a different recipient account.');
  }

  const assignmentId = crypto.randomUUID();
  const storagePath = 'videos/' + assignmentId + '/payload.vsec';
  await db.collection('videoAssignments').doc(assignmentId).create({
    senderUid: sender.uid,
    recipientUid: recipient.uid,
    recipientEmail: recipient.email,
    recipientPhone: recipient.phoneNumber,
    title,
    storagePath,
    status: 'pending_upload',
    expiresAt: Timestamp.fromMillis(expiresAtMillis),
    createdAt: FieldValue.serverTimestamp()
  });

  return { assignmentId, storagePath, expiresAtMillis };
});

exports.finalizeVideoUpload = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Sign in before finalizing an upload.');
  }

  const assignmentId = cleanText(request.data && request.data.assignmentId, 64);
  if (!/^[0-9a-f-]{36}$/i.test(assignmentId)) {
    throw new HttpsError('invalid-argument', 'Invalid video assignment ID.');
  }

  const docRef = db.collection('videoAssignments').doc(assignmentId);
  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(docRef);
    if (!snap.exists) throw new HttpsError('not-found', 'Video assignment not found.');

    const assignment = snap.data();
    if (assignment.senderUid !== request.auth.uid) {
      throw new HttpsError('permission-denied', 'Only the sender can finalize this upload.');
    }
    if (assignment.status !== 'pending_upload') {
      throw new HttpsError('failed-precondition', 'This assignment is not awaiting an upload.');
    }
    if (!(assignment.expiresAt instanceof Timestamp) || assignment.expiresAt.toMillis() <= Date.now()) {
      throw new HttpsError('failed-precondition', 'The video assignment has expired.');
    }

    const file = getStorage().bucket().file(assignment.storagePath);
    const [exists] = await file.exists();
    if (!exists) throw new HttpsError('failed-precondition', 'The uploaded encrypted video was not found.');

    const [metadata] = await file.getMetadata();
    if (!metadata.size || Number(metadata.size) > 500 * 1024 * 1024) {
      throw new HttpsError('failed-precondition', 'The uploaded video is empty or exceeds 500 MB.');
    }

    tx.update(docRef, {
      status: 'active',
      uploadedAt: FieldValue.serverTimestamp(),
      storageSize: Number(metadata.size)
    });
    return { status: 'active' };
  });

  return result;
});

/**
 * First-call device binding for an authenticated, verified account.
 * The client sends only a SHA-256 device fingerprint, never the raw Android ID.
 * This is a basic binding control; Play Integrity/App Check and recovery policy
 * must be configured before treating it as high-assurance device attestation.
 */
exports.bindDevice = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Sign in before binding this device.');
  }

  const deviceIdHash = cleanText(request.data && request.data.deviceIdHash, 64).toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(deviceIdHash)) {
    throw new HttpsError('invalid-argument', 'A valid SHA-256 device fingerprint is required.');
  }

  const user = await getAuth().getUser(request.auth.uid);
  const providers = new Set((user.providerData || []).map((p) => p.providerId));
  if (!user.email || !user.emailVerified || !providers.has('password')
      || !user.phoneNumber || !providers.has('phone')) {
    throw new HttpsError(
      'failed-precondition',
      'Verify your email and link your verified mobile number before binding this device.'
    );
  }

  const bindingRef = db.collection('deviceBindings').doc(request.auth.uid);
  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(bindingRef);
    if (!snap.exists) {
      tx.create(bindingRef, {
        uid: request.auth.uid,
        deviceIdHash,
        platform: 'android',
        createdAt: FieldValue.serverTimestamp(),
        lastSeenAt: FieldValue.serverTimestamp(),
        bindingVersion: 1
      });
      return { bound: true, firstBinding: true };
    }

    const existing = snap.data();
    if (existing.deviceIdHash !== deviceIdHash) {
      throw new HttpsError(
        'permission-denied',
        'This account is already bound to another device. Contact the app administrator for a verified recovery.'
      );
    }

    tx.update(bindingRef, {
      lastSeenAt: FieldValue.serverTimestamp()
    });
    return { bound: true, firstBinding: false };
  });

  return result;
});

