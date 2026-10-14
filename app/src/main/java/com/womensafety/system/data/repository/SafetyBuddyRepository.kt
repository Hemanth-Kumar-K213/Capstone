package com.womensafety.system.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.BuddyRequest
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.data.model.SafetyBuddyAgreement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Requirement 2E: Mutual Safety Buddy Repository.
 *
 * Implements bilateral mutual pairing with real-time Firestore listeners:
 * - Requests: buddyRequests/{targetUserId}/incoming/{requestingUserId}
 * - Agreements: safetyBuddies/{userId}/agreements/{buddyId}
 */
class SafetyBuddyRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) {

    companion object {
        private const val TAG = "SafetyBuddyRepo"
    }

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _incomingRequests = MutableStateFlow<List<BuddyRequest>>(emptyList())
    val incomingRequests: StateFlow<List<BuddyRequest>> = _incomingRequests.asStateFlow()

    private val _activeAgreements = MutableStateFlow<List<SafetyBuddyAgreement>>(emptyList())
    val activeAgreements: StateFlow<List<SafetyBuddyAgreement>> = _activeAgreements.asStateFlow()

    private val pairedBuddyIds = ConcurrentHashMap<String, Boolean>()

    private var requestsListener: ListenerRegistration? = null
    private var agreementsListener: ListenerRegistration? = null

    init {
        startRealtimeListeners()
    }

    /**
     * Attaches real-time listeners to incoming buddy requests and active agreements.
     */
    fun startRealtimeListeners() {
        val currentUserId = authManager.currentUserId ?: return

        // 1. Real-time snapshot listener on buddyRequests/{userId}/incoming
        requestsListener?.remove()
        requestsListener = db.collection("buddyRequests")
            .document(currentUserId)
            .collection("incoming")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to incoming buddy requests", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        BuddyRequest(
                            id = doc.id,
                            agreementId = data["agreementId"] as? String ?: doc.id,
                            fromUserId = data["fromUserId"] as? String ?: doc.id,
                            fromName = data["fromName"] as? String ?: "Coworker",
                            fromPhone = data["fromPhone"] as? String ?: "",
                            workplaceId = data["workplaceId"] as? String ?: "",
                            receivedAt = (data["requestedAt"] as? Number)?.toLong()
                                ?: (data["receivedAt"] as? Number)?.toLong()
                                ?: System.currentTimeMillis(),
                            status = data["status"] as? String ?: "PENDING_ACCEPTANCE"
                        )
                    }
                    _incomingRequests.value = requests
                    Log.d(TAG, "Real-time incoming buddy requests updated: ${requests.size} pending")
                }
            }

        // 2. Real-time snapshot listener on safetyBuddies/{userId}/agreements
        agreementsListener?.remove()
        agreementsListener = db.collection("safetyBuddies")
            .document(currentUserId)
            .collection("agreements")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to buddy agreements", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val agreements = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val status = data["status"] as? String ?: "ACTIVE"
                        if (status == "ACTIVE") {
                            val u1 = data["user1Id"] as? String ?: currentUserId
                            val u2 = data["user2Id"] as? String ?: doc.id
                            val buddyId = if (u1 == currentUserId) u2 else u1
                            pairedBuddyIds[buddyId] = true

                            SafetyBuddyAgreement(
                                id = doc.id,
                                user1Id = u1,
                                user2Id = u2,
                                workplaceId = data["workplaceId"] as? String ?: "",
                                status = status,
                                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                        } else null
                    }
                    _activeAgreements.value = agreements
                    Log.d(TAG, "Real-time active buddy agreements updated: ${agreements.size} active")
                }
            }
    }

    fun isPairedBuddy(colleagueId: String): Boolean {
        if (pairedBuddyIds[colleagueId] == true) return true
        return _activeAgreements.value.any { it.user1Id == colleagueId || it.user2Id == colleagueId }
    }

    /**
     * Sends a real Safety Buddy request:
     * Path: buddyRequests/{targetUserId}/incoming/{requestingUserId}
     */
    suspend fun sendBuddyRequest(targetColleague: Colleague, workplaceId: String): Boolean {
        return try {
            val currentUserId = authManager.currentUserId ?: return false
            val userProfile = firestoreManager.getUserProfile(currentUserId).getOrNull()
            val senderName = userProfile?.name?.ifBlank { "You" } ?: "You"

            val requestMap = mapOf(
                "fromUserId" to currentUserId,
                "fromName" to senderName,
                "toUserId" to targetColleague.id,
                "status" to "PENDING_ACCEPTANCE",
                "requestedAt" to System.currentTimeMillis(),
                "workplaceId" to workplaceId
            )

            db.collection("buddyRequests")
                .document(targetColleague.id)
                .collection("incoming")
                .document(currentUserId)
                .set(requestMap)
                .await()

            Log.d(TAG, "Created buddy request in buddyRequests/${targetColleague.id}/incoming/$currentUserId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending buddy request to ${targetColleague.name}", e)
            false
        }
    }

    /**
     * Target user accepts request:
     * 1. Creates agreement in safetyBuddies/{userId}/agreements/{buddyId}
     * 2. Creates symmetric agreement in safetyBuddies/{buddyId}/agreements/{userId}
     * 3. Deletes buddy request document
     */
    suspend fun acceptBuddyRequest(request: BuddyRequest): Boolean {
        return try {
            val currentUserId = authManager.currentUserId ?: return false
            val buddyId = request.fromUserId
            val now = System.currentTimeMillis()

            val agreementData = mapOf(
                "user1Id" to currentUserId,
                "user2Id" to buddyId,
                "status" to "ACTIVE",
                "createdAt" to now,
                "workplaceId" to request.workplaceId
            )

            // Batch commit bilateral agreements and delete request document
            val batch = db.batch()

            val myAgreementRef = db.collection("safetyBuddies")
                .document(currentUserId)
                .collection("agreements")
                .document(buddyId)

            val buddyAgreementRef = db.collection("safetyBuddies")
                .document(buddyId)
                .collection("agreements")
                .document(currentUserId)

            val incomingRequestRef = db.collection("buddyRequests")
                .document(currentUserId)
                .collection("incoming")
                .document(buddyId)

            batch.set(myAgreementRef, agreementData)
            batch.set(buddyAgreementRef, agreementData)
            batch.delete(incomingRequestRef)

            batch.commit().await()

            pairedBuddyIds[buddyId] = true
            Log.d(TAG, "Bilateral safety buddy agreement created between $currentUserId and $buddyId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting safety buddy request", e)
            false
        }
    }

    /**
     * Target user declines request:
     * Deletes buddy request document from buddyRequests/{userId}/incoming/{requesterId}
     */
    suspend fun declineBuddyRequest(request: BuddyRequest): Boolean {
        return try {
            val currentUserId = authManager.currentUserId ?: return false
            val buddyId = request.fromUserId

            db.collection("buddyRequests")
                .document(currentUserId)
                .collection("incoming")
                .document(buddyId)
                .delete()
                .await()

            Log.d(TAG, "Deleted declined buddy request from $buddyId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error declining buddy request", e)
            false
        }
    }

    fun stopListeners() {
        requestsListener?.remove()
        agreementsListener?.remove()
    }
}
