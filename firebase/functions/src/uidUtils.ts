/**
 * Shared UID utility functions for Cloud Functions.
 *
 * UID FORMAT: YYYYMMDDXXXX
 * - First 8 digits: date of generation
 * - Last 4 digits: random 0000–9999
 * - Total: exactly 12 digits
 *
 * IMPORTANT: The document ID in weeklyUids IS the UID value itself.
 * This matches the Firestore security rule:
 *   allow get: if isSignedIn() && resource.data.uid == weeklyUid;
 *
 * The Cloud Function also sets currentWeeklyUid on the user profile
 * so the Android client can read it without querying the weeklyUids collection.
 */

import * as admin from "firebase-admin";
import * as crypto from "crypto";

export const WEEKLY_UIDS_COLLECTION = "weeklyUids";
export const USERS_COLLECTION = "users";
export const UID_VALIDITY_DAYS = 7;
export const VALIDITY_DURATION_MS = UID_VALIDITY_DAYS * 24 * 60 * 60 * 1000; // Exactly 7 days (604,800,000 ms)
export const TRANSITION_GRACE_PERIOD_MS = 2 * 60 * 60 * 1000; // 2 hours continuity grace period
export const ROTATION_THRESHOLD_MS = 24 * 60 * 60 * 1000; // Rotate if within 24h of expiry

export interface RotationOptions {
  force?: boolean;
}

export interface UidOperationResult {
  uid: string;
  rotated: boolean;
  generatedAt: number;
  expiresAt: number;
  userName?: string;
  previousUid?: string | null;
}

/**
 * Generates the raw YYYYMMDDXXXX string using cryptographically secure random number.
 * Format:
 * - First 8 digits: YYYYMMDD (UTC date of generation for consistency)
 * - Last 4 digits: Cryptographically secure random 0000–9999
 * - Exactly 12 digits total
 */
export function generateUidString(date: Date = new Date()): string {
  const year = date.getUTCFullYear().toString();
  const month = (date.getUTCMonth() + 1).toString().padStart(2, "0");
  const day = date.getUTCDate().toString().padStart(2, "0");
  const randomNum = crypto.randomInt(0, 10000);
  const random = randomNum.toString().padStart(4, "0");

  return `${year}${month}${day}${random}`;
}

/**
 * Generates a new 12-digit UID in YYYYMMDDXXXX format.
 * Checks for collision against existing UIDs in Firestore with safe retries.
 */
export async function generateUniqueUid(
  db: admin.firestore.Firestore,
  maxAttempts = 15
): Promise<string> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const uid = generateUidString();

    // Check collision — direct document get (doc ID = UID value)
    const existing = await db
      .collection(WEEKLY_UIDS_COLLECTION)
      .doc(uid)
      .get();

    if (!existing.exists) {
      return uid;
    }
  }

  throw new Error(`Failed to generate unique UID after ${maxAttempts} collision attempts`);
}

/**
 * Atomically creates or rotates the weekly UID for a given user.
 *
 * Implements Zero-Downtime Handover:
 * 1. Generates unique replacement UID using cryptographically secure random numbers.
 * 2. In a single Firestore transaction:
 *    - Reads user profile to verify identity and obtain currentWeeklyUid pointer.
 *    - Reads existing UID record to check expiry, validity, and idempotency.
 *    - Commits new UID record with active: true, 7-day server-authoritative expiry.
 *    - Updates user profile's currentWeeklyUid pointer to the replacement UID.
 *    - Marks previous UID as active: false, retiredAt: now, replacedBy: replacementUid.
 * 3. Continuity: Old UID document retains original 7-day expiresAt and receives retiredAt,
 *    allowing lookup during the transition grace period while preventing indefinite access.
 * 4. Idempotency: If the current UID was generated recently and is still valid,
 *    duplicate invocations return the existing UID without redundant rotation.
 */
export async function createOrRotateUidForUser(
  db: admin.firestore.Firestore,
  userId: string,
  options: RotationOptions = {}
): Promise<UidOperationResult> {
  const force = options.force ?? false;
  const now = Date.now();
  const expiresAt = now + VALIDITY_DURATION_MS;

  // Pre-generate a collision-checked unique replacement candidate
  const replacementUid = await generateUniqueUid(db);

  return await db.runTransaction(async (transaction) => {
    const userRef = db.collection(USERS_COLLECTION).doc(userId);
    const userDoc = await transaction.get(userRef);

    if (!userDoc.exists) {
      throw new Error(`User ${userId} does not exist in ${USERS_COLLECTION}`);
    }

    const userData = userDoc.data() || {};
    if (userData.accountType && userData.accountType !== "USER") {
      throw new Error(`Account ${userId} is not a USER account (found: ${userData.accountType})`);
    }

    const currentWeeklyUid: string | undefined = userData.currentWeeklyUid;
    let currentUidDoc: admin.firestore.DocumentSnapshot | null = null;

    if (currentWeeklyUid) {
      const currentUidRef = db.collection(WEEKLY_UIDS_COLLECTION).doc(currentWeeklyUid);
      currentUidDoc = await transaction.get(currentUidRef);
    }

    // Check existing UID state if present
    if (currentUidDoc && currentUidDoc.exists) {
      const currentData = currentUidDoc.data() || {};
      const isCurrentlyActive = currentData.active === true && currentData.expiresAt > now;
      const isDueForRotation = currentData.expiresAt <= (now + ROTATION_THRESHOLD_MS);

      // Idempotency: If not forced, UID is active, and not due for rotation, preserve it!
      if (!force && isCurrentlyActive && !isDueForRotation) {
        return {
          uid: currentWeeklyUid!,
          rotated: false,
          generatedAt: currentData.generatedAt,
          expiresAt: currentData.expiresAt,
          userName: currentData.userName || userData.name || "",
          previousUid: currentData.previousUid || null,
        };
      }
    }

    const userName = userData.name || userData.fullName || "";

    // 1. Write replacement UID record
    const newUidRef = db.collection(WEEKLY_UIDS_COLLECTION).doc(replacementUid);
    transaction.set(newUidRef, {
      userId: userId,
      uid: replacementUid,
      userName: userName,
      generatedAt: now,
      expiresAt: expiresAt,
      active: true,
      previousUid: currentWeeklyUid || null,
    });

    // 2. Atomically update user profile pointer
    transaction.update(userRef, {
      currentWeeklyUid: replacementUid,
      lastRotatedAt: now,
    });

    // 3. Gracefully retire previous UID if it exists
    if (currentUidDoc && currentUidDoc.exists) {
      transaction.update(currentUidDoc.ref, {
        active: false,
        retiredAt: now,
        replacedBy: replacementUid,
      });
    }

    return {
      uid: replacementUid,
      rotated: true,
      generatedAt: now,
      expiresAt: expiresAt,
      userName: userName,
      previousUid: currentWeeklyUid || null,
    };
  });
}

/**
 * Creates a new active UID document for a user (backwards-compatible wrapper).
 */
export async function createUidForUser(
  db: admin.firestore.Firestore,
  userId: string
): Promise<string> {
  const result = await createOrRotateUidForUser(db, userId, { force: true });
  return result.uid;
}

/**
 * Clean up any older orphaned active UIDs for this user to maintain collection integrity.
 */
export async function cleanupOrphanedUidsForUser(
  db: admin.firestore.Firestore,
  userId: string,
  keepUid: string
): Promise<number> {
  const snapshot = await db
    .collection(WEEKLY_UIDS_COLLECTION)
    .where("userId", "==", userId)
    .where("active", "==", true)
    .get();

  let deactivated = 0;
  const batch = db.batch();

  snapshot.docs.forEach((doc) => {
    if (doc.id !== keepUid) {
      batch.update(doc.ref, {
        active: false,
        retiredAt: Date.now(),
        inconsistencyResolved: true,
      });
      deactivated++;
    }
  });

  if (deactivated > 0) {
    await batch.commit();
  }

  return deactivated;
}
