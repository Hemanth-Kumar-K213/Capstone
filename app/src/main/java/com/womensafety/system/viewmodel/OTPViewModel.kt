package com.womensafety.system.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.sms.TwoFactorClient
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 */
class OTPViewModel : ViewModel() {

    companion object {
        private const val TAG = "OTP_DEBUG"
        private var activeSessionId: String? = null
        private var activeSessionPhone: String? = null
    }

    private val _state = MutableStateFlow(OTPState())
    val state: StateFlow<OTPState> = _state.asStateFlow()

    /**
     * Dispatches a 6-digit verification code strictly via 2Factor SMS (AUTOGEN2 endpoint).
     */
    fun sendOTP(phoneNumber: String, userName: String) {
        val normalizedPhone = Validators.normalizePhone(phoneNumber)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                Log.d(TAG, "Calling TwoFactorClient.sendOTP for $normalizedPhone...")
                val result = TwoFactorClient.sendOTP(normalizedPhone, userName = userName)

                if (result.isSuccess) {
                    val sessionId = result.getOrNull()
                    activeSessionId = sessionId
                    activeSessionPhone = normalizedPhone
                    Log.d(TAG, "Success via TwoFactorClient! Session ID: $sessionId")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        otpSent = true,
                        expiresIn = 600,
                        error = null,
                        attempts = 0
                    )
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to dispatch verification OTP via SMS."
                    Log.e(TAG, "Send OTP error: $errorMsg")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send OTP error: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to dispatch verification OTP via SMS."
                )
            }
        }
    }

    /**
     * Authoritatively validates the 6-digit OTP code through 2Factor SMS/VERIFY.
     */
    fun verifyOTP(phoneNumber: String, otp: String) {
        val normalizedPhone = Validators.normalizePhone(phoneNumber)
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val submittedOtp = otp.trim()

            Log.d(TAG, "Verifying OTP for $normalizedPhone via 2Factor...")

            val sessionId = activeSessionId
            if (sessionId == null || activeSessionPhone != normalizedPhone) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "No active OTP request found. Please request an OTP first."
                )
                return@launch
            }

            try {
                val result = TwoFactorClient.verifyOTP(sessionId, submittedOtp)
                if (result.isSuccess && result.getOrDefault(false)) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isVerified = true,
                        error = null
                    )
                } else {
                    val newAttempts = _state.value.attempts + 1
                    val err = result.exceptionOrNull()?.message ?: "Invalid OTP code. Please try again."
                    _state.value = _state.value.copy(
                        isLoading = false,
                        attempts = newAttempts,
                        error = err
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
