package com.womensafety.system.data.repository

import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.UserProfile

/**
 * Repository for User profile operations.
 * Accesses Firestore exclusively through [FirestoreManager].
 *
 * NOTE: Phone/email duplicate lookups are not supported under the current
 * Firestore security rules which deny collection-wide list/query operations.
 * Duplicate detection is handled by Firebase Auth (for email) and
 * Firebase Phone Auth flow (for phone).
 */
class UserRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {

    suspend fun getUserProfile(authUserId: String): Result<UserProfile?> {
        return firestoreManager.getUserProfile(authUserId)
    }

    suspend fun updatePhone(authUserId: String, newPhone: String): Result<Unit> {
        return firestoreManager.updateUserProfile(authUserId, mapOf("phone" to newPhone))
    }

    suspend fun updateEmailVerified(authUserId: String, verified: Boolean): Result<Unit> {
        return firestoreManager.updateUserProfile(authUserId, mapOf("emailVerified" to verified))
    }

    suspend fun updatePhoneVerified(authUserId: String, verified: Boolean): Result<Unit> {
        return firestoreManager.updateUserProfile(authUserId, mapOf("phoneVerified" to verified))
    }
}
