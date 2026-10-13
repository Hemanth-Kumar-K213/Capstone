package com.womensafety.system.data.repository

import kotlinx.coroutines.flow.Flow
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.data.model.Relationship

/**
 * Repository for managing User ↔ Trusted Contact relationships.
 * All relationships are keyed by STABLE Firebase Auth UIDs, never by the weekly UID.
 * Accesses Firestore exclusively through [FirestoreManager].
 *
 * IMPORTANT: Relationship document IDs are deterministic: {userId}_{trustedContactId}
 * This is enforced by the Firestore security rules.
 *
 * When creating or re-requesting relationships, this repository also maintains
 * the trustedContactIds array in the User profile and the connectedUserIds array
 * in the TC profile, because Firestore rules deny collection-wide list queries.
 */
class RelationshipRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {

    /**
     * Real-time flow of all relationships for a User.
     */
    fun observeRelationshipsForUser(userId: String): Flow<List<Relationship>> {
        return firestoreManager.observeRelationshipsForUser(userId)
    }

    /**
     * Real-time flow of all relationships for a Trusted Contact.
     */
    fun observeRelationshipsForTrustedContact(trustedContactId: String): Flow<List<Relationship>> {
        return firestoreManager.observeRelationshipsForTrustedContact(trustedContactId)
    }

    /**
     * Creates a new PENDING relationship request from a TC to a User.
     * The [userId] is the User's stable Firebase Auth UID, resolved from the weekly UID.
     * The [trustedContactId] is the TC's stable Firebase Auth UID.
     * The [userName] and [tcName] are denormalized display names stored in the
     * relationship document so neither party needs cross-collection profile reads.
     *
     * Also updates both profiles' ID arrays so that relationships can be
     * listed later without collection-wide queries.
     */
    suspend fun createRelationshipRequest(
        userId: String,
        trustedContactId: String,
        userName: String = "",
        tcName: String = "",
        tcPhone: String = ""
    ): Result<String> {
        // Check for existing relationship using deterministic doc ID
        val existing = firestoreManager.findExistingRelationship(userId, trustedContactId)
        val existingRel = existing.getOrNull()

        if (existingRel != null) {
            when (existingRel.permissionState) {
                PermissionState.APPROVED -> return Result.failure(Exception("Already connected to this user"))
                PermissionState.PENDING -> {
                    // Update the request with refreshed timestamps and denormalized info
                    val relationship = Relationship(
                        id = FirestoreManager.relationshipDocId(userId, trustedContactId),
                        userId = userId,
                        trustedContactId = trustedContactId,
                        userName = userName.ifBlank { existingRel.userName },
                        trustedContactName = tcName.ifBlank { existingRel.trustedContactName },
                        trustedContactPhone = tcPhone.ifBlank { existingRel.trustedContactPhone },
                        status = PermissionState.PENDING.name,
                        requestedAt = System.currentTimeMillis()
                    )
                    val result = firestoreManager.createRelationship(relationship)
                    if (result.isSuccess) {
                        try { firestoreManager.addConnectedUserIdToTc(trustedContactId, userId) } catch (_: Exception) {}
                    }
                    return result
                }
                PermissionState.DENIED, PermissionState.REVOKED, PermissionState.EXPIRED -> {
                    // Allow re-request by overwriting the same deterministic document
                    val relationship = Relationship(
                        id = FirestoreManager.relationshipDocId(userId, trustedContactId),
                        userId = userId,
                        trustedContactId = trustedContactId,
                        userName = userName.ifBlank { existingRel.userName },
                        trustedContactName = tcName.ifBlank { existingRel.trustedContactName },
                        trustedContactPhone = tcPhone.ifBlank { existingRel.trustedContactPhone },
                        status = PermissionState.PENDING.name,
                        requestedAt = System.currentTimeMillis()
                    )
                    val result = firestoreManager.createRelationship(relationship)
                    if (result.isSuccess) {
                        try { firestoreManager.addConnectedUserIdToTc(trustedContactId, userId) } catch (_: Exception) {}
                    }
                    return result
                }
            }
        }

        val relationship = Relationship(
            id = FirestoreManager.relationshipDocId(userId, trustedContactId),
            userId = userId,
            trustedContactId = trustedContactId,
            userName = userName,
            trustedContactName = tcName,
            trustedContactPhone = tcPhone,
            status = PermissionState.PENDING.name,
            requestedAt = System.currentTimeMillis()
        )
        val result = firestoreManager.createRelationship(relationship)

        if (result.isSuccess) {
            // Update TC profile ID array for relationship listing
            try {
                firestoreManager.addConnectedUserIdToTc(trustedContactId, userId)
            } catch (_: Exception) {}
        }

        return result
    }

    /**
     * User approves a pending TC request.
     *
     * IMPORTANT: Firestore rules require userId and trustedContactId in update data
     * (request.resource.data must contain these fields).
     */
    suspend fun approveRelationship(relationshipId: String): Result<Unit> {
        val parts = relationshipId.split("_", limit = 2)
        if (parts.size != 2) return Result.failure(Exception("Invalid relationship ID"))

        val userId = parts[0]
        val trustedContactId = parts[1]

        val result = firestoreManager.updateRelationshipStatus(
            relationshipId,
            mapOf(
                "userId" to userId,
                "trustedContactId" to trustedContactId,
                "status" to PermissionState.APPROVED.name,
                "respondedAt" to System.currentTimeMillis()
            )
        )

        if (result.isSuccess) {
            // Now the user can update their own profile to add the TC ID
            firestoreManager.addTrustedContactIdToUser(userId, trustedContactId)
        }

        return result
    }

    /**
     * User denies a pending TC request.
     * Completely deletes the relationship from Firestore so it never lingers or reappears.
     */
    suspend fun denyRelationship(relationshipId: String): Result<Unit> {
        val parts = relationshipId.split("_", limit = 2)
        if (parts.size == 2) {
            val userId = parts[0]
            val trustedContactId = parts[1]
            try { firestoreManager.removeTrustedContactIdFromUser(userId, trustedContactId) } catch (_: Exception) {}
        }
        return firestoreManager.deleteRelationship(relationshipId)
    }

    /**
     * User revokes an approved TC's access.
     * Completely unlinks the user profile and deletes the relationship from Firestore.
     */
    suspend fun revokeRelationship(relationshipId: String): Result<Unit> {
        val parts = relationshipId.split("_", limit = 2)
        if (parts.size == 2) {
            val userId = parts[0]
            val trustedContactId = parts[1]
            try { firestoreManager.removeTrustedContactIdFromUser(userId, trustedContactId) } catch (_: Exception) {}
        }
        return firestoreManager.deleteRelationship(relationshipId)
    }

    /**
     * Trusted Contact removes a monitored user (Approved or Pending).
     * Cleans up the TC's connectedUserIds list and completely deletes the relationship.
     */
    suspend fun removeRelationshipByTc(tcId: String, relationshipId: String): Result<Unit> {
        val parts = relationshipId.split("_", limit = 2)
        val userId = if (parts.size == 2) parts[0] else ""

        if (userId.isNotBlank()) {
            try { firestoreManager.removeConnectedUserIdFromTc(tcId, userId) } catch (_: Exception) {}
        }

        return firestoreManager.deleteRelationship(relationshipId)
    }

    /**
     * Deletes a relationship completely from Firestore and unlinks both profiles.
     */
    suspend fun deleteRelationship(relationshipId: String): Result<Unit> {
        val parts = relationshipId.split("_", limit = 2)
        if (parts.size == 2) {
            val userId = parts[0]
            val tcId = parts[1]
            try { firestoreManager.removeTrustedContactIdFromUser(userId, tcId) } catch (_: Exception) {}
        }
        return firestoreManager.deleteRelationship(relationshipId)
    }

    /**
     * Gets all relationships for a User (where they are the owner).
     * Reads from the user's trustedContactIds array, then fetches each doc.
     */
    suspend fun getRelationshipsForUser(userId: String): Result<List<Relationship>> {
        val rawResult = firestoreManager.getRelationshipsForUser(userId)
        if (rawResult.isFailure) return rawResult

        val list = rawResult.getOrNull() ?: emptyList()
        val enriched = list.map { rel ->
            if (rel.trustedContactName.isBlank() || rel.trustedContactPhone.isBlank()) {
                val tcProfileResult = firestoreManager.getTrustedContactProfile(rel.trustedContactId)
                val tcProfile = tcProfileResult.getOrNull()
                if (tcProfile != null) {
                    rel.copy(
                        trustedContactName = rel.trustedContactName.ifBlank { tcProfile.name },
                        trustedContactPhone = rel.trustedContactPhone.ifBlank { tcProfile.phone }
                    )
                } else {
                    rel
                }
            } else {
                rel
            }
        }
        return Result.success(enriched)
    }

    /**
     * Gets all relationships for a Trusted Contact.
     * Reads from the TC's connectedUserIds array, then fetches each doc.
     */
    suspend fun getRelationshipsForTrustedContact(trustedContactId: String): Result<List<Relationship>> {
        val rawResult = firestoreManager.getRelationshipsForTrustedContact(trustedContactId)
        if (rawResult.isFailure) return rawResult

        val list = rawResult.getOrNull() ?: emptyList()
        val enriched = list.map { rel ->
            val userProfileResult = firestoreManager.getUserProfile(rel.userId)
            val userProfile = userProfileResult.getOrNull()
            if (userProfile != null) {
                rel.copy(
                    userName = userProfile.name,
                    userPhone = userProfile.phone
                )
            } else {
                rel
            }
        }
        return Result.success(enriched)
    }

    /**
     * Counts approved Trusted Contacts for a User.
     */
    suspend fun getApprovedContactCount(userId: String): Result<Int> {
        val result = firestoreManager.getRelationshipsForUser(userId)
        return result.map { relationships ->
            relationships.count { it.permissionState == PermissionState.APPROVED }
        }
    }
}
