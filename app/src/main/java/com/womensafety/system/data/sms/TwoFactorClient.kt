package com.womensafety.system.data.sms

import com.womensafety.system.BuildConfig
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Direct HTTP client for 2Factor SMS Gateway (https://2factor.in/).
 * Strictly delivers via SMS (enforces 10-digit Indian number format).
 */
object TwoFactorClient {
    private const val TAG = "TwoFactorClient"
    const val API_URL = "https://2factor.in/API/R1/"

    // Configured via BuildConfig (from local.properties) or overridden at runtime
    var apiKey: String = BuildConfig.TWOFACTOR_API_KEY
    var senderId: String = BuildConfig.TWOFACTOR_SENDER_ID

    fun isPlaceholderKey(): Boolean {
        return apiKey.isBlank() ||
                apiKey.contains("XXXX", ignoreCase = true) ||
                apiKey.contains("YOUR_", ignoreCase = true)
    }

    /**
     * Normalizes phone number to 10-digit format for 2Factor India SMS gateway.
     * 2Factor strictly expects 10 digits for Indian numbers (rejects 12-digit numbers with "Length Mismatch").
     */
    fun normalizePhone(phone: String): String {
        val cleaned = phone.replace(Regex("\\D"), "")
        return when {
            cleaned.length == 10 -> cleaned
            cleaned.length == 12 && cleaned.startsWith("91") -> cleaned.substring(2)
            cleaned.length == 11 && cleaned.startsWith("0") -> cleaned.substring(1)
            phone.startsWith("+91") && cleaned.length >= 12 -> cleaned.takeLast(10)
            cleaned.length > 10 -> cleaned.takeLast(10)
            else -> cleaned
        }
    }

    /**
     * Executes an HTTP GET request with timeout and error handling.
     */
    private fun executeGetRequest(urlString: String): Result<String> {
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (responseCode in 200..299) {
                Result.success(responseText)
            } else {
                Result.failure(Exception("HTTP $responseCode: $responseText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a 6-digit OTP via 2Factor dedicated SMS AUTOGEN API.
     *
     * Strict SMS Delivery:
     * - Uses 10-digit normalized Indian phone number format (bypasses length mismatch).
     * - Calls standard SMS /AUTOGEN endpoint (returns session ID).
     * - Falls back to /AUTOGEN2 if needed.
     */
    suspend fun sendOTP(
        phoneNumber: String,
        otp: String = "",
        userName: String = "User"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (isPlaceholderKey()) {
            val err = "2Factor API Key contains placeholder '$apiKey'. Please set your active API Key from https://www.2factor.in/ in local.properties or TwoFactorClient.kt"
            Log.e(TAG, err)
            return@withContext Result.failure(Exception(err))
        }

        val normalized = normalizePhone(phoneNumber)

        // Attempt 1: Standard SMS AUTOGEN endpoint
        try {
            val autogenUrl = "https://2factor.in/API/V1/$apiKey/SMS/$normalized/AUTOGEN"
            Log.d(TAG, "Dispatching 2Factor SMS OTP via AUTOGEN to $normalized...")

            val reqResult = executeGetRequest(autogenUrl)
            if (reqResult.isSuccess) {
                val responseText = reqResult.getOrNull()!!
                Log.d(TAG, "2Factor AUTOGEN response: $responseText")

                val json = JSONObject(responseText)
                val status = json.optString("Status", "")
                val details = json.optString("Details", "")

                if (status.equals("Success", ignoreCase = true)) {
                    return@withContext Result.success(details)
                }
            } else {
                Log.w(TAG, "AUTOGEN failed: ${reqResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "AUTOGEN request exception: ${e.message}")
        }

        // Attempt 2: Standard SMS AUTOGEN2 endpoint
        try {
            val autogen2Url = "https://2factor.in/API/V1/$apiKey/SMS/$normalized/AUTOGEN2"
            Log.d(TAG, "Dispatching 2Factor SMS OTP via AUTOGEN2 to $normalized...")

            val reqResult = executeGetRequest(autogen2Url)
            if (reqResult.isSuccess) {
                val responseText = reqResult.getOrNull()!!
                Log.d(TAG, "2Factor AUTOGEN2 response: $responseText")

                val json = JSONObject(responseText)
                val status = json.optString("Status", "")
                val details = json.optString("Details", "")

                if (status.equals("Success", ignoreCase = true)) {
                    return@withContext Result.success(details)
                }

                return@withContext Result.failure(Exception(if (details.isNotBlank()) details else "2Factor error: $responseText"))
            } else {
                return@withContext Result.failure(reqResult.exceptionOrNull() ?: Exception("HTTP request failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct 2Factor AUTOGEN2 dispatch exception", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Verifies the OTP with 2Factor server using official SMS/VERIFY endpoint.
     * Documented Endpoint:
     *   GET https://2factor.in/API/V1/:api_key/SMS/VERIFY/:session_id/:otp_input
     */
    suspend fun verifyOTP(
        sessionId: String,
        otpInput: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val verifyUrl = "https://2factor.in/API/V1/$apiKey/SMS/VERIFY/${URLEncoder.encode(sessionId, "UTF-8")}/${URLEncoder.encode(otpInput.trim(), "UTF-8")}"
            Log.d(TAG, "Verifying OTP via 2Factor SMS/VERIFY endpoint...")

            val reqResult = executeGetRequest(verifyUrl)
            if (reqResult.isSuccess) {
                val responseText = reqResult.getOrNull()!!
                Log.d(TAG, "2Factor VERIFY response: $responseText")

                val json = JSONObject(responseText)
                val status = json.optString("Status", "")
                val details = json.optString("Details", "")

                if (status.equals("Success", ignoreCase = true) && details.contains("Match", ignoreCase = true)) {
                    Result.success(true)
                } else {
                    Result.success(false)
                }
            } else {
                Result.failure(reqResult.exceptionOrNull() ?: Exception("Verification request failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "2Factor SMS/VERIFY exception", e)
            Result.failure(e)
        }
    }

    /**
     * Overload for direct OTP dispatch with message string
     */
    suspend fun sendOTP(
        phoneNumber: String,
        message: String
    ): Result<String> {
        val extractedOtp = Regex("\\b\\d{4,8}\\b").find(message)?.value ?: ""
        return sendOTP(phoneNumber, extractedOtp, "User")
    }

    /**
     * Sends an Emergency SOS SMS alert via 2Factor REST API
     */
    suspend fun sendSOSAlert(
        phoneNumber: String,
        userName: String,
        latitude: Double,
        longitude: Double,
        address: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalized = normalizePhone(phoneNumber)
            val timeStr = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.US).format(java.util.Date(timestamp))
            val mapLink = if (latitude != 0.0 && longitude != 0.0) {
                "https://maps.google.com/?q=$latitude,$longitude"
            } else {
                "https://maps.google.com/?q=12.9716,77.5946"
            }
            val message = "🚨 EMERGENCY ALERT 🚨\n" +
                    "$userName triggered SOS and needs urgent help!\n\n" +
                    "Location:\n" +
                    "$mapLink\n" +
                    "Time: $timeStr\n\n" +
                    "Sent automatically from Trinetra."

            val params = "module=" + URLEncoder.encode("TRANS_SMS", "UTF-8") +
                    "&apikey=" + URLEncoder.encode(apiKey, "UTF-8") +
                    "&to=" + URLEncoder.encode(normalized, "UTF-8") +
                    "&from=" + URLEncoder.encode(senderId, "UTF-8") +
                    "&msg=" + URLEncoder.encode(message, "UTF-8")

            Log.d(TAG, "Dispatching direct 2Factor SOS alert to $normalized...")

            val url = URL(API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(params)
                writer.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            val json = JSONObject(responseText)
            val status = json.optString("Status", "")
            val details = json.optString("Details", "")

            if (status.equals("Success", ignoreCase = true)) {
                Result.success(details)
            } else {
                Result.failure(Exception(if (details.isNotBlank()) details else "2Factor error: $responseText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct 2Factor SOS dispatch exception", e)
            Result.failure(e)
        }
    }
}
