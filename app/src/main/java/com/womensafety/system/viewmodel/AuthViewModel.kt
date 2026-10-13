package com.womensafety.system.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.data.repository.DuplicateCheckResult
import com.womensafety.system.data.repository.UidRepository
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages the User signup flow (6 steps) and login.
 * Accesses Firebase ONLY through AuthRepository.
 */
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val uidRepository: UidRepository = UidRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    // ─── Signup State ────────────────────────────────────────────

    data class SignupState(
        val currentStep: Int = 1,
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val password: String = "",
        val confirmPassword: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val successMessage: String? = null,

        // Phone OTP
        val verificationId: String? = null,
        val resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        val otpCode: String = "",
        val phoneVerified: Boolean = false,
        val resendCooldown: Int = 0,
        val autoVerified: Boolean = false,

        // Email verification
        val verificationEmailSent: Boolean = false,
        val emailVerified: Boolean = false,

        // Duplicate check
        val duplicateCheckDone: Boolean = false,
        val duplicateCheckResult: DuplicateCheckResult? = null,

        // Account creation
        val accountCreated: Boolean = false,
        val authUserId: String? = null
    )

    private val _signupState = MutableStateFlow(SignupState())
    val signupState: StateFlow<SignupState> = _signupState.asStateFlow()

    // ─── Login State ─────────────────────────────────────────────

    data class LoginState(
        val email: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val isLoggedIn: Boolean = false,
        val authUserId: String? = null,
        val showEmailLinkLogin: Boolean = false,
        val emailLinkSent: Boolean = false,
        val successMessage: String? = null
    )

    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    // ─── Session State ───────────────────────────────────────────

    data class SessionState(
        val isLoggedIn: Boolean = false,
        val accountType: String? = null,
        val setupCompleted: Boolean = false,
        val isChecking: Boolean = true
    )

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        viewModelScope.launch {
            val loggedIn = authRepository.isLoggedIn
            if (loggedIn) {
                val typeResult = authRepository.getCurrentAccountType()
                val accountType = typeResult.getOrNull()?.name
                val uid = authRepository.currentUserId
                var setupDone = true
                if (accountType == "USER" && uid != null) {
                    val profile = userRepository.getUserProfile(uid).getOrNull()
                    setupDone = profile?.setupCompleted ?: false
                }
                _sessionState.value = SessionState(
                    isLoggedIn = true,
                    accountType = accountType,
                    setupCompleted = setupDone,
                    isChecking = false
                )
            } else {
                _sessionState.value = SessionState(isChecking = false)
            }
        }
    }

    // ─── Step 1: Personal Details ────────────────────────────────

    fun updateName(name: String) {
        _signupState.value = _signupState.value.copy(name = name, error = null)
    }

    fun updatePhone(phone: String) {
        _signupState.value = _signupState.value.copy(phone = phone, error = null)
    }

    fun updateEmail(email: String) {
        _signupState.value = _signupState.value.copy(email = email, error = null)
    }

    fun validateStep1(): Boolean {
        val state = _signupState.value
        if (!Validators.isValidName(state.name)) {
            _signupState.value = state.copy(error = "Please enter your full name.")
            return false
        }
        if (!Validators.isValidPhone(Validators.normalizePhone(state.phone))) {
            _signupState.value = state.copy(error = "Please enter a valid phone number (e.g., +91XXXXXXXXXX).")
            return false
        }
        if (!Validators.isValidEmail(state.email)) {
            _signupState.value = state.copy(error = "Please enter a valid email address.")
            return false
        }
        _signupState.value = state.copy(error = null, currentStep = 2)
        return true
    }

    // ─── Step 2: Phone OTP (Twilio SMS Integration) ──────────────

    fun sendPhoneOtp(activity: Activity? = null) {
        val state = _signupState.value
        requestPhoneOTP(state.phone, state.name)
    }

    companion object {
        private var signupSessionId: String? = null
        private var signupSessionPhone: String? = null
        private var signupSessionExpiry: Long = 0L
        private var signupLastSent: Long = 0L
    }

    fun requestPhoneOTP(phoneNumber: String, userName: String) {
        if (_signupState.value.isLoading) return
        val normalizedPhone = Validators.normalizePhone(phoneNumber)
        val now = System.currentTimeMillis()

        if (signupSessionPhone == normalizedPhone && (now - signupLastSent) < 60000) {
            val remainingSecs = ((60000 - (now - signupLastSent)) / 1000).toInt()
            _signupState.update {
                it.copy(error = "Please wait $remainingSecs second(s) before requesting another OTP.")
            }
            return
        }

        _signupState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(20000L) {
                    com.womensafety.system.data.sms.TwoFactorClient.sendOTP(normalizedPhone, userName = userName)
                }

                if (result == null) {
                    _signupState.update {
                        it.copy(error = "SMS OTP request timed out. Please check your connection and retry.")
                    }
                    return@launch
                }

                if (result.isSuccess) {
                    val sessionId = result.getOrNull()
                    signupSessionId = sessionId
                    signupSessionPhone = normalizedPhone
                    signupSessionExpiry = System.currentTimeMillis() + 10 * 60 * 1000
                    signupLastSent = System.currentTimeMillis()

                    _signupState.update {
                        it.copy(
                            verificationId = sessionId ?: "2FACTOR_OTP",
                            resendCooldown = 60,
                            error = null
                        )
                    }
                } else {
                    val errMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to dispatch SMS OTP via 2Factor."
                    _signupState.update { it.copy(error = errMsg) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _signupState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to dispatch verification OTP via 2Factor SMS.")
                }
            } finally {
                _signupState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onPhoneOTPVerified() {
        _signupState.update {
            it.copy(
                phoneVerified = true,
                currentStep = 3,
                error = null
            )
        }
    }

    fun updateOtpCode(code: String) {
        val digitsOnly = code.filter { it.isDigit() }
        if (digitsOnly.length <= 6) {
            _signupState.update { it.copy(otpCode = digitsOnly, error = null) }
        }
    }

    fun verifyOtp() {
        if (_signupState.value.isLoading) return
        val state = _signupState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _signupState.update { it.copy(error = "Please enter a valid 6-digit OTP.") }
            return
        }

        val normalizedPhone = Validators.normalizePhone(state.phone)
        val sessionId = signupSessionId

        if (sessionId == null || signupSessionPhone != normalizedPhone) {
            _signupState.update { it.copy(error = "No active OTP request found. Please request an OTP first.") }
            return
        }

        if (System.currentTimeMillis() > signupSessionExpiry) {
            _signupState.update { it.copy(error = "OTP expired. Please request a new code.") }
            return
        }

        _signupState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val verifyResult = withTimeoutOrNull(20000L) {
                    com.womensafety.system.data.sms.TwoFactorClient.verifyOTP(
                        sessionId,
                        state.otpCode.trim()
                    )
                }

                if (verifyResult == null) {
                    _signupState.update {
                        it.copy(error = "OTP verification timed out. Please check your connection and retry.")
                    }
                    return@launch
                }

                if (verifyResult.isSuccess && verifyResult.getOrNull() == true) {
                    signupSessionId = null
                    _signupState.update {
                        it.copy(
                            phoneVerified = true,
                            currentStep = 3,
                            error = null
                        )
                    }
                } else {
                    _signupState.update {
                        it.copy(error = "Invalid OTP code. Please check and try again.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _signupState.update {
                    it.copy(error = e.localizedMessage ?: "OTP verification failed. Please try again.")
                }
            } finally {
                _signupState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateResendCooldown(seconds: Int) {
        _signupState.value = _signupState.value.copy(resendCooldown = seconds)
    }

    // ─── Step 3: Email Verification ──────────────────────────────

    fun sendEmailVerification() {
        if (_signupState.value.isLoading) return
        val state = _signupState.value
        _signupState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // If currentUser already exists with this email, reuse the session
                val currentEmail = authRepository.currentUserEmail
                val uid = if (currentEmail != null && currentEmail.equals(state.email, ignoreCase = true)) {
                    authRepository.currentUserId
                } else {
                    val createResult = withTimeoutOrNull(15000L) {
                        authRepository.createUserWithEmailAndPassword(
                            state.email,
                            // Use a temporary password — will be updated in Step 5
                            "TempPass_${System.currentTimeMillis()}!"
                        )
                    }
                    if (createResult == null) {
                        _signupState.update {
                            it.copy(error = "Request timed out. Please check your connection and retry.")
                        }
                        return@launch
                    }
                    if (createResult.isFailure) {
                        _signupState.update {
                            it.copy(
                                error = createResult.exceptionOrNull()?.localizedMessage ?: "Failed to initiate email verification."
                            )
                        }
                        return@launch
                    }
                    createResult.getOrNull()
                }

                val sendResult = withTimeoutOrNull(15000L) {
                    authRepository.sendEmailVerification()
                }
                if (sendResult == null) {
                    _signupState.update {
                        it.copy(error = "Email verification request timed out. Please retry.")
                    }
                    return@launch
                }

                _signupState.update {
                    it.copy(
                        verificationEmailSent = sendResult.isSuccess,
                        authUserId = uid,
                        error = if (sendResult.isFailure) sendResult.exceptionOrNull()?.localizedMessage else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _signupState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to send email verification.")
                }
            } finally {
                _signupState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun checkEmailVerificationStatus() {
        if (_signupState.value.isLoading) return
        _signupState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(15000L) {
                    authRepository.checkEmailVerified()
                }
                if (result == null) {
                    _signupState.update {
                        it.copy(error = "Verification check timed out. Please retry.")
                    }
                    return@launch
                }
                if (result.isSuccess && result.getOrNull() == true) {
                    _signupState.update {
                        it.copy(
                            emailVerified = true,
                            currentStep = 4,
                            error = null
                        )
                    }
                } else {
                    _signupState.update {
                        it.copy(
                            error = "Email not yet verified. Please check your inbox and click the verification link."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _signupState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to verify email status.")
                }
            } finally {
                _signupState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ─── Step 4: Duplicate Check ─────────────────────────────────

    fun performDuplicateCheck() {
        if (_signupState.value.isLoading) return
        val state = _signupState.value
        _signupState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(15000L) {
                    authRepository.checkDuplicateAccount(
                        Validators.normalizePhone(state.phone),
                        state.email
                    )
                }
                if (result == null) {
                    _signupState.update {
                        it.copy(error = "Account verification check timed out. Please retry.")
                    }
                    return@launch
                }
                if (result.isSuccess) {
                    val checkResult = result.getOrNull()!!
                    when (checkResult) {
                        DuplicateCheckResult.NO_DUPLICATE -> {
                            _signupState.update {
                                it.copy(
                                    duplicateCheckDone = true,
                                    duplicateCheckResult = checkResult,
                                    currentStep = 5,
                                    error = null
                                )
                            }
                        }
                        DuplicateCheckResult.PHONE_EXISTS -> {
                            _signupState.update {
                                it.copy(
                                    duplicateCheckDone = true,
                                    duplicateCheckResult = checkResult,
                                    error = "An account already exists with this phone number."
                                )
                            }
                        }
                        DuplicateCheckResult.EMAIL_EXISTS -> {
                            _signupState.update {
                                it.copy(
                                    duplicateCheckDone = true,
                                    duplicateCheckResult = checkResult,
                                    error = "An account already exists with this email."
                                )
                            }
                        }
                    }
                } else {
                    _signupState.update {
                        it.copy(
                            error = result.exceptionOrNull()?.localizedMessage ?: "Failed to check for existing accounts."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _signupState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to perform security audit.")
                }
            } finally {
                _signupState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ─── Step 5: Create Password ─────────────────────────────────

    fun updatePassword(password: String) {
        _signupState.update { it.copy(password = password, error = null) }
    }

    fun updateConfirmPassword(confirmPassword: String) {
        _signupState.update { it.copy(confirmPassword = confirmPassword, error = null) }
    }

    fun createAccount() {
        if (_signupState.value.isLoading) return
        val state = _signupState.value

        if (!Validators.isValidPassword(state.password)) {
            _signupState.update {
                it.copy(
                    error = "Password must be at least 8 characters with uppercase, lowercase, number, and special character."
                )
            }
            return
        }
        if (!Validators.doPasswordsMatch(state.password, state.confirmPassword)) {
            _signupState.update { it.copy(error = "Passwords do not match.") }
            return
        }

        _signupState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // Ensure we have a valid authenticated user ID
                val currentUser = authRepository.currentUser
                val uid = state.authUserId ?: currentUser?.uid
                if (uid == null || currentUser == null) {
                    android.util.Log.e("AuthViewModel", "Cannot create account: uid=$uid, currentUser=$currentUser")
                    _signupState.update {
                        it.copy(
                            error = "Account session missing. Please restart signup from Step 1."
                        )
                    }
                    return@launch
                }

                // Force refresh token to ensure Firestore has valid auth credentials
                try {
                    currentUser.getIdToken(true).await()
                } catch (e: Exception) {
                    android.util.Log.w("AuthViewModel", "Pre-profile token refresh warning: ${e.message}")
                }

                val finalEmail = state.email.ifBlank { currentUser.email ?: "" }
                val finalName = state.name.ifBlank { currentUser.displayName?.ifBlank { "User" } ?: "User" }
                val finalPhone = if (state.phone.isNotBlank()) Validators.normalizePhone(state.phone) else (currentUser.phoneNumber ?: "")

                // 1. Create/merge user profile in Firestore FIRST while the session token is active and verified
                val profileResult = withTimeoutOrNull(15000L) {
                    authRepository.createUserProfile(
                        authUserId = uid,
                        name = finalName,
                        email = finalEmail,
                        phone = finalPhone
                    )
                }
                if (profileResult == null) {
                    _signupState.update {
                        it.copy(
                            error = "Profile creation timed out. Please check your network connection and retry."
                        )
                    }
                    return@launch
                }
                if (profileResult.isFailure) {
                    val ex = profileResult.exceptionOrNull()
                    android.util.Log.e("AuthViewModel", "Profile creation failed", ex)
                    val rawErr = ex?.localizedMessage ?: ex?.message ?: "Failed to create user profile."
                    _signupState.update {
                        it.copy(error = "Profile creation error: $rawErr")
                    }
                    return@launch
                }

                // 2. Generate dev UID mapping for local/demo functionality
                try {
                    withTimeoutOrNull(10000L) {
                        uidRepository.generateDevUid(uid, finalName)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AuthViewModel", "Dev UID generation warning: ${e.message}")
                }

                // 3. Update the Firebase Auth password from temporary password to user's desired password
                val passwordUpdateResult = withTimeoutOrNull(15000L) {
                    authRepository.updatePassword(state.password)
                }
                if (passwordUpdateResult == null) {
                    _signupState.update {
                        it.copy(
                            error = "Password update timed out. Please check your network connection and retry."
                        )
                    }
                    return@launch
                }
                if (passwordUpdateResult.isFailure) {
                    val passEx = passwordUpdateResult.exceptionOrNull()
                    android.util.Log.e("AuthViewModel", "Password update failed", passEx)
                    val passErr = passEx?.localizedMessage ?: passEx?.message ?: "Failed to set account password."
                    _signupState.update { it.copy(error = passErr) }
                    return@launch
                }

                // 4. Finalize state, safely clear sensitive credentials from UI memory
                _signupState.update {
                    it.copy(
                        password = "",
                        confirmPassword = "",
                        accountCreated = true,
                        authUserId = uid,
                        currentStep = 6,
                        error = null
                    )
                }

                // 5. Update session state
                _sessionState.value = SessionState(
                    isLoggedIn = true,
                    accountType = "USER",
                    isChecking = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "createAccount unexpected error", e)
                _signupState.update {
                    it.copy(
                        error = e.localizedMessage ?: "Failed to finalize account creation. Please try again."
                    )
                }
            } finally {
                _signupState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ─── Login ───────────────────────────────────────────────────

    fun updateLoginEmail(email: String) {
        _loginState.update { it.copy(email = email, error = null) }
    }

    fun updateLoginPassword(password: String) {
        _loginState.update { it.copy(password = password, error = null) }
    }

    fun loginWithEmailAndPassword() {
        if (_loginState.value.isLoading) return
        val state = _loginState.value
        val cleanEmail = state.email.trim()
        val cleanPassword = state.password.trim()

        if (!Validators.isValidEmail(cleanEmail)) {
            _loginState.update { it.copy(error = "Please enter a valid email address.") }
            return
        }
        if (cleanPassword.isBlank()) {
            _loginState.update { it.copy(error = "Please enter your password.") }
            return
        }

        _loginState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(15000L) {
                    authRepository.loginWithEmailAndPassword(cleanEmail, cleanPassword)
                }
                if (result == null) {
                    _loginState.update {
                        it.copy(error = "Login timed out. Please check your network connection and retry.")
                    }
                    return@launch
                }
                if (result.isSuccess) {
                    val uid = result.getOrNull()
                    val profile = if (uid != null) {
                        withTimeoutOrNull(10000L) { userRepository.getUserProfile(uid).getOrNull() }
                    } else null
                    val setupDone = profile?.setupCompleted ?: false

                    _loginState.update {
                        it.copy(
                            isLoggedIn = true,
                            authUserId = uid,
                            password = "", // Clear credential from memory
                            error = null
                        )
                    }
                    _sessionState.value = SessionState(
                        isLoggedIn = true,
                        accountType = "USER",
                        setupCompleted = setupDone,
                        isChecking = false
                    )
                } else {
                    val rawMsg = result.exceptionOrNull()?.localizedMessage ?: ""
                    val friendlyError = when {
                        rawMsg.contains("password", ignoreCase = true) -> "Incorrect password. Please try again or use Forgot Password."
                        rawMsg.contains("no user record", ignoreCase = true) || rawMsg.contains("user-not-found", ignoreCase = true) -> "No account found with this email. Please check your email or sign up."
                        rawMsg.contains("network", ignoreCase = true) -> "Network error. Please check your internet connection."
                        rawMsg.isNotBlank() -> rawMsg
                        else -> "Authentication failed. Please check your email and password."
                    }
                    _loginState.update { it.copy(error = friendlyError) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _loginState.update {
                    it.copy(error = e.localizedMessage ?: "Authentication failed.")
                }
            } finally {
                _loginState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleEmailLinkLogin() {
        _loginState.update {
            it.copy(
                showEmailLinkLogin = !it.showEmailLinkLogin,
                error = null
            )
        }
    }

    fun sendEmailLoginLink() {
        if (_loginState.value.isLoading) return
        val state = _loginState.value
        if (!Validators.isValidEmail(state.email)) {
            _loginState.update { it.copy(error = "Please enter your primary Gmail.") }
            return
        }

        _loginState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(15000L) {
                    authRepository.sendEmailSignInLink(state.email)
                }
                if (result == null) {
                    _loginState.update {
                        it.copy(error = "Request timed out. Please check your connection and retry.")
                    }
                    return@launch
                }
                _loginState.update {
                    it.copy(
                        emailLinkSent = result.isSuccess,
                        error = if (result.isFailure) "Failed to send sign-in link. Please try again." else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _loginState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to send sign-in link.")
                }
            } finally {
                _loginState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun forgotPassword() {
        if (_loginState.value.isLoading) return
        val state = _loginState.value
        if (!Validators.isValidEmail(state.email)) {
            _loginState.update { it.copy(error = "Please enter your Gmail to reset password.") }
            return
        }

        _loginState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(15000L) {
                    authRepository.sendPasswordResetEmail(state.email)
                }
                if (result == null) {
                    _loginState.update {
                        it.copy(error = "Password reset request timed out. Please retry.")
                    }
                    return@launch
                }
                _loginState.update {
                    it.copy(
                        error = if (result.isFailure) "Failed to send reset email." else null,
                        successMessage = if (result.isSuccess) "Password reset link sent to ${state.email}" else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _loginState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to send reset email.")
                }
            } finally {
                _loginState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ─── Session ─────────────────────────────────────────────────

    fun signOut() {
        authRepository.signOut()
        _sessionState.value = SessionState(isChecking = false)
        _loginState.value = LoginState()
        _signupState.value = SignupState()
    }

    fun clearError() {
        _signupState.value = _signupState.value.copy(error = null)
        _loginState.value = _loginState.value.copy(error = null)
    }
}
