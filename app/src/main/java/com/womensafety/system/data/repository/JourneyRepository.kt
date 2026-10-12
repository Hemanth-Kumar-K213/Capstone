package com.womensafety.system.data.repository

import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.JourneyRecord

/**
 * Repository handling journey history persistence operations.
 * Communicates with Firestore exclusively through [FirestoreManager].
 */
class JourneyRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {
    suspend fun createJourneyRecord(record: JourneyRecord): Result<Unit> {
        return firestoreManager.createJourneyRecord(record)
    }

    suspend fun getUserJourneys(userId: String): Result<List<JourneyRecord>> {
        return firestoreManager.getUserJourneys(userId)
    }
}
