"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.generateUniqueUid = generateUniqueUid;
exports.createUidForUser = createUidForUser;
const WEEKLY_UIDS_COLLECTION = "weeklyUids";
const USERS_COLLECTION = "users";
const UID_VALIDITY_DAYS = 7;
/**
 * Generates a new 12-digit UID in YYYYMMDDXXXX format.
 * Checks for collision against existing UIDs in Firestore.
 */
async function generateUniqueUid(db) {
    const maxAttempts = 10;
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
    throw new Error("Failed to generate unique UID after maximum attempts");
}
/**
 * Generates the raw YYYYMMDDXXXX string.
 */
function generateUidString() {
    const now = new Date();
    const year = now.getFullYear().toString();
    const month = (now.getMonth() + 1).toString().padStart(2, "0");
    const day = now.getDate().toString().padStart(2, "0");
    const random = Math.floor(Math.random() * 10000)
        .toString()
        .padStart(4, "0");
    return `${year}${month}${day}${random}`;
}
/**
 * Creates a new active UID document for a user and expires any previous ones.
 *
 * IMPORTANT:
 * - Document ID = UID value (e.g., weeklyUids/202608181234)
 * - Also updates the user's profile with currentWeeklyUid so the
 *   Android client can find the UID without list queries.
 */
async function createUidForUser(db, userId) {
    const uid = await generateUniqueUid(db);
    const now = Date.now();
    const expiresAt = now + UID_VALIDITY_DAYS * 24 * 60 * 60 * 1000;
    // Expire previous active UIDs for this user
    // Admin SDK bypasses security rules, so we can query here
    const previousUids = await db
        .collection(WEEKLY_UIDS_COLLECTION)
        .where("userId", "==", userId)
        .where("active", "==", true)
        .get();
    const batch = db.batch();
    previousUids.docs.forEach((doc) => {
        batch.update(doc.ref, { active: false });
    });
    // Create new UID document with doc ID = UID value
    const newUidRef = db.collection(WEEKLY_UIDS_COLLECTION).doc(uid);
    batch.set(newUidRef, {
        userId: userId,
        uid: uid,
        generatedAt: now,
        expiresAt: expiresAt,
        active: true,
    });
    // Update user profile with the current weekly UID
    const userRef = db.collection(USERS_COLLECTION).doc(userId);
    batch.update(userRef, {
        currentWeeklyUid: uid,
    });
    await batch.commit();
    return uid;
}
//# sourceMappingURL=uidUtils.js.map