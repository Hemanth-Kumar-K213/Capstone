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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    fun requestPhoneOTP(phoneNumber: String, userName: String) {
        val normalizedPhone = Validators.normalizePhone(phoneNumber)
        _signupState.value = _signupState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val payload = hashMapOf(
                    "phoneNumber" to normalizedPhone,
                    "userName" to userName.ifBlank { "User" }
                )

                FirebaseFunctions.getInstance()
                    .getHttpsCallable("sendOTPFunction")
                    .call(payload)
                    .await()

                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    verificationId = "2FACTOR_OTP",
                    resendCooldown = 60
                )
            } catch (e: Exception) {
                val errorMsg = if (e is com.google.firebase.functions.FirebaseFunctionsException &&
                    e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND) {
                    "Cloud Function 'sendOTPFunction' not found. Please deploy Cloud Functions to Firebase project 'women-safety-system-65893' via 'firebase deploy --only functions'."
                } else {
                    e.message ?: "Failed to dispatch verification OTP via 2Factor SMS."
                }
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = errorMsg
                )
            }
        }
    }

    fun onPhoneOTPVerified() {
        _signupState.value = _signupState.value.copy(
            phoneVerified = true,
            currentStep = 3,
            error = null
        )
    }

    fun updateOtpCode(code: String) {
        val digitsOnly = code.filter { it.isDigit() }
        if (digitsOnly.length <= 6) {
            _signupState.value = _signupState.value.copy(otpCode = digitsOnly, error = null)
        }
    }

    fun verifyOtp() {
        val state = _signupState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _signupState.value = state.copy(error = "Please enter a valid 6-digit OTP.")
            return
        }

        val normalizedPhone = Validators.normalizePhone(state.phone)
        _signupState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val payload = hashMapOf(
                    "phoneNumber" to normalizedPhone,
                    "otp" to state.otpCode.trim()
                )

                val result = FirebaseFunctions.getInstance()
                    .getHttpsCallable("verifyOTPFunction")
                    .call(payload)
                    .await()

                val data = result.getData() as? Map<*, *>
                val isSuccess = (data?.get("success") as? Boolean) == true

                if (isSuccess) {
                    _signupState.value = _signupState.value.copy(
                        isLoading = false,
                        phoneVerified = true,
                        currentStep = 3,
                        error = null
                    )
                } else {
                    _signupState.value = _signupState.value.copy(
                        isLoading = false,
                        error = "Invalid OTP code. Please check and try again."
                    )
                }
            } catch (e: Exception) {
                val errorMsg = if (e is com.google.firebase.functions.FirebaseFunctionsException &&
                    e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND) {
                    "Cloud Function 'verifyOTPFunction' not found. Please deploy Cloud Functions to Firebase project 'women-safety-system-65893'."
                } else {
                    e.message ?: "OTP verification failed. Please try again."
                }
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = errorMsg
                )
            }
        }
    }

    fun updateResendCooldown(seconds: Int) {
        _signupState.value = _signupState.value.copy(resendCooldown = seconds)
    }

    // ─── Step 3: Email Verification ──────────────────────────────

    fun sendEmailVerification() {
        val state = _signupState.value
        _signupState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            // First create a temporary email/password account to send verification
            val createResult = authRepository.createUserWithEmailAndPassword(
                state.email,
                // Use a temporary password — will be updated in Step 5
                "TempPass_${System.currentTimeMillis()}!"
            )
            if (createResult.isFailure) {
                // Account might already exist — try to check
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = createResult.exceptionOrNull()?.message ?: "Failed to initiate email verification."
                )
                return@launch
            }

            val sendResult = authRepository.sendEmailVerification()
            _signupState.value = _signupState.value.copy(
                isLoading = false,
                verificationEmailSent = sendResult.isSuccess,
                authUserId = createResult.getOrNull(),
                error = if (sendResult.isFailure) sendResult.exceptionOrNull()?.message else null
            )
        }
    }

    fun checkEmailVerificationStatus() {
        _signupState.value = _signupState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = authRepository.checkEmailVerified()
            if (result.isSuccess && result.getOrNull() == true) {
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    emailVerified = true,
                    currentStep = 4
                )
            } else {
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = "Email not yet verified. Please check your inbox and click the verification link."
                )
            }
        }
    }

    // ─── Step 4: Duplicate Check ─────────────────────────────────

    fun performDuplicateCheck() {
        val state = _signupState.value
        _signupState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = authRepository.checkDuplicateAccount(
                Validators.normalizePhone(state.phone),
                state.email
            )
            if (result.isSuccess) {
                val checkResult = result.getOrNull()!!
                when (checkResult) {
                    DuplicateCheckResult.NO_DUPLICATE -> {
                        _signupState.value = _signupState.value.copy(
                            isLoading = false,
                            duplicateCheckDone = true,
                            duplicateCheckResult = checkResult,
                            currentStep = 5
                        )
                    }
                    DuplicateCheckResult.PHONE_EXISTS -> {
                        _signupState.value = _signupState.value.copy(
                            isLoading = false,
                            duplicateCheckDone = true,
                            duplicateCheckResult = checkResult,
                            error = "An account already exists with this phone number."
                        )
                    }
                    DuplicateCheckResult.EMAIL_EXISTS -> {
                        _signupState.value = _signupState.value.copy(
                            isLoading = false,
                            duplicateCheckDone = true,
                            duplicateCheckResult = checkResult,
                            error = "An account already exists with this email."
                        )
                    }
                }
            } else {
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to check for existing accounts."
                )
            }
        }
    }

    // ─── Step 5: Create Password ─────────────────────────────────

    fun updatePassword(password: String) {
        _signupState.value = _signupState.value.copy(password = password, error = null)
    }

    fun updateConfirmPassword(confirmPassword: String) {
        _signupState.value = _signupState.value.copy(confirmPassword = confirmPassword, error = null)
    }

    fun createAccount() {
        val state = _signupState.value

        if (!Validators.isValidPassword(state.password)) {
            _signupState.value = state.copy(
                error = "Password must be at least 8 characters with uppercase, lowercase, number, and special character."
            )
            return
        }
        if (!Validators.doPasswordsMatch(state.password, state.confirmPassword)) {
            _signupState.value = state.copy(error = "Passwords do not match.")
            return
        }

        _signupState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            // The Firebase Auth account was already created in Step 3 with a temp password.
            // Now we need to update it. Since Firebase doesn't have a direct "update password"
            // without re-auth in this flow, we'll delete and recreate with proper password,
            // or use the password reset approach. For simplicity in signup flow,
            // we send a password reset email and let user set their password.
            //
            // Alternative approach: The account was created in Step 3. Now create the Firestore profile.
            // The user can change their password later via password reset.
            // For a better UX, we'll try to update the password directly.

            val uid = state.authUserId
            if (uid == null) {
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = "Account creation error. Please restart signup."
                )
                return@launch
            }

            // Create user profile in Firestore
            val profileResult = authRepository.createUserProfile(
                authUserId = uid,
                name = state.name,
                email = state.email,
                phone = Validators.normalizePhone(state.phone)
            )

            if (profileResult.isSuccess) {
                // Generate dev UID for this user (Spark plan fallback)
                // This writes to devUidMappings and updates the user profile.
                // On Blaze plan, Cloud Functions would handle this instead.
                uidRepository.generateDevUid(uid)

                // Send password reset to let user set their final password
                authRepository.sendPasswordResetEmail(state.email)

                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    accountCreated = true,
                    currentStep = 6
                )

                // Update session state
                _sessionState.value = SessionState(
                    isLoggedIn = true,
                    accountType = "USER",
                    isChecking = false
                )
            } else {
                _signupState.value = _signupState.value.copy(
                    isLoading = false,
                    error = profileResult.exceptionOrNull()?.message ?: "Failed to create account."
                )
            }
        }
    }

    // ─── Login ───────────────────────────────────────────────────

    fun updateLoginEmail(email: String) {
        _loginState.value = _loginState.value.copy(email = email, error = null)
    }

    fun updateLoginPassword(password: String) {
        _loginState.value = _loginState.value.copy(password = password, error = null)
    }

    fun loginWithEmailAndPassword() {
        val state = _loginState.value

        if (!Validators.isValidEmail(state.email)) {
            _loginState.value = state.copy(error = "Please enter a valid email address.")
            return
        }
        if (state.password.isBlank()) {
            _loginState.value = state.copy(error = "Please enter your password.")
            return
        }

        _loginState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = authRepository.loginWithEmailAndPassword(state.email, state.password)
            if (result.isSuccess) {
                val uid = result.getOrNull()
                val profile = if (uid != null) userRepository.getUserProfile(uid).getOrNull() else null
                val setupDone = profile?.setupCompleted ?: false

                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    authUserId = uid
                )
                _sessionState.value = SessionState(
                    isLoggedIn = true,
                    accountType = "USER",
                    setupCompleted = setupDone,
                    isChecking = false
                )
            } else {
                _loginState.value = _loginState.value.copy(
                    isLoading = false,
                    error = "Authentication failed. Please check your email and password."
                )
            }
        }
    }

    fun toggleEmailLinkLogin() {
        _loginState.value = _loginState.value.copy(
            showEmailLinkLogin = !_loginState.value.showEmailLinkLogin,
            error = null
        )
    }

    fun sendEmailLoginLink() {
        val state = _loginState.value
        if (!Validators.isValidEmail(state.email)) {
            _loginState.value = state.copy(error = "Please enter your primary Gmail.")
            return
        }

        _loginState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = authRepository.sendEmailSignInLink(state.email)
            _loginState.value = _loginState.value.copy(
                isLoading = false,
                emailLinkSent = result.isSuccess,
                error = if (result.isFailure) "Failed to send sign-in link. Please try again." else null
            )
        }
    }

    fun forgotPassword() {
        val state = _loginState.value
        if (!Validators.isValidEmail(state.email)) {
            _loginState.value = state.copy(error = "Please enter your Gmail to reset password.")
            return
        }

        viewModelScope.launch {
            _loginState.value = _loginState.value.copy(isLoading = true, error = null)
            val result = authRepository.sendPasswordResetEmail(state.email)
            _loginState.value = _loginState.value.copy(
                isLoading = false,
                error = if (result.isFailure) "Failed to send reset email." else null,
                successMessage = if (result.isSuccess) "Password reset link sent to ${state.email}" else null
            )
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
