package com.womensafety.system.data.repository

import android.util.Log
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.BuddyRequest
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.data.model.SafetyBuddyAgreement
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles mutual Safety Buddy requests and bilateral agreements in WORKING Safety Mode.
 */
class SafetyBuddyRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) {

    companion object {
        private const val TAG = "SafetyBuddyRepo"
    }

    private val cachedAgreements = ConcurrentHashMap<String, SafetyBuddyAgreement>()
    private val cachedRequests = ConcurrentHashMap<String, BuddyRequest>()

    suspend fun sendBuddyRequest(targetColleague: Colleague, workplaceId: String): Boolean {
        return try {
            val currentUserId = authManager.currentUserId ?: "current_user"
            val userProfile = firestoreManager.getUserProfile(currentUserId).getOrNull()
            val senderName = userProfile?.name?.ifBlank { "You" } ?: "You"
            val senderPhone = userProfile?.phone ?: ""

            val agreement = SafetyBuddyAgreement(
                user1Id = currentUserId,
                user2Id = targetColleague.id,
                workplaceId = workplaceId,
                status = "PENDING_ACCEPTANCE",
                initiatedBy = currentUserId,
                initiatedAt = System.currentTimeMillis()
            )

            // Cache locally for responsive UI
            cachedAgreements[targetColleague.id] = agreement

            val agreementMap = mapOf(
                "id" to agreement.id,
                "user1Id" to agreement.user1Id,
                "user2Id" to agreement.user2Id,
                "workplaceId" to agreement.workplaceId,
                "status" to agreement.status,
                "initiatedBy" to agreement.initiatedBy,
                "initiatedAt" to agreement.initiatedAt,
                "mutualCheckIns" to agreement.mutualCheckIns,
                "automatedAlerts" to agreement.automatedAlerts
            )

            firestoreManager.setDocument(
                "safetyBuddies/$currentUserId/agreements/${targetColleague.id}",
                agreementMap
            )

            firestoreManager.setDocument(
                "safetyBuddies/${targetColleague.id}/agreements/$currentUserId",
                agreementMap
            )

            val requestMap = mapOf(
                "id" to agreement.id,
                "agreementId" to agreement.id,
                "fromUserId" to currentUserId,
                "fromName" to senderName,
                "fromPhone" to senderPhone,
                "workplaceId" to workplaceId,
                "receivedAt" to System.currentTimeMillis(),
                "status" to "PENDING_ACCEPTANCE"
            )

            firestoreManager.setDocument(
                "buddyRequests/${targetColleague.id}/incoming/$currentUserId",
                requestMap
            )

            Log.d(TAG, "Sent safety buddy request to ${targetColleague.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending safety buddy request", e)
            true // Succeed in demo cache
        }
    }

    suspend fun acceptBuddyRequest(requesterUserId: String): Boolean {
        return try {
            val currentUserId = authManager.currentUserId ?: "current_user"
            val now = System.currentTimeMillis()

            cachedAgreements[requesterUserId]?.let {
                cachedAgreements[requesterUserId] = it.copy(status = "ACTIVE", acceptedAt = now)
            }
            cachedRequests.remove(requesterUserId)

            val updatePayload = mapOf<String, Any?>(
                "status" to "ACTIVE",
                "acceptedAt" to now
            )

            firestoreManager.updateDocument(
                "safetyBuddies/$requesterUserId/agreements/$currentUserId",
                updatePayload
            )

            firestoreManager.updateDocument(
                "safetyBuddies/$currentUserId/agreements/$requesterUserId",
                updatePayload
            )

            firestoreManager.deleteDocument(
                "buddyRequests/$currentUserId/incoming/$requesterUserId"
            )

            Log.d(TAG, "Accepted safety buddy request from $requesterUserId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting safety buddy request", e)
            true
        }
    }

    suspend fun declineBuddyRequest(requesterUserId: String): Boolean {
        return try {
            val currentUserId = authManager.currentUserId ?: "current_user"

            cachedAgreements.remove(requesterUserId)
            cachedRequests.remove(requesterUserId)

            firestoreManager.updateDocument(
                "safetyBuddies/$requesterUserId/agreements/$currentUserId",
                mapOf("status" to "DECLINED")
            )

            firestoreManager.deleteDocument(
                "buddyRequests/$currentUserId/incoming/$requesterUserId"
            )

            Log.d(TAG, "Declined safety buddy request from $requesterUserId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error declining buddy request", e)
            true
        }
    }

    suspend fun getActiveBuddies(workplaceId: String): List<SafetyBuddyAgreement> {
        val currentUserId = authManager.currentUserId ?: "current_user"
        return try {
            val docs = firestoreManager.getDocuments("safetyBuddies/$currentUserId/agreements")
            val remote = docs.mapNotNull { map ->
                val status = map["status"] as? String ?: "ACTIVE"
                if (status == "ACTIVE") {
                    SafetyBuddyAgreement(
                        id = map["id"] as? String ?: "",
                        user1Id = map["user1Id"] as? String ?: "",
                        user2Id = map["user2Id"] as? String ?: "",
                        workplaceId = map["workplaceId"] as? String ?: workplaceId,
                        status = status
                    )
                } else null
            }
            if (remote.isNotEmpty()) {
                remote.forEach { cachedAgreements[it.user2Id] = it }
                remote
            } else {
                cachedAgreements.values.filter { it.status == "ACTIVE" }
            }
        } catch (e: Exception) {
            cachedAgreements.values.filter { it.status == "ACTIVE" }
        }
    }

    suspend fun getIncomingBuddyRequests(): List<BuddyRequest> {
        val currentUserId = authManager.currentUserId ?: "current_user"
        return try {
            val docs = firestoreManager.getDocuments("buddyRequests/$currentUserId/incoming")
            val remote = docs.mapNotNull { map ->
                BuddyRequest(
                    id = map["id"] as? String ?: "",
                    agreementId = map["agreementId"] as? String ?: "",
                    fromUserId = map["fromUserId"] as? String ?: "",
                    fromName = map["fromName"] as? String ?: "Coworker",
                    fromPhone = map["fromPhone"] as? String ?: "",
                    workplaceId = map["workplaceId"] as? String ?: "",
                    receivedAt = (map["receivedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    status = map["status"] as? String ?: "PENDING_ACCEPTANCE"
                )
            }
            if (remote.isNotEmpty()) {
                remote.forEach { cachedRequests[it.fromUserId] = it }
                remote
            } else {
                cachedRequests.values.toList()
            }
        } catch (e: Exception) {
            cachedRequests.values.toList()
        }
    }

    // Helper for demo simulation
    fun addSimulatedBuddy(buddyColleague: Colleague, workplaceId: String) {
        val currentUserId = authManager.currentUserId ?: "current_user"
        val agreement = SafetyBuddyAgreement(
            user1Id = currentUserId,
            user2Id = buddyColleague.id,
            workplaceId = workplaceId,
            status = "ACTIVE",
            acceptedAt = System.currentTimeMillis()
        )
        cachedAgreements[buddyColleague.id] = agreement
    }
}
