package com.womensafety.system.data.repository

import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.model.SOSStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository for creating, updating, and monitoring SOS events.
 * All Firebase interactions are routed strictly through FirestoreManager.
 */
class SOSRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {

    suspend fun createSOSEvent(event: SOSEvent): Result<Unit> {
        return firestoreManager.createSOSEvent(event)
    }

    suspend fun updateSOSEventStatus(
        eventId: String,
        status: SOSStatus,
        resolvedAt: Long = System.currentTimeMillis()
    ): Result<Unit> {
        return firestoreManager.updateSOSEventStatus(eventId, status, resolvedAt)
    }

    suspend fun updateDeliveryStats(
        eventId: String,
        notificationsSent: Int,
        smsSent: Int,
        callMade: Boolean,
        fcmTokens: List<String>
    ): Result<Unit> {
        return firestoreManager.updateSOSDeliveryStats(
            eventId = eventId,
            notificationsSent = notificationsSent,
            smsSent = smsSent,
            callMade = callMade,
            fcmTokens = fcmTokens
        )
    }

    suspend fun getActiveSOSEvent(userId: String): Result<SOSEvent?> {
        return firestoreManager.getActiveUserSOSEvent(userId)
    }

    suspend fun getSOSHistory(userId: String, limit: Int = 50): Result<List<SOSEvent>> {
        return firestoreManager.getUserSOSHistory(userId, limit)
    }

    fun observeActiveSOSEvent(userId: String): Flow<SOSEvent?> {
        return firestoreManager.listenToUserSOSEvents(userId)
    }
}
