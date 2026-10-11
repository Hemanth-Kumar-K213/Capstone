package com.womensafety.system.data.sms

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
 * Direct HTTP client for 2Factor SMS Gateway (https://2factor.in/API/R1/).
 * Serves as a reliable, direct fallback when Firebase Cloud Functions
 * are not yet deployed or when running on the Firebase Spark (free) tier.
 */
object TwoFactorClient {
    private const val TAG = "TwoFactorClient"
    const val API_URL = "https://2factor.in/API/R1/"

    // Replace with your actual 2Factor API Key from https://www.2factor.in/
    var apiKey: String = "7e825d24-XXXX-XXXX-XXXX-0200cd936042"
    var senderId: String = "WSAFETY"

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
     * Sends a 6-digit OTP via 2Factor REST API
     */
    suspend fun sendOTP(
        phoneNumber: String,
        otp: String,
        userName: String = "User"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalized = normalizePhone(phoneNumber)
            val message = "Women Safety OTP: $otp. Valid for 10 minutes."

            val params = "module=" + URLEncoder.encode("TRANS_SMS", "UTF-8") +
                    "&apikey=" + URLEncoder.encode(apiKey, "UTF-8") +
                    "&to=" + URLEncoder.encode(normalized, "UTF-8") +
                    "&from=" + URLEncoder.encode(senderId, "UTF-8") +
                    "&msg=" + URLEncoder.encode(message, "UTF-8")

            Log.d(TAG, "Dispatching direct 2Factor OTP to $normalized...")

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

            Log.d(TAG, "2Factor direct response ($responseCode): $responseText")

            val json = JSONObject(responseText)
            val status = json.optString("Status", "")
            val details = json.optString("Details", "")

            if (status.equals("Success", ignoreCase = true)) {
                Result.success(details)
            } else {
                Result.failure(Exception(if (details.isNotBlank()) details else "2Factor error: $responseText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct 2Factor dispatch exception", e)
            Result.failure(e)
        }
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
