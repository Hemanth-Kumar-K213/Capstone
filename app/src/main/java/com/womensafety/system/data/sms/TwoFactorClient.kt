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
 * Operates with dual-endpoint resilience:
 * 1. Primary: R1 endpoint with `module=TRANS_SMS` (DLT Sender ID e.g. WSAFETY)
 * 2. Fallback: V1 Open OTP endpoint (works instantly on trial/new accounts without DLT approval)
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

    fun normalizePhone(phone: String): String {
        val cleaned = phone.replace(Regex("\\D"), "")
        return when {
            cleaned.length == 10 -> "91$cleaned"
            cleaned.length == 12 && cleaned.startsWith("91") -> cleaned
            phone.startsWith("+91") && cleaned.length == 12 -> cleaned
            cleaned.length in 10..15 -> cleaned
            else -> cleaned
        }
    }

    /**
     * Sends a 6-digit OTP via 2Factor REST API (R1 TRANS_SMS with V1 Open OTP fallback)
     */
    suspend fun sendOTP(
        phoneNumber: String,
        otp: String,
        userName: String = "User"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (isPlaceholderKey()) {
            val err = "2Factor API Key contains placeholder '$apiKey'. Please set your active API Key from https://www.2factor.in/ in local.properties or TwoFactorClient.kt"
            Log.e(TAG, err)
            return@withContext Result.failure(Exception(err))
        }

        val normalized = normalizePhone(phoneNumber)
        val message = "Women Safety OTP: $otp. Valid for 10 minutes."

        // Attempt 1: Transactional R1 Route (module=TRANS_SMS)
        try {
            val params = "module=" + URLEncoder.encode("TRANS_SMS", "UTF-8") +
                    "&apikey=" + URLEncoder.encode(apiKey, "UTF-8") +
                    "&to=" + URLEncoder.encode(normalized, "UTF-8") +
                    "&from=" + URLEncoder.encode(senderId, "UTF-8") +
                    "&msg=" + URLEncoder.encode(message, "UTF-8")

            Log.d(TAG, "Dispatching 2Factor R1 OTP to $normalized...")

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

            Log.d(TAG, "2Factor R1 response ($responseCode): $responseText")

            val json = JSONObject(responseText)
            val status = json.optString("Status", "")
            val details = json.optString("Details", "")

            if (status.equals("Success", ignoreCase = true)) {
                return@withContext Result.success(details)
            }

            Log.w(TAG, "R1 dispatch returned $responseText. Checking V1 Open OTP fallback...")
        } catch (e: Exception) {
            Log.w(TAG, "R1 dispatch threw: ${e.message}. Attempting V1 Open OTP fallback...")
        }

        // Attempt 2: Universal V1 OTP Endpoint (Works for accounts without approved DLT sender ID)
        try {
            val v1Url = "https://2factor.in/API/V1/$apiKey/SMS/$normalized/$otp"
            Log.d(TAG, "Dispatching 2Factor V1 Open OTP to $normalized...")

            val url = URL(v1Url)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            Log.d(TAG, "2Factor V1 response ($responseCode): $responseText")

            val json = JSONObject(responseText)
            val status = json.optString("Status", "")
            val details = json.optString("Details", "")

            if (status.equals("Success", ignoreCase = true)) {
                return@withContext Result.success(details)
            } else {
                return@withContext Result.failure(Exception(if (details.isNotBlank()) details else "2Factor error: $responseText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct 2Factor dispatch exception", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Overload for direct OTP dispatch with message string
     */
    suspend fun sendOTP(
        phoneNumber: String,
        message: String
    ): Result<String> {
        val extractedOtp = Regex("\\b\\d{4,8}\\b").find(message)?.value
            ?: (100000..999999).random().toString()
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
        address: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalized = normalizePhone(phoneNumber)
            val mapLink = if (latitude != 0.0 && longitude != 0.0) {
                "https://maps.google.com/?q=$latitude,$longitude"
            } else {
                "Location unavailable"
            }
            val message = "🚨 EMERGENCY: $userName triggered SOS. Location: ${address ?: "GPS attached"} - $mapLink"

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
