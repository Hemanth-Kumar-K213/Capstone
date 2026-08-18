"use strict";
/**
 * Firebase Cloud Functions entry point.
 *
 * Exports:
 * - onUserCreated: Generates first weekly UID when a User account is created
 * - rotateWeeklyUids: Weekly scheduled rotation of UIDs for all users
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.rotateWeeklyUids = exports.onUserCreated = void 0;
const admin = require("firebase-admin");
// Initialize Firebase Admin SDK
admin.initializeApp();
// Export Cloud Functions
var generateUid_1 = require("./generateUid");
Object.defineProperty(exports, "onUserCreated", { enumerable: true, get: function () { return generateUid_1.onUserCreated; } });
var rotateUids_1 = require("./rotateUids");
Object.defineProperty(exports, "rotateWeeklyUids", { enumerable: true, get: function () { return rotateUids_1.rotateWeeklyUids; } });
//# sourceMappingURL=index.js.map