"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.validatePhoneNumber = validatePhoneNumber;
exports.sendOTP = sendOTP;
exports.verifyOTP = verifyOTP;
exports.sendSOSAlert = sendSOSAlert;
exports.sendTransactionalSMS = sendTransactionalSMS;
const axios_1 = require("axios");
const functions = require("firebase-functions");
const dotenv = require("dotenv");
dotenv.config();
// Retrieve 2Factor configuration from process.env or firebase functions:config()
const getApiKey = () => {
    var _a;
    return process.env.TWOFACTOR_API_KEY ||
        ((_a = functions.config().twofactor) === null || _a === void 0 ? void 0 : _a.api_key) ||
        "";
};
const getSenderId = () => {
    var _a;
    return process.env.TWOFACTOR_SENDER_ID ||
        ((_a = functions.config().twofactor) === null || _a === void 0 ? void 0 : _a.sender_id) ||
        "WSAFETY";
};
const getApiUrl = () => process.env.TWOFACTOR_API_URL ||
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
async function sendOTP(phoneNumber, _arg2, _arg3) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l;
    try {
        const phoneValidation = validatePhoneNumber(phoneNumber);
        if (!phoneValidation.valid) {
            return { success: false, error: phoneValidation.error };
        }
        const normalizedPhone = phoneValidation.normalized;
        const apiKey = getApiKey();
        const templateName = process.env.TWOFACTOR_OTP_TEMPLATE ||
            ((_a = functions.config().twofactor) === null || _a === void 0 ? void 0 : _a.otp_template) ||
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
                const response = await axios_1.default.get(urlWithTemplate, { timeout: 10000 });
                console.log(`[2FactorHelper] AUTOGEN2 template dispatch status: ${(_b = response.data) === null || _b === void 0 ? void 0 : _b.Status}, sessionId: ${(_c = response.data) === null || _c === void 0 ? void 0 : _c.Details}`);
                if (((_d = response.data) === null || _d === void 0 ? void 0 : _d.Status) === "Success") {
                    return {
                        success: true,
                        messageId: response.data.Details,
                        sessionId: response.data.Details,
                        otp: response.data.OTP,
                    };
                }
            }
            catch (templateErr) {
                console.warn(`[2FactorHelper] Template '${templateName}' dispatch note:`, ((_e = templateErr === null || templateErr === void 0 ? void 0 : templateErr.response) === null || _e === void 0 ? void 0 : _e.data) || templateErr.message);
            }
        }
        // Attempt 2: Universal AUTOGEN2 endpoint (default approved SMS template)
        const defaultUrl = `https://2factor.in/API/V1/${apiKey}/SMS/${normalizedPhone}/AUTOGEN2`;
        console.log(`[2FactorHelper] Dispatching SMS OTP via universal AUTOGEN2 to ${normalizedPhone}...`);
        const response = await axios_1.default.get(defaultUrl, { timeout: 10000 });
        console.log(`[2FactorHelper] Universal AUTOGEN2 dispatch status: ${(_f = response.data) === null || _f === void 0 ? void 0 : _f.Status}, sessionId: ${(_g = response.data) === null || _g === void 0 ? void 0 : _g.Details}`);
        if (((_h = response.data) === null || _h === void 0 ? void 0 : _h.Status) === "Success") {
            return {
                success: true,
                messageId: response.data.Details,
                sessionId: response.data.Details,
                otp: response.data.OTP,
            };
        }
        else {
            return {
                success: false,
                error: ((_j = response.data) === null || _j === void 0 ? void 0 : _j.Details) || "Failed to dispatch SMS OTP via 2Factor AUTOGEN2",
            };
        }
    }
    catch (error) {
        const errorDetails = ((_l = (_k = error.response) === null || _k === void 0 ? void 0 : _k.data) === null || _l === void 0 ? void 0 : _l.Details) || error.message || "Unknown error";
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
async function verifyOTP(sessionId, otpInput) {
    var _a, _b, _c, _d, _e, _f, _g;
    try {
        const apiKey = getApiKey();
        if (!apiKey) {
            return { success: false, error: "2Factor API key not configured" };
        }
        const verifyUrl = `https://2factor.in/API/V1/${apiKey}/SMS/VERIFY/${encodeURIComponent(sessionId)}/${encodeURIComponent(otpInput.trim())}`;
        console.log(`[2FactorHelper] Verifying OTP against 2Factor session ${sessionId}...`);
        const response = await axios_1.default.get(verifyUrl, { timeout: 10000 });
        console.log(`[2FactorHelper] VERIFY status: ${(_a = response.data) === null || _a === void 0 ? void 0 : _a.Status}, details: ${(_b = response.data) === null || _b === void 0 ? void 0 : _b.Details}`);
        if (((_c = response.data) === null || _c === void 0 ? void 0 : _c.Status) === "Success" && ((_d = response.data) === null || _d === void 0 ? void 0 : _d.Details) === "OTP Matched") {
            return { success: true };
        }
        else {
            return {
                success: false,
                error: ((_e = response.data) === null || _e === void 0 ? void 0 : _e.Details) || "OTP Mismatch",
            };
        }
    }
    catch (error) {
        const errorDetails = ((_g = (_f = error.response) === null || _f === void 0 ? void 0 : _f.data) === null || _g === void 0 ? void 0 : _g.Details) || error.message || "Verification request failed";
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
async function sendSOSAlert(phoneNumber, userName, latitude, longitude, address) {
    var _a, _b, _c, _d;
    try {
        const phoneValidation = validatePhoneNumber(phoneNumber);
        if (!phoneValidation.valid) {
            return { success: false, error: phoneValidation.error };
        }
        const normalizedPhone = phoneValidation.normalized;
        const apiKey = getApiKey();
        const senderId = getSenderId();
        const apiUrl = getApiUrl();
        const mapLink = latitude !== 0 && longitude !== 0
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
        const response = await axios_1.default.post(apiUrl, params.toString(), {
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
            timeout: 10000,
        });
        console.log(`[2FactorHelper] SOS response for ${normalizedPhone}:`, response.data);
        if (((_a = response.data) === null || _a === void 0 ? void 0 : _a.Status) === "Success") {
            return {
                success: true,
                messageId: response.data.Details,
            };
        }
        else {
            return {
                success: false,
                error: ((_b = response.data) === null || _b === void 0 ? void 0 : _b.Details) || "Failed to send SOS alert via 2Factor",
            };
        }
    }
    catch (error) {
        const errorDetails = ((_d = (_c = error.response) === null || _c === void 0 ? void 0 : _c.data) === null || _d === void 0 ? void 0 : _d.Details) || error.message || "Unknown error";
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
async function sendTransactionalSMS(phoneNumber, message) {
    var _a, _b, _c, _d;
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
            module: "TRANS_SMS",
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
        if (((_a = response.data) === null || _a === void 0 ? void 0 : _a.Status) === "Success") {
            return {
                success: true,
                messageId: response.data.Details,
            };
        }
        else {
            return {
                success: false,
                error: ((_b = response.data) === null || _b === void 0 ? void 0 : _b.Details) || "Failed to send SMS via 2Factor",
            };
        }
    }
    catch (error) {
        const errorDetails = ((_d = (_c = error.response) === null || _c === void 0 ? void 0 : _c.data) === null || _d === void 0 ? void 0 : _d.Details) || error.message || "Unknown error";
        console.error(`[2FactorHelper] Error sending SMS to ${phoneNumber}:`, errorDetails);
        return {
            success: false,
            error: errorDetails,
        };
    }
}
//# sourceMappingURL=twoFactorHelper.js.map