package com.womensafety.system.data.repository

import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.EmergencyContact
import com.womensafety.system.data.model.UserProfile

/**
 * Repository for User profile operations.
 * Accesses Firestore exclusively through [FirestoreManager] and Auth through [FirebaseAuthManager].
 *
 * NOTE: Phone/email duplicate lookups are not supported under the current
 * Firestore security rules which deny collection-wide list/query operations.
 * Duplicate detection is handled by Firebase Auth (for email) and
 * Firebase Phone Auth flow (for phone).
 */
class UserRepository(
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {

    val currentUserId: String?
        get() = authManager.currentUserId

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

    suspend fun updateUserProfile(authUserId: String, updates: Map<String, Any?>): Result<Unit> {
        return firestoreManager.updateUserProfile(authUserId, updates)
    }

    suspend fun updateSetupStatus(
        setupCompleted: Boolean,
        preferences: Map<String, Any?> = emptyMap()
    ): Result<Unit> {
        val userId = authManager.currentUserId
            ?: return Result.failure(Exception("User not authenticated"))
        val updates = preferences.toMutableMap<String, Any?>()
        updates["setupCompleted"] = setupCompleted
        return firestoreManager.updateUserSetupPreferences(userId, updates)
    }

    suspend fun addEmergencyContact(contact: EmergencyContact): Result<Unit> {
        val userId = authManager.currentUserId
            ?: return Result.failure(Exception("User not authenticated"))
        return firestoreManager.addEmergencyContactToUser(userId, contact)
    }

    suspend fun updateEmergencyContact(
        contactId: String,
        name: String,
        phone: String,
        isPrimary: Boolean,
        enableEscalation: Boolean
    ): Result<Unit> {
        val userId = authManager.currentUserId
            ?: return Result.failure(Exception("User not authenticated"))
        return firestoreManager.updateEmergencyContactInUser(
            userId,
            contactId,
            mapOf(
                "name" to name,
                "phone" to phone,
                "isPrimary" to isPrimary,
                "enableEscalation" to enableEscalation
            )
        )
    }

    suspend fun deleteEmergencyContact(contactId: String): Result<Unit> {
        val userId = authManager.currentUserId
            ?: return Result.failure(Exception("User not authenticated"))
        return firestoreManager.deleteEmergencyContactFromUser(userId, contactId)
    }

    suspend fun getEmergencyContacts(): Result<List<EmergencyContact>> {
        val userId = authManager.currentUserId
            ?: return Result.failure(Exception("User not authenticated"))
        return firestoreManager.getEmergencyContactsForUser(userId)
    }
}

