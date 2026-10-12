package com.womensafety.system.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.data.sms.TwoFactorClient
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Trusted Contact signup and login via 2Factor phone OTP.
 * Eliminates Firebase Phone Auth BILLING_NOT_ENABLED errors by routing
 * OTP verification through 2Factor SMS and establishing authenticated Firebase sessions.
 */
class TrustedContactAuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val TAG = "TrustedContactAuthVM"

    // ─── Signup State ────────────────────────────────────────────

    data class TCSignupState(
        val name: String = "",
        val phone: String = "",
        val verificationId: String? = null,
        val otpCode: String = "",
        val otpSent: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val accountCreated: Boolean = false,
        val resendCooldown: Int = 0
    )

    private val _signupState = MutableStateFlow(TCSignupState())
    val signupState: StateFlow<TCSignupState> = _signupState.asStateFlow()

    // ─── Login State ─────────────────────────────────────────────

    data class TCLoginState(
        val phone: String = "",
        val verificationId: String? = null,
        val otpCode: String = "",
        val otpSent: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val isLoggedIn: Boolean = false,
        val resendCooldown: Int = 0
    )

    private val _loginState = MutableStateFlow(TCLoginState())
    val loginState: StateFlow<TCLoginState> = _loginState.asStateFlow()

    // ─── Signup Methods ──────────────────────────────────────────

    fun updateSignupName(name: String) {
        _signupState.value = _signupState.value.copy(name = name, error = null)
    }

    fun updateSignupPhone(phone: String) {
        _signupState.value = _signupState.value.copy(phone = phone, error = null)
    }

    fun updateSignupOtp(code: String) {
        val digits = code.filter { it.isDigit() }
        if (digits.length <= 6) {
            _signupState.value = _signupState.value.copy(otpCode = digits, error = null)
        }
    }

    fun sendSignupOtp(activity: Activity? = null) {
        val state = _signupState.value

        if (!Validators.isValidName(state.name)) {
            _signupState.value = state.copy(error = "Please enter your full name.")
            return
        }

        val normalized = Validators.normalizePhone(state.phone)
        if (!Validators.isValidPhone(normalized)) {
            _signupState.value = state.copy(error = "Please enter a valid phone number.")
            return
        }

        _signupState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                Log.d(TAG, "Dispatching 2Factor SMS OTP for TC signup to $normalized...")
                val result = TwoFactorClient.sendOTP(normalized, userName = state.name)

                if (result.isSuccess) {
                    val sessionId = result.getOrNull()
                    Log.i(TAG, "2Factor OTP sent successfully! Session: $sessionId")
                    _signupState.value = _signupState.value.copy(
                        isLoading = false,
                        verificationId = sessionId ?: "2FACTOR_TC_SIGNUP",
                        otpSent = true,
                        resendCooldown = 60,
                        error = null
                    )
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Failed to dispatch verification OTP via SMS."
                    Log.e(TAG, "2Factor OTP dispatch failed: $errMsg")
                    _signupState.value = _signupState.value.copy(
                        isLoading = false,
                        error = errMsg
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during 2Factor sendSignupOtp", e)
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to send verification OTP via SMS."
                )
            }
        }
    }

    fun verifySignupOtp() {
        val state = _signupState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _signupState.value = state.copy(error = "Please enter a valid 6-digit OTP.")
            return
        }

        val verificationId = state.verificationId
        if (verificationId.isNullOrBlank()) {
            _signupState.value = state.copy(error = "No active OTP session found. Please request OTP again.")
            return
        }

        val normalized = Validators.normalizePhone(state.phone)
        _signupState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                Log.d(TAG, "Verifying 2Factor OTP for session $verificationId...")
                val verifyResult = TwoFactorClient.verifyOTP(verificationId, state.otpCode.trim())

                if (verifyResult.isSuccess && verifyResult.getOrNull() == true) {
                    Log.i(TAG, "2Factor OTP verified successfully! Completing Firebase Auth setup...")
                    completeSignupWith2Factor(state.name, normalized)
                } else {
                    val err = verifyResult.exceptionOrNull()?.message ?: "Invalid OTP code. Please check and try again."
                    _signupState.value = _signupState.value.copy(
                        isLoading = false,
                        error = err
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during verifySignupOtp", e)
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = e.message ?: "OTP verification failed. Please try again."
                )
            }
        }
    }

    private suspend fun completeSignupWith2Factor(name: String, normalizedPhone: String) {
        val cleanPhone = normalizedPhone.replace("+", "")
        val tcEmail = "tc_${cleanPhone}@womensafety.internal"
        val tcPassword = "TC_${cleanPhone}_Trinetra2026!"

        var authResult = authRepository.loginWithEmailAndPassword(tcEmail, tcPassword)
        if (authResult.isFailure) {
            authResult = authRepository.createUserWithEmailAndPassword(tcEmail, tcPassword)
        }

        if (authResult.isSuccess) {
            val uid = authResult.getOrNull()!!
            val profileResult = authRepository.createTrustedContactProfile(
                authUserId = uid,
                name = name,
                phone = normalizedPhone
            )
            if (profileResult.isSuccess) {
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    accountCreated = true,
                    error = null
                )
            } else {
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = profileResult.exceptionOrNull()?.message ?: "Failed to save profile in database."
                )
            }
        } else {
            _signupState.value = _signupState.value.copy(
                isLoading = false,
                error = authResult.exceptionOrNull()?.message ?: "Authentication setup failed."
            )
        }
    }

    // ─── Login Methods ───────────────────────────────────────────

    fun updateLoginPhone(phone: String) {
        _loginState.value = _loginState.value.copy(phone = phone, error = null)
    }

    fun updateLoginOtp(code: String) {
        val digits = code.filter { it.isDigit() }
        if (digits.length <= 6) {
            _loginState.value = _loginState.value.copy(otpCode = digits, error = null)
        }
    }

    fun sendLoginOtp(activity: Activity? = null) {
        val state = _loginState.value
        val normalized = Validators.normalizePhone(state.phone)

        if (!Validators.isValidPhone(normalized)) {
            _loginState.value = state.copy(error = "Please enter a valid phone number.")
            return
        }

        _loginState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                Log.d(TAG, "Dispatching 2Factor SMS OTP for TC login to $normalized...")
                val result = TwoFactorClient.sendOTP(normalized, userName = "Trusted Contact")

                if (result.isSuccess) {
                    val sessionId = result.getOrNull()
                    Log.i(TAG, "2Factor login OTP sent successfully! Session: $sessionId")
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        verificationId = sessionId ?: "2FACTOR_TC_LOGIN",
                        otpSent = true,
                        resendCooldown = 60,
                        error = null
                    )
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Failed to dispatch verification OTP via SMS."
                    Log.e(TAG, "2Factor login OTP failed: $errMsg")
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        error = errMsg
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during sendLoginOtp", e)
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to dispatch verification OTP."
                )
            }
        }
    }

    fun verifyLoginOtp() {
        val state = _loginState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _loginState.value = state.copy(error = "Please enter a valid 6-digit OTP.")
            return
        }

        val verificationId = state.verificationId
        if (verificationId.isNullOrBlank()) {
            _loginState.value = state.copy(error = "No active OTP session found. Please request OTP again.")
            return
        }

        val normalized = Validators.normalizePhone(state.phone)
        _loginState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                Log.d(TAG, "Verifying 2Factor login OTP for session $verificationId...")
                val verifyResult = TwoFactorClient.verifyOTP(verificationId, state.otpCode.trim())

                if (verifyResult.isSuccess && verifyResult.getOrNull() == true) {
                    Log.i(TAG, "2Factor login OTP verified! Establishing authenticated session...")
                    completeLoginWith2Factor(normalized)
                } else {
                    val err = verifyResult.exceptionOrNull()?.message ?: "Invalid OTP code. Please check and try again."
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        error = err
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during verifyLoginOtp", e)
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    error = e.message ?: "OTP verification failed. Please try again."
                )
            }
        }
    }

    private suspend fun completeLoginWith2Factor(normalizedPhone: String) {
        val cleanPhone = normalizedPhone.replace("+", "")
        val tcEmail = "tc_${cleanPhone}@womensafety.internal"
        val tcPassword = "TC_${cleanPhone}_Trinetra2026!"

        var signInResult = authRepository.loginWithEmailAndPassword(tcEmail, tcPassword)
        if (signInResult.isFailure) {
            signInResult = authRepository.createUserWithEmailAndPassword(tcEmail, tcPassword)
        }

        if (signInResult.isSuccess) {
            val uid = signInResult.getOrNull()!!
            val profile = authRepository.getCurrentAccountType()
            if (profile.getOrNull() == null) {
                authRepository.createTrustedContactProfile(
                    authUserId = uid,
                    name = "Trusted Contact",
                    phone = normalizedPhone
                )
            }
            _loginState.value = _loginState.value.copy(
                isLoading = false,
                isLoggedIn = true,
                error = null
            )
        } else {
            _loginState.value = _loginState.value.copy(
                isLoading = false,
                error = "Login authentication failed: ${signInResult.exceptionOrNull()?.message}"
            )
        }
    }

    fun updateSignupResendCooldown(seconds: Int) {
        _signupState.value = _signupState.value.copy(resendCooldown = seconds)
    }

    fun updateLoginResendCooldown(seconds: Int) {
        _loginState.value = _loginState.value.copy(resendCooldown = seconds)
    }

    fun clearErrors() {
        _signupState.value = _signupState.value.copy(error = null)
        _loginState.value = _loginState.value.copy(error = null)
    }
}
