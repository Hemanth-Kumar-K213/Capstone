import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { sendOTP, formatToE164 } from "./twilioHelper";

// Ensure Firebase Admin is initialized
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * HTTPS Callable Function: Generates and dispatches a 6-digit OTP via Twilio SMS.
 * Called from mobile application during User / Trusted Contact signup or 2FA login.
 */
export const sendOTPFunction = functions.https.onCall(
  async (data: any, context: functions.https.CallableContext) => {
    const phoneNumber = data?.phoneNumber;
    const userName = data?.userName || "User";

    // 1. Input Validation
    if (!phoneNumber || typeof phoneNumber !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "A valid phoneNumber is required to dispatch an OTP."
      );
    }

    const formattedPhone = formatToE164(phoneNumber);

    try {
      // 2. Generate secure 6-digit numeric OTP
      const otp = Math.floor(100000 + Math.random() * 900000).toString();

      // 3. Calculate 10-minute expiry time
      const expiryTime = Date.now() + 10 * 60 * 1000; // 10 minutes in milliseconds

      // 4. Check existing OTP document for rate limiting / lockout
      const otpDocRef = db.collection("otps").doc(formattedPhone);
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

      // 5. Store OTP record in Firestore
      await otpDocRef.set(
        {
          otp: otp,
          phoneNumber: formattedPhone,
          userName: userName,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          expiresAt: expiryTime,
          verified: false,
          attempts: 0,
          locked: false,
        },
        { merge: true }
      );

      // 6. Send OTP via Twilio SMS
      const smsResult = await sendOTP(formattedPhone, otp, userName);

      if (!smsResult.success) {
        console.error(`[sendOTPFunction] Twilio dispatch failure for ${formattedPhone}:`, smsResult.error);
        throw new functions.https.HttpsError(
          "internal",
          `Twilio SMS dispatch failed: ${smsResult.error}`
        );
      }

      console.log(`[sendOTPFunction] Successfully dispatched OTP to ${formattedPhone}. SID: ${smsResult.sid}`);

      return {
        success: true,
        message: `OTP successfully dispatched to ${formattedPhone}`,
        expiresIn: 600, // 10 minutes in seconds
        sid: smsResult.sid,
      };
    } catch (error: any) {
      if (error instanceof functions.https.HttpsError) {
        throw error;
      }
      console.error("[sendOTPFunction] Unexpected error:", error);
      throw new functions.https.HttpsError(
        "internal",
        error instanceof Error ? error.message : "Failed to dispatch verification OTP."
      );
    }
  }
);

/**
 * HTTPS Callable Function: Validates the 6-digit OTP code submitted by the mobile client.
 */
export const verifyOTPFunction = functions.https.onCall(
  async (data: any, context: functions.https.CallableContext) => {
    const phoneNumber = data?.phoneNumber;
    const otp = data?.otp;

    if (!phoneNumber || !otp) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Both phoneNumber and otp are required."
      );
    }

    const formattedPhone = formatToE164(phoneNumber);
    const submittedOtp = otp.toString().trim();

    try {
      const otpDocRef = db.collection("otps").doc(formattedPhone);
      const otpDoc = await otpDocRef.get();

      if (!otpDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "No active OTP request found for this phone number. Please request a new OTP."
        );
      }

      const otpData = otpDoc.data();

      // Check if locked
      if (otpData?.locked && (otpData.lockedUntil || 0) > Date.now()) {
        const remainingMins = Math.ceil((otpData.lockedUntil - Date.now()) / 60000);
        throw new functions.https.HttpsError(
          "permission-denied",
          `Too many invalid attempts. Phone number locked for ${remainingMins} minute(s).`
        );
      }

      // Check expiration
      if ((otpData?.expiresAt || 0) < Date.now()) {
        throw new functions.https.HttpsError(
          "deadline-exceeded",
          "OTP has expired. Please request a new code."
        );
      }

      // Verify OTP match
      if (otpData?.otp !== submittedOtp) {
        const currentAttempts = (otpData?.attempts || 0) + 1;
        const updates: any = {
          attempts: admin.firestore.FieldValue.increment(1),
        };

        if (currentAttempts >= 5) {
          updates.locked = true;
          updates.lockedUntil = Date.now() + 15 * 60 * 1000; // 15-minute lockout
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

      // Mark verified
      await otpDocRef.update({
        verified: true,
        verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      console.log(`[verifyOTPFunction] Phone number verified successfully: ${formattedPhone}`);

      return {
        success: true,
        message: "Phone number verified successfully.",
        phoneNumber: formattedPhone,
      };
    } catch (error: any) {
      if (error instanceof functions.https.HttpsError) {
        throw error;
      }
      console.error("[verifyOTPFunction] Unexpected verification error:", error);
      throw new functions.https.HttpsError(
        "internal",
        error instanceof Error ? error.message : "Failed to verify OTP code."
      );
    }
  }
);
