/**
 * Cloud Function: rotateWeeklyUids
 *
 * Scheduled function that runs weekly to rotate UIDs for all active users.
 * - Idempotent: Checks expiry and avoids re-rotating recently generated UIDs
 * - Zero-Downtime: Replacement UID is committed atomically before retiring previous UID
 * - Transition Continuity: Retiring UID remains resolvable during a 2-hour grace period
 * - Error Isolation: Individual user rotation errors do not abort the entire batch
 *
 * Existing Trusted Contact relationships are NOT affected because they
 * reference stable Firebase Auth UIDs, never the weekly UID.
 *
 * Schedule: Every Monday at 00:00 UTC
 */

import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { createOrRotateUidForUser, cleanupOrphanedUidsForUser } from "./uidUtils";

export const rotateWeeklyUids = functions.pubsub
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

      console.log(`Starting weekly UID rotation evaluation for ${usersSnapshot.size} users`);

      let rotatedCount = 0;
      let skippedCount = 0;
      let errorCount = 0;

      for (const userDoc of usersSnapshot.docs) {
        const userId = userDoc.id;

        let attemptSuccess = false;
        const maxAttempts = 3;

        for (let attempt = 1; attempt <= maxAttempts && !attemptSuccess; attempt++) {
          try {
            // Idempotent: creates replacement only if missing, expired, or due for rotation
            const result = await createOrRotateUidForUser(db, userId, { force: false });

            if (result.rotated) {
              console.log(`Successfully rotated UID for user ${userId}: ${result.uid}`);
              rotatedCount++;
            } else {
              console.log(`User ${userId} UID ${result.uid} is still valid; rotation skipped.`);
              skippedCount++;
            }

            // Clean up any historical orphaned active UIDs
            await cleanupOrphanedUidsForUser(db, userId, result.uid);
            attemptSuccess = true;
          } catch (error) {
            console.warn(`Attempt ${attempt}/${maxAttempts} failed for user ${userId}:`, error);
            if (attempt === maxAttempts) {
              console.error(`Final failure rotating UID for user ${userId}`);
              errorCount++;
            }
          }
        }
      }

      console.log(
        `UID rotation complete. Rotated: ${rotatedCount}, Skipped: ${skippedCount}, Errors: ${errorCount}`
      );
      return null;
    } catch (error) {
      console.error("UID rotation batch execution failed:", error);
      throw error;
    }
  });
