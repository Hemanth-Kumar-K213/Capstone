"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.sendTransactionalSMS = exports.sendSOSAlert = exports.sendOTP = exports.validatePhoneNumber = void 0;

const axios_1 = require("axios");
const functions = require("firebase-functions");
const dotenv = require("dotenv");

dotenv.config();

const getApiKey = () =>
    process.env.TWOFACTOR_API_KEY ||
    functions.config().twofactor?.api_key ||
    "";

const getSenderId = () =>
    process.env.TWOFACTOR_SENDER_ID ||
    functions.config().twofactor?.sender_id ||
    "WSAFETY";

const getApiUrl = () =>
    process.env.TWOFACTOR_API_URL ||
    "https://2factor.in/API/R1/";

/**
 * Validate and normalize phone numbers for 2Factor API (India format without leading '+')
 */
function validatePhoneNumber(phone) {
    if (!phone || typeof phone !== "string") {
        return {
            valid: false,
            error: "Phone number is required.",
        };
    }
    const cleaned = phone.replace(/\D/g, "");
    if (cleaned.length === 10) {
        return {
            valid: true,
            normalized: `91${cleaned}`,
        };
    }
    if (cleaned.length === 12 && cleaned.startsWith("91")) {
        return {
            valid: true,
            normalized: cleaned,
        };
    }
    if (phone.startsWith("+91") && cleaned.length === 12) {
        return {
            valid: true,
            normalized: cleaned,
        };
    }
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
exports.validatePhoneNumber = validatePhoneNumber;

/**
 * Send OTP via 2Factor API
 */
async function sendOTP(phoneNumber, arg2, arg3) {
    try {
        const phoneValidation = validatePhoneNumber(phoneNumber);
        if (!phoneValidation.valid) {
            return { success: false, error: phoneValidation.error };
        }
        const normalizedPhone = phoneValidation.normalized;
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
            module: "OTP",
            apikey: apiKey,
            to: normalizedPhone,
            from: senderId,
            msg: message,
        });
        console.log(`[2FactorHelper] Dispatching OTP to ${normalizedPhone} via 2Factor...`);
        const response = await axios_1.default.post(apiUrl, params.toString(), {
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
    } catch (error) {
        const errorDetails = error.response?.data?.Details || error.message || "Unknown error";
        console.error(`[2FactorHelper] Error sending OTP to ${phoneNumber}:`, errorDetails);
        return {
            success: false,
            error: errorDetails,
        };
    }
}
exports.sendOTP = sendOTP;

/**
 * Send SOS Alert via 2Factor API (module: TRANSACTIONAL_SMS)
 */
async function sendSOSAlert(phoneNumber, userName, latitude, longitude, address) {
    try {
        const phoneValidation = validatePhoneNumber(phoneNumber);
        if (!phoneValidation.valid) {
            return { success: false, error: phoneValidation.error };
        }
        const normalizedPhone = phoneValidation.normalized;
        const apiKey = getApiKey();
        const senderId = getSenderId();
        const apiUrl = getApiUrl();
        const mapLink =
            latitude !== 0 && longitude !== 0
                ? `https://maps.google.com/?q=${latitude},${longitude}`
                : "Location coordinates unavailable";
        const message = `🚨 EMERGENCY: ${userName} triggered SOS. Location: ${address || "GPS attached"} - ${mapLink}`;
        const params = new URLSearchParams({
            module: "TRANSACTIONAL_SMS",
            apikey: apiKey,
            to: normalizedPhone,
            from: senderId,
            msg: message,
        });
        console.log(`[2FactorHelper] Dispatching SOS alert to ${normalizedPhone}...`);
        const response = await axios_1.default.post(apiUrl, params.toString(), {
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
    } catch (error) {
        const errorDetails = error.response?.data?.Details || error.message || "Unknown error";
        console.error(`[2FactorHelper] Error sending SOS alert to ${phoneNumber}:`, errorDetails);
        return {
            success: false,
            error: errorDetails,
        };
    }
}
exports.sendSOSAlert = sendSOSAlert;

/**
 * Send General Transactional SMS via 2Factor API
 */
async function sendTransactionalSMS(phoneNumber, message) {
    try {
        const phoneValidation = validatePhoneNumber(phoneNumber);
        if (!phoneValidation.valid) {
            return { success: false, error: phoneValidation.error };
        }
        const normalizedPhone = phoneValidation.normalized;
        const apiKey = getApiKey();
        const senderId = getSenderId();
        const apiUrl = getApiUrl();
        const params = new URLSearchParams({
            module: "TRANSACTIONAL_SMS",
            apikey: apiKey,
            to: normalizedPhone,
            from: senderId,
            msg: message,
        });
        const response = await axios_1.default.post(apiUrl, params.toString(), {
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
    } catch (error) {
        const errorDetails = error.response?.data?.Details || error.message || "Unknown error";
        console.error(`[2FactorHelper] Error sending SMS to ${phoneNumber}:`, errorDetails);
        return {
            success: false,
            error: errorDetails,
        };
    }
}
exports.sendTransactionalSMS = sendTransactionalSMS;
