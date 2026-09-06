package com.womensafety.system.data.repository

import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.TrustedContactProfile

/**
 * Repository for Trusted Contact profile operations.
 * Accesses Firestore exclusively through [FirestoreManager].
 *
 * NOTE: Phone-based lookups (isPhoneTaken) are not supported under the current
 * Firestore security rules which deny collection-wide list/query operations.
 * Phone duplicate detection is handled by Firebase Phone Auth flow itself —
 * if a phone is already registered, the auth flow signs in to the existing account.
 */
class TrustedContactRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {

    suspend fun getTrustedContactProfile(authUserId: String): Result<TrustedContactProfile?> {
        return firestoreManager.getTrustedContactProfile(authUserId)
    }
}
