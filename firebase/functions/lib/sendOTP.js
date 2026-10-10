"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.verifyOTPFunction = exports.sendOTPFunction = void 0;

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const twoFactorHelper_1 = require("./twoFactorHelper");

if (!admin.apps.length) {
    admin.initializeApp();
}

const db = admin.firestore();

/**
 * HTTPS Callable Function: Generates and dispatches a 6-digit OTP via 2Factor API.
 */
exports.sendOTPFunction = functions.https.onCall(async (data, context) => {
    const phoneNumber = data?.phoneNumber;
    const userName = data?.userName || "User";

    if (!phoneNumber || typeof phoneNumber !== "string") {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "A valid phoneNumber is required to dispatch an OTP."
        );
    }

    const validation = (0, twoFactorHelper_1.validatePhoneNumber)(phoneNumber);
    if (!validation.valid || !validation.normalized) {
        throw new functions.https.HttpsError(
            "invalid-argument",
            validation.error || "Invalid phone number format."
        );
    }

    const normalizedPhone = validation.normalized;

    try {
        const otp = Math.floor(100000 + Math.random() * 900000).toString();
        const expiryTime = Date.now() + 10 * 60 * 1000;

        const otpDocRef = db.collection("otps").doc(normalizedPhone);
        const existingDoc = await otpDocRef.get();

        if (existingDoc.exists) {
            const existingData = existingDoc.data();
            if (existingData?.locked && existingData.lockedUntil > Date.now()) {
                const remainingMins = Math.ceil((existingData.lockedUntil - Date.now()) / 60000);
                throw new functions.https.HttpsError(
                    "resource-exhausted",
                    `This phone number is temporarily locked due to too many failed attempts. Try again in ${remainingMins} minute(s).`
                );
            }
        }

        const result = await (0, twoFactorHelper_1.sendOTP)(normalizedPhone, otp, userName);

        if (!result.success) {
            console.error(`[sendOTPFunction] 2Factor dispatch failure for ${normalizedPhone}:`, result.error);
            throw new functions.https.HttpsError(
                "internal",
                `2Factor SMS dispatch failed: ${result.error}`
            );
        }

        await otpDocRef.set({
            otp: otp,
            phoneNumber: normalizedPhone,
            userName: userName,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            expiresAt: expiryTime,
            verified: false,
            attempts: 0,
            locked: false,
            messageId: result.messageId || null,
        }, { merge: true });

        console.log(`[sendOTPFunction] Successfully dispatched OTP to ${normalizedPhone}. MessageId: ${result.messageId}`);

        return {
            success: true,
            message: `OTP sent to ${normalizedPhone}`,
            expiresIn: 600,
            messageId: result.messageId,
        };
    } catch (error) {
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        console.error("[sendOTPFunction] Unexpected error:", error);
        throw new functions.https.HttpsError(
            "internal",
            error instanceof Error ? error.message : "Failed to dispatch verification OTP."
        );
    }
});

/**
 * HTTPS Callable Function: Validates the 6-digit OTP code submitted by the mobile client.
 */
exports.verifyOTPFunction = functions.https.onCall(async (data, context) => {
    const phoneNumber = data?.phoneNumber;
    const otp = data?.otp;

    if (!phoneNumber || !otp) {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "Both phoneNumber and otp are required."
        );
    }

    const validation = (0, twoFactorHelper_1.validatePhoneNumber)(phoneNumber);
    if (!validation.valid || !validation.normalized) {
        throw new functions.https.HttpsError(
            "invalid-argument",
            validation.error || "Invalid phone number format."
        );
    }

    const normalizedPhone = validation.normalized;
    const submittedOtp = otp.toString().trim();

    try {
        const otpDocRef = db.collection("otps").doc(normalizedPhone);
        const otpDoc = await otpDocRef.get();

        if (!otpDoc.exists) {
            throw new functions.https.HttpsError(
                "not-found",
                "No active OTP request found for this phone number. Please request a new OTP."
            );
        }

        const otpData = otpDoc.data();

        if (otpData?.locked && (otpData.lockedUntil || 0) > Date.now()) {
            const remainingMins = Math.ceil((otpData.lockedUntil - Date.now()) / 60000);
            throw new functions.https.HttpsError(
                "permission-denied",
                `Too many invalid attempts. Phone number locked for ${remainingMins} minute(s).`
            );
        }

        if ((otpData?.expiresAt || 0) < Date.now()) {
            throw new functions.https.HttpsError(
                "deadline-exceeded",
                "OTP has expired. Please request a new code."
            );
        }

        if (otpData?.otp !== submittedOtp) {
            const currentAttempts = (otpData?.attempts || 0) + 1;
            const updates = {
                attempts: admin.firestore.FieldValue.increment(1),
            };

            if (currentAttempts >= 5) {
                updates.locked = true;
                updates.lockedUntil = Date.now() + 15 * 60 * 1000;
            }

            await otpDocRef.update(updates);

            if (currentAttempts >= 5) {
                throw new functions.https.HttpsError(
                    "permission-denied",
                    "Too many failed attempts. Phone locked for 15 minutes."
                );
            }

            const remainingAttempts = 5 - currentAttempts;
            throw new functions.https.HttpsError(
                "unauthenticated",
                `Invalid verification code. ${remainingAttempts} attempt(s) remaining.`
            );
        }

        await otpDocRef.update({
            verified: true,
            verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        console.log(`[verifyOTPFunction] Phone number verified successfully: ${normalizedPhone}`);

        return {
            success: true,
            message: "OTP verified successfully.",
            phoneNumber: normalizedPhone,
        };
    } catch (error) {
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }
        console.error("[verifyOTPFunction] Unexpected verification error:", error);
        throw new functions.https.HttpsError(
            "internal",
            error instanceof Error ? error.message : "Failed to verify OTP code."
        );
    }
});
