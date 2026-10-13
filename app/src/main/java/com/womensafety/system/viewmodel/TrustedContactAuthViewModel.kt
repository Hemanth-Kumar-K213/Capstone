package com.womensafety.system.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.data.sms.TwoFactorClient
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
        if (_signupState.value.isLoading) return
        val state = _signupState.value

        if (!Validators.isValidName(state.name)) {
            _signupState.update { it.copy(error = "Please enter your full name.") }
            return
        }

        val normalized = Validators.normalizePhone(state.phone)
        if (!Validators.isValidPhone(normalized)) {
            _signupState.update { it.copy(error = "Please enter a valid phone number.") }
            return
        }

        _signupState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                Log.d(TAG, "Dispatching 2Factor SMS OTP for TC signup to $normalized...")
                val result = withTimeoutOrNull(20000L) {
                    TwoFactorClient.sendOTP(normalized, userName = state.name)
                }

                if (result == null) {
                    _signupState.update {
                        it.copy(error = "SMS dispatch timed out. Please check your connection and retry.")
                    }
                    return@launch
                }

                if (result.isSuccess) {
                    val sessionId = result.getOrNull()
                    Log.i(TAG, "2Factor OTP sent successfully! Session: $sessionId")
                    _signupState.update {
                        it.copy(
                            verificationId = sessionId ?: "2FACTOR_TC_SIGNUP",
                            otpSent = true,
                            resendCooldown = 60,
                            error = null
                        )
                    }
                } else {
                    val errMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to dispatch verification OTP via SMS."
                    Log.e(TAG, "2Factor OTP dispatch failed: $errMsg")
                    _signupState.update { it.copy(error = errMsg) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception during 2Factor sendSignupOtp", e)
                _signupState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to send verification OTP via SMS.")
                }
            } finally {
                _signupState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun verifySignupOtp() {
        if (_signupState.value.isLoading) return
        val state = _signupState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _signupState.update { it.copy(error = "Please enter a valid 6-digit OTP.") }
            return
        }

        val verificationId = state.verificationId
        if (verificationId.isNullOrBlank()) {
            _signupState.update { it.copy(error = "No active OTP session found. Please request OTP again.") }
            return
        }

        val normalized = Validators.normalizePhone(state.phone)
        _signupState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                Log.d(TAG, "Verifying 2Factor OTP for session $verificationId...")
                val verifyResult = withTimeoutOrNull(20000L) {
                    TwoFactorClient.verifyOTP(verificationId, state.otpCode.trim())
                }

                if (verifyResult == null) {
                    _signupState.update {
                        it.copy(error = "OTP verification timed out. Please check your connection and retry.")
                    }
                    return@launch
                }

                if (verifyResult.isSuccess && verifyResult.getOrNull() == true) {
                    Log.i(TAG, "2Factor OTP verified successfully! Completing Firebase Auth setup...")
                    completeSignupWith2Factor(state.name, normalized)
                } else {
                    val err = verifyResult.exceptionOrNull()?.localizedMessage ?: "Invalid OTP code. Please check and try again."
                    _signupState.update { it.copy(error = err) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception during verifySignupOtp", e)
                _signupState.update {
                    it.copy(error = e.localizedMessage ?: "OTP verification failed. Please try again.")
                }
            } finally {
                _signupState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun completeSignupWith2Factor(name: String, normalizedPhone: String) {
        val cleanPhone = normalizedPhone.replace("+", "")
        val tcEmail = "tc_${cleanPhone}@womensafety.internal"
        val tcPassword = "TC_${cleanPhone}_Trinetra2026!"

        var authResult = withTimeoutOrNull(15000L) {
            authRepository.loginWithEmailAndPassword(tcEmail, tcPassword)
        }
        if (authResult == null || authResult.isFailure) {
            authResult = withTimeoutOrNull(15000L) {
                authRepository.createUserWithEmailAndPassword(tcEmail, tcPassword)
            }
        }

        if (authResult != null && authResult.isSuccess) {
            val uid = authResult.getOrNull()!!
            val profileResult = withTimeoutOrNull(15000L) {
                authRepository.createTrustedContactProfile(
                    authUserId = uid,
                    name = name,
                    phone = normalizedPhone
                )
            }
            if (profileResult != null && profileResult.isSuccess) {
                _signupState.update {
                    it.copy(
                        accountCreated = true,
                        error = null
                    )
                }
            } else {
                _signupState.update {
                    it.copy(
                        error = profileResult?.exceptionOrNull()?.localizedMessage ?: "Failed to save profile in database."
                    )
                }
            }
        } else {
            _signupState.update {
                it.copy(
                    error = authResult?.exceptionOrNull()?.localizedMessage ?: "Authentication setup timed out or failed."
                )
            }
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
        if (_loginState.value.isLoading) return
        val state = _loginState.value
        val normalized = Validators.normalizePhone(state.phone)

        if (!Validators.isValidPhone(normalized)) {
            _loginState.update { it.copy(error = "Please enter a valid phone number.") }
            return
        }

        _loginState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                Log.d(TAG, "Dispatching 2Factor SMS OTP for TC login to $normalized...")
                val result = withTimeoutOrNull(20000L) {
                    TwoFactorClient.sendOTP(normalized, userName = "Trusted Contact")
                }

                if (result == null) {
                    _loginState.update {
                        it.copy(error = "SMS dispatch timed out. Please check your connection and retry.")
                    }
                    return@launch
                }

                if (result.isSuccess) {
                    val sessionId = result.getOrNull()
                    Log.i(TAG, "2Factor login OTP sent successfully! Session: $sessionId")
                    _loginState.update {
                        it.copy(
                            verificationId = sessionId ?: "2FACTOR_TC_LOGIN",
                            otpSent = true,
                            resendCooldown = 60,
                            error = null
                        )
                    }
                } else {
                    val errMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to dispatch verification OTP via SMS."
                    Log.e(TAG, "2Factor login OTP failed: $errMsg")
                    _loginState.update { it.copy(error = errMsg) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception during sendLoginOtp", e)
                _loginState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to dispatch verification OTP.")
                }
            } finally {
                _loginState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun verifyLoginOtp() {
        if (_loginState.value.isLoading) return
        val state = _loginState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _loginState.update { it.copy(error = "Please enter a valid 6-digit OTP.") }
            return
        }

        val verificationId = state.verificationId
        if (verificationId.isNullOrBlank()) {
            _loginState.update { it.copy(error = "No active OTP session found. Please request OTP again.") }
            return
        }

        val normalized = Validators.normalizePhone(state.phone)
        _loginState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                Log.d(TAG, "Verifying 2Factor login OTP for session $verificationId...")
                val verifyResult = withTimeoutOrNull(20000L) {
                    TwoFactorClient.verifyOTP(verificationId, state.otpCode.trim())
                }

                if (verifyResult == null) {
                    _loginState.update {
                        it.copy(error = "OTP verification timed out. Please check your connection and retry.")
                    }
                    return@launch
                }

                if (verifyResult.isSuccess && verifyResult.getOrNull() == true) {
                    Log.i(TAG, "2Factor login OTP verified! Establishing authenticated session...")
                    completeLoginWith2Factor(normalized)
                } else {
                    val err = verifyResult.exceptionOrNull()?.localizedMessage ?: "Invalid OTP code. Please check and try again."
                    _loginState.update { it.copy(error = err) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception during verifyLoginOtp", e)
                _loginState.update {
                    it.copy(error = e.localizedMessage ?: "OTP verification failed. Please try again.")
                }
            } finally {
                _loginState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun completeLoginWith2Factor(normalizedPhone: String) {
        val cleanPhone = normalizedPhone.replace("+", "")
        val tcEmail = "tc_${cleanPhone}@womensafety.internal"
        val tcPassword = "TC_${cleanPhone}_Trinetra2026!"

        var signInResult = withTimeoutOrNull(15000L) {
            authRepository.loginWithEmailAndPassword(tcEmail, tcPassword)
        }
        if (signInResult == null || signInResult.isFailure) {
            signInResult = withTimeoutOrNull(15000L) {
                authRepository.createUserWithEmailAndPassword(tcEmail, tcPassword)
            }
        }

        if (signInResult != null && signInResult.isSuccess) {
            val uid = signInResult.getOrNull()!!
            val profile = withTimeoutOrNull(10000L) { authRepository.getCurrentAccountType() }
            if (profile?.getOrNull() == null) {
                withTimeoutOrNull(15000L) {
                    authRepository.createTrustedContactProfile(
                        authUserId = uid,
                        name = "Trusted Contact",
                        phone = normalizedPhone
                    )
                }
            }
            _loginState.update {
                it.copy(
                    isLoggedIn = true,
                    error = null
                )
            }
        } else {
            _loginState.update {
                it.copy(
                    error = "Login authentication timed out or failed: ${signInResult?.exceptionOrNull()?.localizedMessage}"
                )
            }
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
