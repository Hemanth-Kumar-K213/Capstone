package com.womensafety.system.data.firebase

import android.app.Activity
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Wraps all Firebase Authentication operations.
 * This is the ONLY class that directly imports Firebase Auth SDK.
 * No Screen or ViewModel may access Firebase Auth directly.
 */
class FirebaseAuthManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    // ─── Email/Password Authentication ───────────────────────────

    /**
     * Creates a new user with email and password via Firebase Auth.
     * Returns the Firebase Auth UID on success.
     */
    suspend fun createUserWithEmailAndPassword(email: String, password: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("User creation failed"))
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in with email and password.
     * Returns the Firebase Auth UID on success.
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("Sign in failed"))
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Email Verification ──────────────────────────────────────

    /**
     * Sends a verification email to the currently signed-in user.
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reloads the current user to check if email has been verified.
     */
    suspend fun reloadUser(): Result<Boolean> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
            user.reload().await()
            Result.success(auth.currentUser?.isEmailVerified ?: false)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a sign-in link (OTP-style) to the given email.
     */
    suspend fun sendSignInLinkToEmail(email: String, actionCodeSettings: ActionCodeSettings): Result<Unit> {
        return try {
            auth.sendSignInLinkToEmail(email, actionCodeSettings).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in using an email link.
     */
    suspend fun signInWithEmailLink(email: String, emailLink: String): Result<String> {
        return try {
            val result = auth.signInWithEmailLink(email, emailLink).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("Email link sign in failed"))
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isSignInWithEmailLink(link: String): Boolean {
        return auth.isSignInWithEmailLink(link)
    }

    // ─── Password Reset ──────────────────────────────────────────

    /**
     * Sends a password reset email to the given address.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Phone Authentication ────────────────────────────────────

    /**
     * Initiates phone number verification via Firebase Phone Auth.
     * Callbacks handle auto-verification, code sent, and failure.
     */
    fun verifyPhoneNumber(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
        timeoutSeconds: Long = 60L,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null
    ) {
        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (resendToken != null) {
            optionsBuilder.setForceResendingToken(resendToken)
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    /**
     * Signs in using a phone auth credential (verification ID + OTP code).
     */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<String> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("Phone sign in failed"))
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Links a phone credential to the currently signed-in user.
     * Used during signup when user already has email/password auth.
     */
    suspend fun linkPhoneCredential(credential: PhoneAuthCredential): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
            user.linkWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates the phone number on the currently signed-in user.
     */
    suspend fun updatePhoneNumber(credential: PhoneAuthCredential): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
            user.updatePhoneNumber(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a PhoneAuthCredential from a verification ID and SMS code.
     */
    fun getPhoneCredential(verificationId: String, code: String): PhoneAuthCredential {
        return PhoneAuthProvider.getCredential(verificationId, code)
    }

    // ─── Account Management ──────────────────────────────────────

    /**
     * Re-authenticates the current user with email/password before
     * sensitive operations (e.g., phone number change).
     */
    suspend fun reauthenticateWithEmail(email: String, password: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Fetches sign-in methods for a given email.
     * Used for duplicate account detection.
     */
    suspend fun fetchSignInMethodsForEmail(email: String): Result<List<String>> {
        return try {
            val result = auth.fetchSignInMethodsForEmail(email).await()
            Result.success(result.signInMethods ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
