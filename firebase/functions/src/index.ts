/**
 * Firebase Cloud Functions entry point.
 *
 * Exports:
 * - onUserCreated: Generates first weekly UID when a User account is created
 * - rotateWeeklyUids: Weekly scheduled rotation of UIDs for all users
 */

import * as admin from "firebase-admin";

// Initialize Firebase Admin SDK
admin.initializeApp();

// Export Cloud Functions
export { onUserCreated } from "./generateUid";
export { rotateWeeklyUids } from "./rotateUids";
