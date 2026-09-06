"use strict";
/**
 * Cloud Function: onUserCreated
 *
 * Triggered when a new document is created in the 'users' collection.
 * Generates the first weekly UID for the newly registered user.
 *
 * This ensures the UID is generated on the backend, NOT by the Android client.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.onUserCreated = void 0;
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const uidUtils_1 = require("./uidUtils");
exports.onUserCreated = functions.firestore
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
        const uid = await (0, uidUtils_1.createUidForUser)(db, userId);
        console.log(`Generated initial UID ${uid} for user ${userId}`);
        return uid;
    }
    catch (error) {
        console.error(`Failed to generate UID for user ${userId}:`, error);
        throw error;
    }
});
//# sourceMappingURL=generateUid.js.map