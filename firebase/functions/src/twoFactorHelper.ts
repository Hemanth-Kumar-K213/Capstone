import axios from "axios";
import * as functions from "firebase-functions";
import * as dotenv from "dotenv";

dotenv.config();

// Retrieve 2Factor configuration from process.env or firebase functions:config()
const getApiKey = (): string =>
  process.env.TWOFACTOR_API_KEY ||
  functions.config().twofactor?.api_key ||
  "";

const getSenderId = (): string =>
  process.env.TWOFACTOR_SENDER_ID ||
  functions.config().twofactor?.sender_id ||
  "WSAFETY";

const getApiUrl = (): string =>
  process.env.TWOFACTOR_API_URL ||
  "https://2factor.in/API/R1/";

/**
 * Validate and normalize phone numbers for 2Factor API (India format without leading '+')
 */
export function validatePhoneNumber(phone: string): {
  valid: boolean;
  normalized?: string;
  error?: string;
} {
  if (!phone || typeof phone !== "string") {
    return {
      valid: false,
      error: "Phone number is required.",
    };
  }

  // Remove whitespace, dashes, parentheses
  const cleaned = phone.replace(/\D/g, "");

  // 10-digit Indian phone number: prepend 91
  if (cleaned.length === 10) {
    return {
      valid: true,
      normalized: `91${cleaned}`,
    };
  }

  // 12-digit Indian phone number starting with 91
  if (cleaned.length === 12 && cleaned.startsWith("91")) {
    return {
      valid: true,
      normalized: cleaned,
    };
  }

  // E.164 with +91
  if (phone.startsWith("+91") && cleaned.length === 12) {
    return {
      valid: true,
      normalized: cleaned,
    };
  }

  // Other international formats: if digits between 10 and 15, accept cleaned digits
  if (cleaned.length >= 10 && cleaned.length <= 15) {
    return {
      valid: true,
      normalized: cleaned,
    };
  }

  return {
    valid: false,
    error: `Invalid phone number: ${phone}. Expected format: 10-digit number or +91XXXXXXXXXX`,
  };
}

export interface TwoFactorResult {
  success: boolean;
  messageId?: string;
  otp?: string;
  error?: string;
}

/**
 * Send OTP via 2Factor API
 * Supports:
 * - sendOTP(phoneNumber, otpCode, userName)
 * - sendOTP(phoneNumber, userName)
 */
export async function sendOTP(
  phoneNumber: string,
  arg2?: string,
  arg3?: string
): Promise<TwoFactorResult> {
  try {
    const phoneValidation = validatePhoneNumber(phoneNumber);
    if (!phoneValidation.valid) {
      return { success: false, error: phoneValidation.error };
    }

    const normalizedPhone = phoneValidation.normalized!;

    // Resolve OTP and userName based on argument types
    let otp = "";
    let userName = "User";

    if (arg2 && /^\d{4,8}$/.test(arg2)) {
      otp = arg2;
      userName = arg3 || "User";
    } else {
      userName = arg2 || "User";
      otp = arg3 && /^\d{4,8}$/.test(arg3)
        ? arg3
        : Math.floor(100000 + Math.random() * 900000).toString();
    }

    const apiKey = getApiKey();
    const senderId = getSenderId();
    const apiUrl = getApiUrl();

    if (!apiKey) {
      console.warn("[2FactorHelper] TWOFACTOR_API_KEY not configured.");
    }

    const message = `Women Safety OTP: ${otp}. Valid for 10 minutes.`;

    const params = new URLSearchParams({
      module: "TRANS_SMS",
      apikey: apiKey,
      to: normalizedPhone,
      from: senderId,
      msg: message,
    });

    console.log(`[2FactorHelper] Dispatching OTP to ${normalizedPhone} via 2Factor...`);

    const response = await axios.post(apiUrl, params.toString(), {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      timeout: 10000,
    });

    console.log(`[2FactorHelper] Response for ${normalizedPhone}:`, response.data);

    if (response.data?.Status === "Success") {
      return {
        success: true,
        messageId: response.data.Details,
        otp: otp,
      };
    } else {
      return {
        success: false,
        error: response.data?.Details || "Failed to send OTP via 2Factor",
      };
    }
  } catch (error: any) {
    const errorDetails = error.response?.data?.Details || error.message || "Unknown error";
    console.error(`[2FactorHelper] Error sending OTP to ${phoneNumber}:`, errorDetails);
    return {
      success: false,
      error: errorDetails,
    };
  }
}

/**
 * Send SOS Alert via 2Factor API (module: TRANSACTIONAL_SMS)
 */
export async function sendSOSAlert(
  phoneNumber: string,
  userName: string,
  latitude: number,
  longitude: number,
  address?: string
): Promise<TwoFactorResult> {
  try {
    const phoneValidation = validatePhoneNumber(phoneNumber);
    if (!phoneValidation.valid) {
      return { success: false, error: phoneValidation.error };
    }

    const normalizedPhone = phoneValidation.normalized!;
    const apiKey = getApiKey();
    const senderId = getSenderId();
    const apiUrl = getApiUrl();

    const mapLink =
      latitude !== 0 && longitude !== 0
        ? `https://maps.google.com/?q=${latitude},${longitude}`
        : "Location coordinates unavailable";

    const message = `🚨 EMERGENCY: ${userName} triggered SOS. Location: ${address || "GPS attached"} - ${mapLink}`;

    const params = new URLSearchParams({
      module: "TRANS_SMS",
      apikey: apiKey,
      to: normalizedPhone,
      from: senderId,
      msg: message,
    });

    console.log(`[2FactorHelper] Dispatching SOS alert to ${normalizedPhone}...`);

    const response = await axios.post(apiUrl, params.toString(), {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      timeout: 10000,
    });

    console.log(`[2FactorHelper] SOS response for ${normalizedPhone}:`, response.data);

    if (response.data?.Status === "Success") {
      return {
        success: true,
        messageId: response.data.Details,
      };
    } else {
      return {
        success: false,
        error: response.data?.Details || "Failed to send SOS alert via 2Factor",
      };
    }
  } catch (error: any) {
    const errorDetails = error.response?.data?.Details || error.message || "Unknown error";
    console.error(`[2FactorHelper] Error sending SOS alert to ${phoneNumber}:`, errorDetails);
    return {
      success: false,
      error: errorDetails,
    };
  }
}

/**
 * Send General Transactional SMS via 2Factor API
 */
export async function sendTransactionalSMS(
  phoneNumber: string,
  message: string
): Promise<TwoFactorResult> {
  try {
    const phoneValidation = validatePhoneNumber(phoneNumber);
    if (!phoneValidation.valid) {
      return { success: false, error: phoneValidation.error };
    }

    const normalizedPhone = phoneValidation.normalized!;
    const apiKey = getApiKey();
    const senderId = getSenderId();
    const apiUrl = getApiUrl();

    const params = new URLSearchParams({
      module: "TRANS_SMS",
      apikey: apiKey,
      to: normalizedPhone,
      from: senderId,
      msg: message,
    });

    const response = await axios.post(apiUrl, params.toString(), {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      timeout: 10000,
    });

    if (response.data?.Status === "Success") {
      return {
        success: true,
        messageId: response.data.Details,
      };
    } else {
      return {
        success: false,
        error: response.data?.Details || "Failed to send SMS via 2Factor",
      };
    }
  } catch (error: any) {
    const errorDetails = error.response?.data?.Details || error.message || "Unknown error";
    console.error(`[2FactorHelper] Error sending SMS to ${phoneNumber}:`, errorDetails);
    return {
      success: false,
      error: errorDetails,
    };
  }
}
