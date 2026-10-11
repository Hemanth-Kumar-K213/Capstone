"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.onSOSEventCreated = exports.verifyOTPFunction = exports.sendOTPFunction = exports.rotateWeeklyUids = exports.ensureActiveUidFunction = exports.onUserCreated = void 0;
const admin = require("firebase-admin");
// Initialize Firebase Admin SDK
if (!admin.apps.length) {
    admin.initializeApp();
}
// UID Management Functions
var generateUid_1 = require("./generateUid");
Object.defineProperty(exports, "onUserCreated", { enumerable: true, get: function () { return generateUid_1.onUserCreated; } });
Object.defineProperty(exports, "ensureActiveUidFunction", { enumerable: true, get: function () { return generateUid_1.ensureActiveUidFunction; } });
var rotateUids_1 = require("./rotateUids");
Object.defineProperty(exports, "rotateWeeklyUids", { enumerable: true, get: function () { return rotateUids_1.rotateWeeklyUids; } });
// 2Factor SMS Functions
var sendOTP_1 = require("./sendOTP");
Object.defineProperty(exports, "sendOTPFunction", { enumerable: true, get: function () { return sendOTP_1.sendOTPFunction; } });
Object.defineProperty(exports, "verifyOTPFunction", { enumerable: true, get: function () { return sendOTP_1.verifyOTPFunction; } });
var sendSOSAlert_1 = require("./sendSOSAlert");
Object.defineProperty(exports, "onSOSEventCreated", { enumerable: true, get: function () { return sendSOSAlert_1.onSOSEventCreated; } });
//# sourceMappingURL=index.js.map