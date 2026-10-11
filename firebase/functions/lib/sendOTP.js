"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.verifyOTPFunction = exports.sendOTPFunction = void 0;
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");
const twoFactorHelper_1 = require("./twoFactorHelper");
// Ensure Firebase Admin is initialized
if (!admin.apps.length) {
    admin.initializeApp();
}
const db = admin.firestore();
/**
 * Computes a salted cryptographic SHA-256 hash of the OTP and phone number.
 * Plaintext OTPs are NEVER persisted to the database.
 */
function hashOtp(phoneNumber, otp) {
    return crypto
        .createHash("sha256")
        .update(`${phoneNumber}:${otp.trim()}:TrinetraSalt2026`)
        .digest("hex");
}
/**
 * HTTPS Callable Function: Dispatches a 6-digit OTP via 2Factor SMS AUTOGEN2 endpoint.
 * Called from mobile application during User / Trusted Contact signup or phone verification.
 *
 * Security Protections:
 * - 60-second atomic resend cooldown enforced server-side.
 * - 15-minute lockout enforced server-side on brute-force attempts.
 * - Plaintext OTP is NEVER persisted in Firestore (only salted SHA-256 hash).
 * - Plaintext OTP is NEVER returned in the client response payload.
 */
exports.sendOTPFunction = functions.https.onCall(async (data, _context) => {
    const phoneNumber = data === null || data === void 0 ? void 0 : data.phoneNumber;
    const userName = (data === null || data === void 0 ? void 0 : data.userName) || "User";
    // 1. Input Validation
    if (!phoneNumber || typeof phoneNumber !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "A valid phoneNumber is required to dispatch an OTP.");
    }
    const validation = (0, twoFactorHelper_1.validatePhoneNumber)(phoneNumber);
    if (!validation.valid || !validation.normalized) {
        throw new functions.https.HttpsError("invalid-argument", validation.error || "Invalid phone number format.");
    }
    const normalizedPhone = validation.normalized;
    const otpDocRef = db.collection("otps").doc(normalizedPhone);
    const now = Date.now();
    try {
        // 2. Atomic Cooldown and Lockout Verification via Transaction
        await db.runTransaction(async (transaction) => {
            const existingDoc = await transaction.get(otpDocRef);
            if (existingDoc.exists) {
                const existingData = existingDoc.data();
                // Check if phone number is currently locked out
                if ((existingData === null || existingData === void 0 ? void 0 : existingData.locked) && (existingData.lockedUntil || 0) > now) {
                    const remainingMins = Math.ceil(((existingData.lockedUntil || 0) - now) / 60000);
                    throw new functions.https.HttpsError("resource-exhausted", `This phone number is temporarily locked due to too many failed attempts. Try again in ${remainingMins} minute(s).`);
                }
                // Check 60-second resend cooldown
                const lastSentAt = (existingData === null || existingData === void 0 ? void 0 : existingData.lastSentAt) || 0;
                if (lastSentAt && (now - lastSentAt) < 60000) {
                    const remainingSecs = Math.ceil((60000 - (now - lastSentAt)) / 1000);
                    throw new functions.https.HttpsError("resource-exhausted", `Please wait ${remainingSecs} second(s) before requesting another OTP.`);
                }
            }
            // Atomically claim the resend slot
            transaction.set(otpDocRef, {
                lastSentAt: now,
                pendingDispatch: true,
            }, { merge: true });
        });
        // 3. Dispatch 6-digit OTP strictly via 2Factor SMS AUTOGEN2 endpoint
        let result;
        try {
            result = await (0, twoFactorHelper_1.sendOTP)(normalizedPhone, userName);
        }
        catch (dispatchError) {
            // Revert lastSentAt on provider dispatch failure so user is not locked out of retry
            await otpDocRef.set({ lastSentAt: 0, pendingDispatch: false }, { merge: true });
            throw dispatchError;
        }
        if (!result.success || !result.otp) {
            await otpDocRef.set({ lastSentAt: 0, pendingDispatch: false }, { merge: true });
            console.error(`[sendOTPFunction] 2Factor SMS dispatch failure for ${normalizedPhone}:`, result.error);
            throw new functions.https.HttpsError("internal", `2Factor SMS dispatch failed: ${result.error || "Unable to send SMS OTP"}`);
        }
        // 4. Calculate 10-minute expiry time
        const expiryTime = now + 10 * 60 * 1000;
        const hashedOtp = hashOtp(normalizedPhone, result.otp);
        // 5. Secure Server-Side Storage: Store salted hash, NEVER plaintext OTP
        await otpDocRef.set({
            phoneNumber: normalizedPhone,
            userName: userName,
            otpHash: hashedOtp,
            sessionId: result.sessionId || result.messageId || null,
            messageId: result.messageId || null,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            lastSentAt: now,
            expiresAt: expiryTime,
            verified: false,
            consumed: false,
            attempts: 0,
            locked: false,
            pendingDispatch: false,
        }, { merge: true });
        console.log(`[sendOTPFunction] Successfully dispatched SMS OTP to ${normalizedPhone}. SessionId: ${result.sessionId}`);
        // 6. Return response to mobile client (OTP is strictly omitted)
        return {
            success: true,
            message: `OTP sent via SMS to ${normalizedPhone}`,
            expiresIn: 600, // 10 minutes in seconds
            messageId: result.messageId,
        };
    }
    catch (error) {
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        console.error("[sendOTPFunction] Unexpected error:", error);
        throw new functions.https.HttpsError("internal", error instanceof Error ? error.message : "Failed to dispatch verification OTP via SMS.");
    }
});
/**
 * HTTPS Callable Function: Authoritatively validates the 6-digit OTP code submitted by the mobile client.
 *
 * Security Protections:
 * - Atomic validation in Firestore transaction preventing concurrent brute-force bypass.
 * - Single-use enforcement (consumed flag prevents replay attacks).
 * - Expiration check (10 minutes) using authoritative server timestamp.
 * - Failed attempt counting with 15-minute lockout after 5 consecutive failures.
 * - Invalidates OTP challenge hash upon successful verification.
 */
exports.verifyOTPFunction = functions.https.onCall(async (data, _context) => {
    const phoneNumber = data === null || data === void 0 ? void 0 : data.phoneNumber;
    const otp = data === null || data === void 0 ? void 0 : data.otp;
    if (!phoneNumber || !otp) {
        throw new functions.https.HttpsError("invalid-argument", "Both phoneNumber and otp are required.");
    }
    const validation = (0, twoFactorHelper_1.validatePhoneNumber)(phoneNumber);
    if (!validation.valid || !validation.normalized) {
        throw new functions.https.HttpsError("invalid-argument", validation.error || "Invalid phone number format.");
    }
    const normalizedPhone = validation.normalized;
    const submittedOtp = otp.toString().trim();
    if (!/^\d{6}$/.test(submittedOtp)) {
        throw new functions.https.HttpsError("invalid-argument", "Verification code must be exactly 6 digits.");
    }
    const otpDocRef = db.collection("otps").doc(normalizedPhone);
    const now = Date.now();
    try {
        await db.runTransaction(async (transaction) => {
            const otpDoc = await transaction.get(otpDocRef);
            if (!otpDoc.exists) {
                throw new functions.https.HttpsError("not-found", "No active OTP request found for this phone number. Please request a new OTP.");
            }
            const otpData = otpDoc.data();
            // 1. Check if locked out
            if (otpData.locked && (otpData.lockedUntil || 0) > now) {
                const remainingMins = Math.ceil(((otpData.lockedUntil || 0) - now) / 60000);
                throw new functions.https.HttpsError("permission-denied", `Too many invalid attempts. Phone number locked for ${remainingMins} minute(s).`);
            }
            // 2. Check if already verified or consumed (prevent replay attacks)
            if (otpData.consumed || otpData.verified) {
                throw new functions.https.HttpsError("failed-precondition", "This verification code has already been used. Please request a new OTP.");
            }
            // 3. Check expiration (10 minutes)
            if ((otpData.expiresAt || 0) < now) {
                throw new functions.https.HttpsError("deadline-exceeded", "OTP has expired. Please request a new code.");
            }
            // 4. Check attempt limits
            const currentAttempts = otpData.attempts || 0;
            if (currentAttempts >= 5) {
                transaction.update(otpDocRef, {
                    locked: true,
                    lockedUntil: now + 15 * 60 * 1000,
                });
                throw new functions.https.HttpsError("permission-denied", "Too many failed attempts. Phone locked for 15 minutes.");
            }
            // 5. Verify the submitted OTP against salted cryptographic hash
            const submittedHash = hashOtp(normalizedPhone, submittedOtp);
            const expectedHash = otpData.otpHash;
            const isMatched = expectedHash
                ? expectedHash === submittedHash
                : otpData.otp === submittedOtp; // Fallback for pre-migration documents
            if (!isMatched) {
                const newAttempts = currentAttempts + 1;
                const updates = {
                    attempts: newAttempts,
                };
                if (newAttempts >= 5) {
                    updates.locked = true;
                    updates.lockedUntil = now + 15 * 60 * 1000; // 15-minute lockout
                }
                transaction.update(otpDocRef, updates);
                if (newAttempts >= 5) {
                    throw new functions.https.HttpsError("permission-denied", "Too many failed attempts. Phone locked for 15 minutes.");
                }
                const remainingAttempts = 5 - newAttempts;
                throw new functions.https.HttpsError("unauthenticated", `Invalid verification code. ${remainingAttempts} attempt(s) remaining.`);
            }
            // 6. Atomic Success: Consume challenge, invalidate hash and sessionId to prevent any replay
            transaction.update(otpDocRef, {
                verified: true,
                consumed: true,
                verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
                otpHash: null,
                otp: null,
                sessionId: null,
                attempts: 0,
                locked: false,
            });
        });
        console.log(`[verifyOTPFunction] Phone number verified successfully: ${normalizedPhone}`);
        return {
            success: true,
            message: "Phone number verified successfully",
            phoneNumber: normalizedPhone,
        };
    }
    catch (error) {
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        console.error("[verifyOTPFunction] Unexpected verification error:", error);
        throw new functions.https.HttpsError("internal", error instanceof Error ? error.message : "Failed to verify OTP code.");
    }
});
//# sourceMappingURL=sendOTP.js.map