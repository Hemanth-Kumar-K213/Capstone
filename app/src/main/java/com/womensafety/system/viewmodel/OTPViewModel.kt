package com.womensafety.system.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.womensafety.system.data.sms.TwoFactorClient
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class OTPState(
    val isLoading: Boolean = false,
    val otpSent: Boolean = false,
    val isVerified: Boolean = false,
    val error: String? = null,
    val expiresIn: Int? = null, // Seconds remaining
    val attempts: Int = 0,
    val maxAttempts: Int = 5
)

/**
 * ViewModel managing phone number verification using 2Factor SMS.
 * Operates with dual-tier reliability:
 * 1. Primary: Firebase Cloud Functions (sendOTPFunction in us-central1 / asia-south1)
 * 2. Fallback: Direct 2Factor Gateway REST API dispatch (if Cloud Functions are not deployed or on Spark Plan)
 */
class OTPViewModel(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : ViewModel() {

    companion object {
        private const val TAG = "OTP_DEBUG"
        // In-memory direct OTP session storage for Spark plan / fallback mode
        private var lastDirectOtp: String? = null
        private var directOtpPhone: String? = null
        private var directOtpExpiry: Long = 0L
    }

    private val _state = MutableStateFlow(OTPState())
    val state: StateFlow<OTPState> = _state.asStateFlow()

    /**
     * Dispatches a 6-digit verification code via 2Factor SMS.
     * Attempts Cloud Functions first, and gracefully falls back to Direct 2Factor API on NOT_FOUND.
     */
    fun sendOTP(phoneNumber: String, userName: String) {
        val normalizedPhone = Validators.normalizePhone(phoneNumber)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val app = FirebaseApp.getInstance()
                Log.d(TAG, "Firebase App: ${app.name}")
                Log.d(TAG, "Project ID: ${app.options.projectId}")
                Log.d(TAG, "Calling sendOTPFunction for $normalizedPhone...")

                var otpSent = false
                var expiresIn = 600

                // Attempt 1: Call Cloud Function via default region (us-central1)
                try {
                    val payload = hashMapOf(
                        "phoneNumber" to normalizedPhone,
                        "userName" to userName.ifBlank { "User" }
                    )
                    val result = functions
                        .getHttpsCallable("sendOTPFunction")
                        .call(payload)
                        .await()

                    val data = result.getData() as? Map<*, *>
                    expiresIn = (data?.get("expiresIn") as? Number)?.toInt() ?: 600
                    otpSent = true
                    Log.d(TAG, "Success via default Cloud Function! Result: $data")
                } catch (e: Exception) {
                    Log.w(TAG, "Default region sendOTPFunction failed: ${e.message} (${e::class.simpleName})")

                    val isNotFound = (e is FirebaseFunctionsException &&
                            e.code == FirebaseFunctionsException.Code.NOT_FOUND)

                    if (isNotFound) {
                        // Attempt 2: Try asia-south1 region
                        try {
                            Log.d(TAG, "Attempting asia-south1 region for sendOTPFunction...")
                            val asiaFunctions = FirebaseFunctions.getInstance("asia-south1")
                            val payload = hashMapOf(
                                "phoneNumber" to normalizedPhone,
                                "userName" to userName.ifBlank { "User" }
                            )
                            val result = asiaFunctions
                                .getHttpsCallable("sendOTPFunction")
                                .call(payload)
                                .await()

                            val data = result.getData() as? Map<*, *>
                            expiresIn = (data?.get("expiresIn") as? Number)?.toInt() ?: 600
                            otpSent = true
                            Log.d(TAG, "Success via asia-south1 Cloud Function! Result: $data")
                        } catch (asiaEx: Exception) {
                            Log.w(TAG, "asia-south1 also returned: ${asiaEx.message}. Engaging Direct 2Factor Fallback...")

                            // Attempt 3: Direct 2Factor Gateway Fallback (Guaranteed to dispatch SMS even without Cloud Functions!)
                            val generatedOtp = (100000..999999).random().toString()
                            val directResult = TwoFactorClient.sendOTP(normalizedPhone, generatedOtp, userName)

                            if (directResult.isSuccess) {
                                lastDirectOtp = generatedOtp
                                directOtpPhone = normalizedPhone
                                directOtpExpiry = System.currentTimeMillis() + 10 * 60 * 1000
                                otpSent = true
                                Log.i(TAG, "Direct 2Factor SMS dispatched successfully! Message ID: ${directResult.getOrNull()}")
                            } else {
                                val err = directResult.exceptionOrNull()?.message ?: "2Factor dispatch error"
                                Log.e(TAG, "Direct 2Factor SMS failed: $err")
                                _state.value = _state.value.copy(
                                    isLoading = false,
                                    error = "SMS dispatch failed: $err. Please verify 2Factor API Key in TwoFactorClient.kt or deploy Cloud Functions."
                                )
                                return@launch
                            }
                        }
                    } else {
                        throw e
                    }
                }

                if (otpSent) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        otpSent = true,
                        expiresIn = expiresIn,
                        error = null,
                        attempts = 0
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send OTP error: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to send verification OTP via 2Factor SMS."
                )
            }
        }
    }

    /**
     * Verifies the 6-digit OTP code against Direct Session or Firestore Cloud Functions.
     */
    fun verifyOTP(phoneNumber: String, otp: String) {
        val normalizedPhone = Validators.normalizePhone(phoneNumber)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val submittedOtp = otp.trim()

            Log.d(TAG, "Verifying OTP for $normalizedPhone...")

            // Check Direct Fallback session first if active
            if (directOtpPhone == normalizedPhone && lastDirectOtp != null) {
                if (System.currentTimeMillis() > directOtpExpiry) {
                    Log.w(TAG, "Direct OTP expired for $normalizedPhone")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "OTP has expired. Please request a new one."
                    )
                    return@launch
                }

                if (lastDirectOtp == submittedOtp) {
                    Log.d(TAG, "Direct OTP verified successfully for $normalizedPhone!")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isVerified = true,
                        error = null
                    )
                    return@launch
                } else {
                    val newAttempts = _state.value.attempts + 1
                    Log.w(TAG, "Direct OTP mismatch. Attempt $newAttempts of 5")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        attempts = newAttempts,
                        error = "Invalid OTP code. Please try again."
                    )
                    return@launch
                }
            }

            // Otherwise verify via Cloud Functions
            try {
                val payload = hashMapOf(
                    "phoneNumber" to normalizedPhone,
                    "otp" to submittedOtp
                )

                var isVerified = false

                try {
                    val result = functions
                        .getHttpsCallable("verifyOTPFunction")
                        .call(payload)
                        .await()

                    val data = result.getData() as? Map<*, *>
                    isVerified = (data?.get("success") as? Boolean) == true
                } catch (e: Exception) {
                    if (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                        val asiaFunctions = FirebaseFunctions.getInstance("asia-south1")
                        val result = asiaFunctions
                            .getHttpsCallable("verifyOTPFunction")
                            .call(payload)
                            .await()
                        val data = result.getData() as? Map<*, *>
                        isVerified = (data?.get("success") as? Boolean) == true
                    } else {
                        throw e
                    }
                }

                if (isVerified) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isVerified = true,
                        error = null
                    )
                } else {
                    val newAttempts = _state.value.attempts + 1
                    _state.value = _state.value.copy(
                        isLoading = false,
                        attempts = newAttempts,
                        error = "Invalid OTP code. Please try again."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Verify OTP error: ${e.message}", e)
                val newAttempts = _state.value.attempts + 1
                _state.value = _state.value.copy(
                    isLoading = false,
                    attempts = newAttempts,
                    error = e.message ?: "Invalid OTP verification code."
                )
            }
        }
    }

    /**
     * Resends the verification OTP code
     */
    fun resendOTP(phoneNumber: String, userName: String) {
        sendOTP(phoneNumber, userName)
    }

    /**
     * Resets state back to default
     */
    fun resetState() {
        _state.value = OTPState()
    }
}
