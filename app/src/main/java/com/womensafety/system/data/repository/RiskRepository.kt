package com.womensafety.system.data.repository

import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.RiskEvent

/**
 * Repository handling RiskEvent persistence operations.
 * Communicates with Firestore exclusively through [FirestoreManager].
 */
class RiskRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {
    suspend fun createRiskEvent(event: RiskEvent): Result<Unit> {
        return firestoreManager.createRiskEvent(event)
    }

    suspend fun updateRiskEventStatus(eventId: String, status: String): Result<Unit> {
        return firestoreManager.updateRiskEventStatus(eventId, status)
    }
}
