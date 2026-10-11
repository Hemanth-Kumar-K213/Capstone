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

  // 10-digit Indian phone number: 2Factor API expects 10 digits
  if (cleaned.length === 10) {
    return {
      valid: true,
      normalized: cleaned,
    };
  }

  // 12-digit Indian phone number starting with 91: strip 91
  if (cleaned.length === 12 && cleaned.startsWith("91")) {
    return {
      valid: true,
      normalized: cleaned.substring(2),
    };
  }

  // E.164 with +91: strip +91
  if (phone.startsWith("+91") && cleaned.length >= 12) {
    return {
      valid: true,
      normalized: cleaned.slice(-10),
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
  sessionId?: string;
  otp?: string;
  error?: string;
}

/**
 * Send 6-digit OTP via 2Factor SMS-specific AUTOGEN2 API.
 * Documented Endpoint:
 *   GET https://2factor.in/API/V1/:api_key/SMS/:phone_number/AUTOGEN2/:otp_template_name
 *
 * Requirements:
 * - Explicitly uses the `/SMS/` endpoint.
 * - Strictly delivers via SMS (zero voice calls or voice fallback).
 * - Automatically generates a 6-digit OTP and returns both session ID (Details) and OTP in response.
 */
export async function sendOTP(
  phoneNumber: string,
  _arg2?: string,
  _arg3?: string
): Promise<TwoFactorResult> {
  try {
    const phoneValidation = validatePhoneNumber(phoneNumber);
    if (!phoneValidation.valid) {
      return { success: false, error: phoneValidation.error };
    }

    const normalizedPhone = phoneValidation.normalized!;
    const apiKey = getApiKey();
    const templateName =
      process.env.TWOFACTOR_OTP_TEMPLATE ||
      functions.config().twofactor?.otp_template ||
      "";

    if (!apiKey) {
      console.warn("[2FactorHelper] TWOFACTOR_API_KEY not configured.");
      return { success: false, error: "2Factor API Key not configured" };
    }

    // Attempt 1: Call AUTOGEN2 with custom template name if configured
    if (templateName) {
      try {
        const urlWithTemplate = `https://2factor.in/API/V1/${apiKey}/SMS/${normalizedPhone}/AUTOGEN2/${encodeURIComponent(templateName)}`;
        console.log(`[2FactorHelper] Dispatching SMS OTP via AUTOGEN2 with template '${templateName}' to ${normalizedPhone}...`);

        const response = await axios.get(urlWithTemplate, { timeout: 10000 });
        console.log(`[2FactorHelper] AUTOGEN2 template dispatch status: ${response.data?.Status}, sessionId: ${response.data?.Details}`);

        if (response.data?.Status === "Success") {
          return {
            success: true,
            messageId: response.data.Details,
            sessionId: response.data.Details,
            otp: response.data.OTP,
          };
        }
      } catch (templateErr: any) {
        console.warn(
          `[2FactorHelper] Template '${templateName}' dispatch note:`,
          templateErr?.response?.data || templateErr.message
        );
      }
    }

    // Attempt 2: Universal AUTOGEN2 endpoint (default approved SMS template)
    const defaultUrl = `https://2factor.in/API/V1/${apiKey}/SMS/${normalizedPhone}/AUTOGEN2`;
    console.log(`[2FactorHelper] Dispatching SMS OTP via universal AUTOGEN2 to ${normalizedPhone}...`);

    const response = await axios.get(defaultUrl, { timeout: 10000 });
    console.log(`[2FactorHelper] Universal AUTOGEN2 dispatch status: ${response.data?.Status}, sessionId: ${response.data?.Details}`);

    if (response.data?.Status === "Success") {
      return {
        success: true,
        messageId: response.data.Details,
        sessionId: response.data.Details,
        otp: response.data.OTP,
      };
    } else {
      return {
        success: false,
        error: response.data?.Details || "Failed to dispatch SMS OTP via 2Factor AUTOGEN2",
      };
    }
  } catch (error: any) {
    const errorDetails = error.response?.data?.Details || error.message || "Unknown error";
    console.error(`[2FactorHelper] Error sending SMS OTP to ${phoneNumber}:`, errorDetails);
    return {
      success: false,
      error: errorDetails,
    };
  }
}

/**
 * Verify OTP code against 2Factor server using official SMS/VERIFY endpoint.
 * Documented Endpoint:
 *   GET https://2factor.in/API/V1/:api_key/SMS/VERIFY/:session_id/:otp_input
 */
export async function verifyOTP(
  sessionId: string,
  otpInput: string
): Promise<{ success: boolean; error?: string }> {
  try {
    const apiKey = getApiKey();
    if (!apiKey) {
      return { success: false, error: "2Factor API key not configured" };
    }

    const verifyUrl = `https://2factor.in/API/V1/${apiKey}/SMS/VERIFY/${encodeURIComponent(sessionId)}/${encodeURIComponent(otpInput.trim())}`;
    console.log(`[2FactorHelper] Verifying OTP against 2Factor session ${sessionId}...`);

    const response = await axios.get(verifyUrl, { timeout: 10000 });
    console.log(`[2FactorHelper] VERIFY status: ${response.data?.Status}, details: ${response.data?.Details}`);

    if (response.data?.Status === "Success" && response.data?.Details === "OTP Matched") {
      return { success: true };
    } else {
      return {
        success: false,
        error: response.data?.Details || "OTP Mismatch",
      };
    }
  } catch (error: any) {
    const errorDetails = error.response?.data?.Details || error.message || "Verification request failed";
    console.error(`[2FactorHelper] Error verifying OTP:`, errorDetails);
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
        : "https://maps.google.com/?q=12.9716,77.5946";

    const formattedTime = new Date().toLocaleString("en-US", {
      month: "short",
      day: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });

    const message = `🚨 EMERGENCY ALERT 🚨\n${userName} triggered SOS and needs urgent help!\n\nLocation:\n${mapLink}\nTime: ${formattedTime}\n\nSent automatically from Trinetra.`;

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
