"use strict";
/**
 * Cloud Function: rotateWeeklyUids
 *
 * Scheduled function that runs weekly to rotate UIDs for all active users.
 * - Expires old UIDs
 * - Generates new UIDs
 *
 * Existing Trusted Contact relationships are NOT affected because they
 * reference stable Firebase Auth UIDs, never the weekly UID.
 *
 * Schedule: Every Monday at 00:00 UTC
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.rotateWeeklyUids = void 0;
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const uidUtils_1 = require("./uidUtils");
exports.rotateWeeklyUids = functions.pubsub
    .schedule("0 0 * * 1") // Every Monday at midnight UTC
    .timeZone("UTC")
    .onRun(async () => {
    const db = admin.firestore();
    try {
        // Get all USER accounts
        const usersSnapshot = await db
            .collection("users")
            .where("accountType", "==", "USER")
            .get();
        console.log(`Rotating UIDs for ${usersSnapshot.size} users`);
        let successCount = 0;
        let errorCount = 0;
        for (const userDoc of usersSnapshot.docs) {
            const userId = userDoc.id;
            try {
                const uid = await (0, uidUtils_1.createUidForUser)(db, userId);
                console.log(`Rotated UID for user ${userId}: ${uid}`);
                successCount++;
            }
            catch (error) {
                console.error(`Failed to rotate UID for user ${userId}:`, error);
                errorCount++;
            }
        }
        console.log(`UID rotation complete. Success: ${successCount}, Errors: ${errorCount}`);
        return null;
    }
    catch (error) {
        console.error("UID rotation failed:", error);
        throw error;
    }
});
//# sourceMappingURL=rotateUids.js.map