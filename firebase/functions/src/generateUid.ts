/**
 * Cloud Functions: UID Generation & On-Demand Recovery
 *
 * - onUserCreated: Triggered when a new document is created in the 'users' collection.
 *   Generates the initial weekly UID for newly registered USER accounts.
 *
 * - ensureActiveUidFunction: HTTPS Callable Function for the mobile client.
 *   Enforces server-authoritative UID creation and recovery. If the user's UID
 *   is missing, expired, or inconsistent due to a delayed scheduler, this function
 *   safely creates or rotates it on the backend without requiring client-side minting.
 */

import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { createOrRotateUidForUser, cleanupOrphanedUidsForUser } from "./uidUtils";

export const onUserCreated = functions.firestore
  .document("users/{userId}")
  .onCreate(async (snapshot, context) => {
    const userId = context.params.userId;
    const data = snapshot.data();

    // Only generate UIDs for USER accounts, not TRUSTED_CONTACT
    if (data.accountType !== "USER") {
      console.log(`Skipping UID generation for non-USER account: ${userId}`);
      return null;
    }

    try {
      const db = admin.firestore();
      const result = await createOrRotateUidForUser(db, userId, { force: true });
      console.log(`Generated initial UID ${result.uid} for user ${userId}`);
      await cleanupOrphanedUidsForUser(db, userId, result.uid);
      return result.uid;
    } catch (error) {
      console.error(`Failed to generate UID for user ${userId}:`, error);
      throw error;
    }
  });

/**
 * Callable Function: ensureActiveUidFunction
 * Allows authenticated USER clients to request backend verification / recovery of their UID.
 */
export const ensureActiveUidFunction = functions.https.onCall(
  async (_data: any, context: functions.https.CallableContext) => {
    if (!context.auth || !context.auth.uid) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Authentication required to verify or refresh Safety UID."
      );
    }

    const userId = context.auth.uid;
    const db = admin.firestore();

    try {
      const userDoc = await db.collection("users").doc(userId).get();
      if (!userDoc.exists) {
        throw new functions.https.HttpsError("not-found", "User profile not found.");
      }

      const userData = userDoc.data();
      if (userData?.accountType !== "USER") {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "UID operations are only allowed for USER accounts."
        );
      }

      // Idempotent: returns existing active UID or rotates if expired/missing
      const result = await createOrRotateUidForUser(db, userId, { force: false });
      await cleanupOrphanedUidsForUser(db, userId, result.uid);

      return {
        success: true,
        uid: result.uid,
        expiresAt: result.expiresAt,
        generatedAt: result.generatedAt,
        userName: result.userName || "",
        rotated: result.rotated,
      };
    } catch (error: any) {
      if (error instanceof functions.https.HttpsError) {
        throw error;
      }
      console.error(`ensureActiveUidFunction failed for user ${userId}:`, error);
      throw new functions.https.HttpsError(
        "internal",
        error.message || "Failed to ensure active Safety UID."
      );
    }
  }
);
