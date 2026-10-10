package com.womensafety.system.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Trusted Contact signup and login via phone OTP.
 * Accesses Firebase ONLY through AuthRepository.
 */
class TrustedContactAuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    // ─── Signup State ────────────────────────────────────────────

    data class TCSignupState(
        val name: String = "",
        val phone: String = "",
        val verificationId: String? = null,
        val resendToken: PhoneAuthProvider.ForceResendingToken? = null,
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
        val resendToken: PhoneAuthProvider.ForceResendingToken? = null,
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
        if (code.length <= 6) {
            _signupState.value = _signupState.value.copy(otpCode = code, error = null)
        }
    }

    fun sendSignupOtp(activity: Activity) {
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

        // NOTE: Phone duplicate checking is handled by Firebase Phone Auth flow.
        // If the phone is already registered, signInWithCredential will sign in
        // to the existing account rather than creating a new one.
        viewModelScope.launch {

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    completeSignup(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    _signupState.value = _signupState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Phone verification failed."
                    )
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    _signupState.value = _signupState.value.copy(
                        isLoading = false,
                        verificationId = verificationId,
                        resendToken = token,
                        otpSent = true,
                        resendCooldown = 60
                    )
                }
            }

            authRepository.sendPhoneOtp(normalized, activity, callbacks, state.resendToken)
        }
    }

    fun verifySignupOtp() {
        val state = _signupState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _signupState.value = state.copy(error = "Please enter a valid 6-digit OTP.")
            return
        }

        val verificationId = state.verificationId ?: return
        val credential = authRepository.getPhoneCredential(verificationId, state.otpCode)
        completeSignup(credential)
    }

    private fun completeSignup(credential: PhoneAuthCredential) {
        _signupState.value = _signupState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val signInResult = authRepository.signInWithPhoneCredential(credential)
            if (signInResult.isFailure) {
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = signInResult.exceptionOrNull()?.message ?: "OTP verification failed."
                )
                return@launch
            }

            val uid = signInResult.getOrNull()!!
            val state = _signupState.value
            val normalized = Validators.normalizePhone(state.phone)

            val profileResult = authRepository.createTrustedContactProfile(
                authUserId = uid,
                name = state.name,
                phone = normalized
            )

            if (profileResult.isSuccess) {
                authRepository.syncFCMToken(com.womensafety.system.data.model.AccountType.TRUSTED_CONTACT)
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    accountCreated = true
                )
            } else {
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = profileResult.exceptionOrNull()?.message ?: "Failed to create account."
                )
            }
        }
    }

    // ─── Login Methods ───────────────────────────────────────────

    fun updateLoginPhone(phone: String) {
        _loginState.value = _loginState.value.copy(phone = phone, error = null)
    }

    fun updateLoginOtp(code: String) {
        if (code.length <= 6) {
            _loginState.value = _loginState.value.copy(otpCode = code, error = null)
        }
    }

    fun sendLoginOtp(activity: Activity) {
        val state = _loginState.value
        val normalized = Validators.normalizePhone(state.phone)

        if (!Validators.isValidPhone(normalized)) {
            _loginState.value = state.copy(error = "Please enter a valid phone number.")
            return
        }

        _loginState.value = state.copy(isLoading = true, error = null)

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                completeLogin(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Phone verification failed."
                )
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    verificationId = verificationId,
                    resendToken = token,
                    otpSent = true,
                    resendCooldown = 60
                )
            }
        }

        authRepository.sendPhoneOtp(normalized, activity, callbacks, state.resendToken)
    }

    fun verifyLoginOtp() {
        val state = _loginState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _loginState.value = state.copy(error = "Please enter a valid 6-digit OTP.")
            return
        }

        val verificationId = state.verificationId ?: return
        val credential = authRepository.getPhoneCredential(verificationId, state.otpCode)
        completeLogin(credential)
    }

    private fun completeLogin(credential: PhoneAuthCredential) {
        _loginState.value = _loginState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = authRepository.signInWithPhoneCredential(credential)
            if (result.isSuccess) {
                authRepository.syncFCMToken(com.womensafety.system.data.model.AccountType.TRUSTED_CONTACT)
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    isLoggedIn = true
                )
            } else {
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    error = "Authentication failed. Please try again."
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
