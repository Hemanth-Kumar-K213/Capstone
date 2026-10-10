import twilio from "twilio";
import * as functions from "firebase-functions";
import * as dotenv from "dotenv";

dotenv.config();

// Attempt to read from environment variables or functions.config()
const accountSid =
  process.env.TWILIO_ACCOUNT_SID ||
  functions.config().twilio?.account_sid ||
  functions.config().twilio?.accountsid;

const authToken =
  process.env.TWILIO_AUTH_TOKEN ||
  functions.config().twilio?.auth_token ||
  functions.config().twilio?.authtoken;

const twilioPhoneNumber =
  process.env.TWILIO_PHONE_NUMBER ||
  functions.config().twilio?.phone_number ||
  functions.config().twilio?.phonenumber;

/**
 * Lazily instantiate Twilio client to prevent crashing at deployment time
 * if credentials have not been configured yet.
 */
function getTwilioClient(): twilio.Twilio | null {
  if (!accountSid || !authToken || !twilioPhoneNumber) {
    console.warn(
      "[TwilioHelper] Twilio credentials are missing or incomplete. Please set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_PHONE_NUMBER."
    );
    return null;
  }
  return twilio(accountSid, authToken);
}

/**
 * Normalizes and validates an E.164 formatted phone number.
 * Ensures the string begins with '+' and contains between 10 and 15 digits.
 */
export function formatToE164(phoneNumber: string): string {
  let cleaned = phoneNumber.trim().replace(/[\s\-\(\)]/g, "");
  if (!cleaned.startsWith("+")) {
    // Default to +91 (India) if standard 10-digit number is passed without country code
    if (/^\d{10}$/.test(cleaned)) {
      cleaned = "+91" + cleaned;
    } else {
      cleaned = "+" + cleaned;
    }
  }
  return cleaned;
}

export function isValidE164(phoneNumber: string): boolean {
  return /^\+[1-9]\d{9,14}$/.test(phoneNumber);
}

export interface SendSMSResult {
  success: boolean;
  sid?: string;
  error?: string;
}

/**
 * Send an SMS message via Twilio REST API
 */
export async function sendSMS(
  toPhoneNumber: string,
  message: string
): Promise<SendSMSResult> {
  try {
    const formattedPhone = formatToE164(toPhoneNumber);

    if (!isValidE164(formattedPhone)) {
      const errMsg = `Invalid phone number format: '${toPhoneNumber}'. Must be E.164 format (e.g. +919876543210)`;
      console.error(`[TwilioHelper] ${errMsg}`);
      return { success: false, error: errMsg };
    }

    const client = getTwilioClient();
    if (!client || !twilioPhoneNumber) {
      const errMsg = "Twilio credentials not configured in environment or functions config";
      console.error(`[TwilioHelper] ${errMsg}`);
      return { success: false, error: errMsg };
    }

    const result = await client.messages.create({
      body: message,
      from: twilioPhoneNumber,
      to: formattedPhone,
    });

    console.log(`[TwilioHelper] SMS dispatched successfully to ${formattedPhone}. SID: ${result.sid}`);

    return {
      success: true,
      sid: result.sid,
    };
  } catch (error: any) {
    console.error(`[TwilioHelper] Failed to send SMS to ${toPhoneNumber}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : "Unknown Twilio dispatch error",
    };
  }
}

/**
 * Sends a 6-digit verification OTP code via Twilio SMS
 */
export async function sendOTP(
  toPhoneNumber: string,
  otp: string,
  userName: string
): Promise<SendSMSResult> {
  const message = `[Women Safety System] Your verification code is: ${otp}. Valid for 10 minutes. Do not share this OTP with anyone. - ${userName}`;
  return sendSMS(toPhoneNumber, message);
}

/**
 * Sends an urgent emergency SOS SMS alert with GPS coordinates and live map link
 */
export async function sendSOSAlert(
  toPhoneNumber: string,
  userName: string,
  latitude: number,
  longitude: number,
  address?: string
): Promise<SendSMSResult> {
  const mapLink =
    latitude !== 0 && longitude !== 0
      ? `https://maps.google.com/?q=${latitude},${longitude}`
      : "Location coordinates unavailable";

  const message = `🚨 EMERGENCY ALERT 🚨
${userName} has triggered an SOS alert and needs immediate help!

📍 Location: ${address || "GPS location attached"}
🔗 Map Link: ${mapLink}
⏰ Time: ${new Date().toLocaleString()}

This is an automated emergency alert from the Women Safety System.`;

  return sendSMS(toPhoneNumber, message);
}
