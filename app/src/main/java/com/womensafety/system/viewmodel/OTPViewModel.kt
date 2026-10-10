package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
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
 * ViewModel managing phone number verification using Twilio SMS Cloud Functions.
 * Handles dispatching 6-digit OTP codes, verifying responses, tracking attempts, and countdowns.
 */
class OTPViewModel(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) : ViewModel() {

    private val _state = MutableStateFlow(OTPState())
    val state: StateFlow<OTPState> = _state.asStateFlow()

    /**
     * Dispatches a 6-digit verification code via Twilio SMS by calling 'sendOTPFunction'
     */
    fun sendOTP(phoneNumber: String, userName: String) {
        val normalizedPhone = Validators.normalizePhone(phoneNumber)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

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
                val expiresIn = (data?.get("expiresIn") as? Number)?.toInt() ?: 600

                _state.value = _state.value.copy(
                    isLoading = false,
                    otpSent = true,
                    expiresIn = expiresIn,
                    error = null,
                    attempts = 0
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to send verification OTP via Twilio."
                )
            }
        }
    }

    /**
     * Verifies the 6-digit OTP code against Firestore records using 'verifyOTPFunction'
     */
    fun verifyOTP(phoneNumber: String, otp: String) {
        val normalizedPhone = Validators.normalizePhone(phoneNumber)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val payload = hashMapOf(
                    "phoneNumber" to normalizedPhone,
                    "otp" to otp.trim()
                )

                val result = functions
                    .getHttpsCallable("verifyOTPFunction")
                    .call(payload)
                    .await()

                val data = result.getData() as? Map<*, *>
                val success = (data?.get("success") as? Boolean) == true

                if (success) {
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
