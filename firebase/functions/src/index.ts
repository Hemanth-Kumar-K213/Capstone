/**
 * Firebase Cloud Functions entry point for Women Safety System.
 *
 * Exports:
 * - onUserCreated: Generates first weekly UID when a User account is created
 * - rotateWeeklyUids: Weekly scheduled rotation of UIDs for all users
 * - sendOTPFunction: Generates and dispatches 6-digit verification code via 2Factor API
 * - verifyOTPFunction: Validates 6-digit OTP code submitted from mobile app
 * - onSOSEventCreated: Sends emergency SOS alert via 2Factor SMS to emergency contacts with GPS link
 */

import * as admin from "firebase-admin";

// Initialize Firebase Admin SDK
if (!admin.apps.length) {
  admin.initializeApp();
}

// UID Management Functions
export { onUserCreated, ensureActiveUidFunction } from "./generateUid";
export { rotateWeeklyUids } from "./rotateUids";

// 2Factor SMS Functions
export { sendOTPFunction, verifyOTPFunction } from "./sendOTP";
export { onSOSEventCreated } from "./sendSOSAlert";
