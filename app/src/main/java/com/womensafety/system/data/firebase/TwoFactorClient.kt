package com.womensafety.system.data.firebase

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
 * Fallback client for testing and direct dispatch when Cloud Functions are unavailable.
 */
class TwoFactorClient {
    private val apiUrl = "https://2factor.in/API/R1/"
    private var apiKey = com.womensafety.system.BuildConfig.TWOFACTOR_API_KEY
    private var senderId = com.womensafety.system.BuildConfig.TWOFACTOR_SENDER_ID

    companion object {
        private const val TAG = "TwoFactorClientFirebase"
    }

    /**
     * Send OTP directly (fallback if Cloud Functions unavailable)
     */
    suspend fun sendOTP(
        phoneNumber: String,
        message: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleaned = phoneNumber.replace(Regex("\\D"), "")
            val normalizedPhone = when {
                phoneNumber.startsWith("+91") -> phoneNumber.substring(1)
                cleaned.length == 10 -> "91$cleaned"
                cleaned.length == 12 && cleaned.startsWith("91") -> cleaned
                else -> cleaned
            }

            if (apiKey.contains("XXXX", ignoreCase = true) || apiKey.isBlank()) {
                val err = "2Factor API Key contains placeholder '$apiKey'. Set your key in local.properties or TwoFactorClient.kt"
                Log.e(TAG, err)
                return@withContext Result.failure(Exception(err))
            }

            // Attempt 1: V1 Open OTP endpoint (works out of the box without DLT template)
            val otpMatch = Regex("\\b\\d{4,8}\\b").find(message)?.value
            if (otpMatch != null) {
                val v1Url = "https://2factor.in/API/V1/$apiKey/SMS/$normalizedPhone/$otpMatch"
                val v1Conn = (URL(v1Url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val v1Stream = if (v1Conn.responseCode in 200..299) v1Conn.inputStream else v1Conn.errorStream
                val v1Text = BufferedReader(InputStreamReader(v1Stream)).use { it.readText() }
                Log.d(TAG, "2Factor V1 Response: $v1Text")
                if (v1Text.contains("\"Status\":\"Success\"")) {
                    return@withContext Result.success(v1Text)
                }
            }

            // Attempt 2: R1 Route with module=TRANS_SMS
            val params = "module=" + URLEncoder.encode("TRANS_SMS", "UTF-8") +
                    "&apikey=" + URLEncoder.encode(apiKey, "UTF-8") +
                    "&to=" + URLEncoder.encode(normalizedPhone, "UTF-8") +
                    "&from=" + URLEncoder.encode(senderId, "UTF-8") +
                    "&msg=" + URLEncoder.encode(message, "UTF-8")

            val url = URL(apiUrl)
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

            Log.d(TAG, "2Factor R1 Response: $responseText")

            if (responseText.contains("\"Status\":\"Success\"")) {
                return@withContext Result.success(responseText)
            }

            Result.failure(Exception("2Factor error: $responseText"))
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            Result.failure(e)
        }
    }
}
