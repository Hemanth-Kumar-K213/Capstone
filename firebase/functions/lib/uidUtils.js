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
exports.ROTATION_THRESHOLD_MS = exports.TRANSITION_GRACE_PERIOD_MS = exports.VALIDITY_DURATION_MS = exports.UID_VALIDITY_DAYS = exports.USERS_COLLECTION = exports.WEEKLY_UIDS_COLLECTION = void 0;
exports.generateUidString = generateUidString;
exports.generateUniqueUid = generateUniqueUid;
exports.createOrRotateUidForUser = createOrRotateUidForUser;
exports.createUidForUser = createUidForUser;
exports.cleanupOrphanedUidsForUser = cleanupOrphanedUidsForUser;
const crypto = require("crypto");
exports.WEEKLY_UIDS_COLLECTION = "weeklyUids";
exports.USERS_COLLECTION = "users";
exports.UID_VALIDITY_DAYS = 7;
exports.VALIDITY_DURATION_MS = exports.UID_VALIDITY_DAYS * 24 * 60 * 60 * 1000; // Exactly 7 days (604,800,000 ms)
exports.TRANSITION_GRACE_PERIOD_MS = 2 * 60 * 60 * 1000; // 2 hours continuity grace period
exports.ROTATION_THRESHOLD_MS = 24 * 60 * 60 * 1000; // Rotate if within 24h of expiry
/**
 * Generates the raw YYYYMMDDXXXX string using cryptographically secure random number.
 * Format:
 * - First 8 digits: YYYYMMDD (UTC date of generation for consistency)
 * - Last 4 digits: Cryptographically secure random 0000–9999
 * - Exactly 12 digits total
 */
function generateUidString(date = new Date()) {
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
async function generateUniqueUid(db, maxAttempts = 15) {
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
        const uid = generateUidString();
        // Check collision — direct document get (doc ID = UID value)
        const existing = await db
            .collection(exports.WEEKLY_UIDS_COLLECTION)
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
async function createOrRotateUidForUser(db, userId, options = {}) {
    var _a;
    const force = (_a = options.force) !== null && _a !== void 0 ? _a : false;
    const now = Date.now();
    const expiresAt = now + exports.VALIDITY_DURATION_MS;
    // Pre-generate a collision-checked unique replacement candidate
    const replacementUid = await generateUniqueUid(db);
    return await db.runTransaction(async (transaction) => {
        const userRef = db.collection(exports.USERS_COLLECTION).doc(userId);
        const userDoc = await transaction.get(userRef);
        if (!userDoc.exists) {
            throw new Error(`User ${userId} does not exist in ${exports.USERS_COLLECTION}`);
        }
        const userData = userDoc.data() || {};
        if (userData.accountType && userData.accountType !== "USER") {
            throw new Error(`Account ${userId} is not a USER account (found: ${userData.accountType})`);
        }
        const currentWeeklyUid = userData.currentWeeklyUid;
        let currentUidDoc = null;
        if (currentWeeklyUid) {
            const currentUidRef = db.collection(exports.WEEKLY_UIDS_COLLECTION).doc(currentWeeklyUid);
            currentUidDoc = await transaction.get(currentUidRef);
        }
        // Check existing UID state if present
        if (currentUidDoc && currentUidDoc.exists) {
            const currentData = currentUidDoc.data() || {};
            const isCurrentlyActive = currentData.active === true && currentData.expiresAt > now;
            const isDueForRotation = currentData.expiresAt <= (now + exports.ROTATION_THRESHOLD_MS);
            // Idempotency: If not forced, UID is active, and not due for rotation, preserve it!
            if (!force && isCurrentlyActive && !isDueForRotation) {
                return {
                    uid: currentWeeklyUid,
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
        const newUidRef = db.collection(exports.WEEKLY_UIDS_COLLECTION).doc(replacementUid);
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
async function createUidForUser(db, userId) {
    const result = await createOrRotateUidForUser(db, userId, { force: true });
    return result.uid;
}
/**
 * Clean up any older orphaned active UIDs for this user to maintain collection integrity.
 */
async function cleanupOrphanedUidsForUser(db, userId, keepUid) {
    const snapshot = await db
        .collection(exports.WEEKLY_UIDS_COLLECTION)
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
//# sourceMappingURL=uidUtils.js.map