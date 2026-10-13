package com.womensafety.system.data.repository

import android.app.Activity
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.AccountType
import com.womensafety.system.data.model.TrustedContactProfile
import com.womensafety.system.data.model.UserProfile

/**
 * Orchestrates all authentication flows.
 * Accesses Firebase exclusively through [FirebaseAuthManager] and [FirestoreManager].
 */
class AuthRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {

    val currentUserId: String?
        get() = authManager.currentUserId

    val currentUser: com.google.firebase.auth.FirebaseUser?
        get() = authManager.currentUser

    val currentUserEmail: String?
        get() = authManager.currentUser?.email

    val isLoggedIn: Boolean
        get() = authManager.isLoggedIn

    // ─── User Signup ─────────────────────────────────────────────

    /**
     * Step 2: Initiate phone OTP verification.
     */
    fun sendPhoneOtp(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null
    ) {
        authManager.verifyPhoneNumber(phoneNumber, activity, callbacks, resendToken = resendToken)
    }

    /**
     * Verify phone OTP code against verification ID.
     */
    fun getPhoneCredential(verificationId: String, code: String): PhoneAuthCredential {
        return authManager.getPhoneCredential(verificationId, code)
    }

    /**
     * Sign in with phone credential (for TC auth or phone verification).
     */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<String> {
        return authManager.signInWithPhoneCredential(credential)
    }

    /**
     * Link phone credential to current user (during User signup).
     */
    suspend fun linkPhoneCredential(credential: PhoneAuthCredential): Result<Unit> {
        return authManager.linkPhoneCredential(credential)
    }

    /**
     * Step 3: Send email verification to the currently signed-in user.
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        return authManager.sendEmailVerification()
    }

    /**
     * Check if email has been verified by reloading user.
     */
    suspend fun checkEmailVerified(): Result<Boolean> {
        return authManager.reloadUser()
    }

    /**
     * Step 4: Check for duplicate accounts.
     *
     * IMPORTANT: Firestore rules deny collection-wide list/query operations on
     * users and trustedContacts collections. Therefore, duplicate checking uses
     * Firebase Authentication's fetchSignInMethodsForEmail instead of Firestore queries.
     *
     * - Email duplicate: Checked via Firebase Auth (fetchSignInMethodsForEmail).
     * - Phone duplicate: Handled naturally by Firebase Phone Auth flow —
     *   if a phone number is already linked to an account, the auth flow handles it.
     *   At this point in the signup flow (Step 4), the phone has already been verified
     *   in Step 2, so if it was already taken, Firebase Auth would have signed in
     *   to that account instead.
     */
    suspend fun checkDuplicateAccount(phone: String, email: String): Result<DuplicateCheckResult> {
        return try {
            // Check if this email belongs to the currently authenticated session created during Step 3
            val currentEmail = authManager.currentUser?.email
            if (currentEmail != null && currentEmail.equals(email, ignoreCase = true)) {
                return Result.success(DuplicateCheckResult.NO_DUPLICATE)
            }

            // Check email via Firebase Auth
            val signInMethods = authManager.fetchSignInMethodsForEmail(email)
            if (signInMethods.isFailure) {
                return Result.failure(signInMethods.exceptionOrNull()!!)
            }

            val methods = signInMethods.getOrNull() ?: emptyList()
            if (methods.isNotEmpty()) {
                // Email already has sign-in methods registered under another account
                return Result.success(DuplicateCheckResult.EMAIL_EXISTS)
            }

            // Phone duplicate is handled by Firebase Phone Auth flow itself.
            // If the phone was already registered, the user would have been signed
            // into the existing account during Step 2.

            Result.success(DuplicateCheckResult.NO_DUPLICATE)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates the password for the current user.
     */
    suspend fun updatePassword(password: String): Result<Unit> {
        return authManager.updatePassword(password)
    }

    /**
     * Step 5: Create Firebase Auth account with email and password.
     */
    suspend fun createUserWithEmailAndPassword(email: String, password: String): Result<String> {
        return authManager.createUserWithEmailAndPassword(email, password)
    }

    /**
     * Create user profile in Firestore after successful auth account creation.
     */
    suspend fun createUserProfile(
        authUserId: String,
        name: String,
        email: String,
        phone: String
    ): Result<Unit> {
        val profile = UserProfile(
            authUserId = authUserId,
            name = name,
            primaryEmail = email,
            phone = phone,
            accountType = AccountType.USER.name,
            createdAt = System.currentTimeMillis(),
            emailVerified = true,
            phoneVerified = true
        )
        return firestoreManager.createUserProfile(profile)
    }

    // ─── User Login ──────────────────────────────────────────────

    /**
     * Login with primary Gmail and password.
     */
    suspend fun loginWithEmailAndPassword(email: String, password: String): Result<String> {
        return authManager.signInWithEmailAndPassword(email, password)
    }

    /**
     * Send passwordless sign-in link to email (Firebase Email Link authentication).
     */
    suspend fun sendEmailSignInLink(email: String): Result<Unit> {
        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl("https://womensafetysystem.page.link/emailSignIn")
            .setHandleCodeInApp(true)
            .setAndroidPackageName("com.womensafety.system", true, null)
            .build()
        return authManager.sendSignInLinkToEmail(email, actionCodeSettings)
    }

    /**
     * Complete sign-in with email link.
     */
    suspend fun signInWithEmailLink(email: String, link: String): Result<String> {
        return authManager.signInWithEmailLink(email, link)
    }

    fun isSignInWithEmailLink(link: String): Boolean {
        return authManager.isSignInWithEmailLink(link)
    }

    // ─── Trusted Contact Auth ────────────────────────────────────

    /**
     * Create TC profile in Firestore after phone auth.
     */
    suspend fun createTrustedContactProfile(
        authUserId: String,
        name: String,
        phone: String
    ): Result<Unit> {
        val profile = TrustedContactProfile(
            authUserId = authUserId,
            name = name,
            phone = phone,
            accountType = AccountType.TRUSTED_CONTACT.name,
            createdAt = System.currentTimeMillis(),
            phoneVerified = true
        )
        return firestoreManager.createTrustedContactProfile(profile)
    }

    // ─── Password Reset ──────────────────────────────────────────

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return authManager.sendPasswordResetEmail(email)
    }

    // ─── Session ─────────────────────────────────────────────────

    fun signOut() {
        authManager.signOut()
    }

    /**
     * Determines the account type of the currently signed-in user.
     * Uses direct document get (by auth UID) — no list queries needed.
     */
    suspend fun getCurrentAccountType(): Result<AccountType?> {
        val uid = currentUserId ?: return Result.success(null)

        // Check if User
        val userResult = firestoreManager.getUserProfile(uid)
        if (userResult.isSuccess && userResult.getOrNull() != null) {
            return Result.success(AccountType.USER)
        }

        // Check if Trusted Contact
        val tcResult = firestoreManager.getTrustedContactProfile(uid)
        if (tcResult.isSuccess && tcResult.getOrNull() != null) {
            return Result.success(AccountType.TRUSTED_CONTACT)
        }

        return Result.success(null)
    }
}

enum class DuplicateCheckResult {
    NO_DUPLICATE,
    PHONE_EXISTS,
    EMAIL_EXISTS
}
