package com.womensafety.system.data.repository

import android.util.Log
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
     * Marks status as DENIED, removes profile references, and deletes the relationship document.
     */
    suspend fun denyRelationship(
        relationshipId: String,
        knownUserId: String? = null,
        knownTcId: String? = null
    ): Result<Unit> {
        val trimmedRelId = relationshipId.trim()
        var resolvedUserId = knownUserId?.trim() ?: ""
        var resolvedTcId = knownTcId?.trim() ?: ""

        if ((resolvedUserId.isBlank() || resolvedTcId.isBlank()) && trimmedRelId.contains("_")) {
            val parts = trimmedRelId.split("_", limit = 2)
            if (parts.size == 2) {
                if (resolvedUserId.isBlank()) resolvedUserId = parts[0]
                if (resolvedTcId.isBlank()) resolvedTcId = parts[1]
            }
        }

        if (resolvedUserId.isBlank() || resolvedTcId.isBlank()) {
            val existing = firestoreManager.getRelationshipById(trimmedRelId).getOrNull()
            if (existing != null) {
                if (resolvedUserId.isBlank()) resolvedUserId = existing.userId
                if (resolvedTcId.isBlank()) resolvedTcId = existing.trustedContactId
            }
        }

        // Clean up arrays
        if (resolvedUserId.isNotBlank() && resolvedTcId.isNotBlank()) {
            try { firestoreManager.removeTrustedContactIdFromUser(resolvedUserId, resolvedTcId) } catch (e: Exception) {
                Log.w("RelationshipRepo", "removeTrustedContactIdFromUser error: ${e.message}")
            }
            try { firestoreManager.removeConnectedUserIdFromTc(resolvedTcId, resolvedUserId) } catch (e: Exception) {
                Log.w("RelationshipRepo", "removeConnectedUserIdFromTc error: ${e.message}")
            }
        }

        // Update status to DENIED so queries and real-time listeners immediately ignore it
        val statusFields = mutableMapOf<String, Any?>(
            "status" to PermissionState.DENIED.name,
            "respondedAt" to System.currentTimeMillis()
        )
        if (resolvedUserId.isNotBlank()) statusFields["userId"] = resolvedUserId
        if (resolvedTcId.isNotBlank()) statusFields["trustedContactId"] = resolvedTcId

        val updateResult = firestoreManager.updateRelationshipStatus(trimmedRelId, statusFields)
        if (resolvedUserId.isNotBlank() && resolvedTcId.isNotBlank()) {
            val deterministicId = FirestoreManager.relationshipDocId(resolvedUserId, resolvedTcId)
            if (deterministicId != trimmedRelId) {
                try { firestoreManager.updateRelationshipStatus(deterministicId, statusFields) } catch (_: Exception) {}
            }
        }

        // Hard-delete document from collection
        val deleteResult = firestoreManager.deleteRelationship(trimmedRelId)
        if (resolvedUserId.isNotBlank() && resolvedTcId.isNotBlank()) {
            val deterministicId = FirestoreManager.relationshipDocId(resolvedUserId, resolvedTcId)
            if (deterministicId != trimmedRelId) {
                try { firestoreManager.deleteRelationship(deterministicId) } catch (_: Exception) {}
            }
        }

        return if (deleteResult.isSuccess || updateResult.isSuccess) {
            Result.success(Unit)
        } else {
            deleteResult
        }
    }

    /**
     * User revokes an approved TC's access.
     * Completely unlinks the user profile, sets status to REVOKED, and deletes the relationship.
     */
    suspend fun revokeRelationship(
        relationshipId: String,
        targetTcId: String? = null,
        currentUserId: String? = null
    ): Result<Unit> {
        val trimmedRelId = relationshipId.trim()
        var resolvedUserId = currentUserId?.trim() ?: ""
        var resolvedTcId = targetTcId?.trim() ?: ""

        if ((resolvedUserId.isBlank() || resolvedTcId.isBlank()) && trimmedRelId.contains("_")) {
            val parts = trimmedRelId.split("_", limit = 2)
            if (parts.size == 2) {
                if (resolvedUserId.isBlank()) resolvedUserId = parts[0]
                if (resolvedTcId.isBlank()) resolvedTcId = parts[1]
            }
        }

        if (resolvedUserId.isBlank() || resolvedTcId.isBlank()) {
            val existing = firestoreManager.getRelationshipById(trimmedRelId).getOrNull()
            if (existing != null) {
                if (resolvedUserId.isBlank()) resolvedUserId = existing.userId
                if (resolvedTcId.isBlank()) resolvedTcId = existing.trustedContactId
            }
        }

        // Clean up arrays
        if (resolvedUserId.isNotBlank() && resolvedTcId.isNotBlank()) {
            try { firestoreManager.removeTrustedContactIdFromUser(resolvedUserId, resolvedTcId) } catch (e: Exception) {
                Log.w("RelationshipRepo", "removeTrustedContactIdFromUser error: ${e.message}")
            }
            try { firestoreManager.removeConnectedUserIdFromTc(resolvedTcId, resolvedUserId) } catch (e: Exception) {
                Log.w("RelationshipRepo", "removeConnectedUserIdFromTc error: ${e.message}")
            }
        }

        // Update status to REVOKED so queries and real-time listeners immediately ignore it
        val statusFields = mutableMapOf<String, Any?>(
            "status" to PermissionState.REVOKED.name,
            "revokedAt" to System.currentTimeMillis()
        )
        if (resolvedUserId.isNotBlank()) statusFields["userId"] = resolvedUserId
        if (resolvedTcId.isNotBlank()) statusFields["trustedContactId"] = resolvedTcId

        val updateResult = firestoreManager.updateRelationshipStatus(trimmedRelId, statusFields)
        if (resolvedUserId.isNotBlank() && resolvedTcId.isNotBlank()) {
            val deterministicId = FirestoreManager.relationshipDocId(resolvedUserId, resolvedTcId)
            if (deterministicId != trimmedRelId) {
                try { firestoreManager.updateRelationshipStatus(deterministicId, statusFields) } catch (_: Exception) {}
            }
        }

        // Hard-delete document from collection
        val deleteResult = firestoreManager.deleteRelationship(trimmedRelId)
        if (resolvedUserId.isNotBlank() && resolvedTcId.isNotBlank()) {
            val deterministicId = FirestoreManager.relationshipDocId(resolvedUserId, resolvedTcId)
            if (deterministicId != trimmedRelId) {
                try { firestoreManager.deleteRelationship(deterministicId) } catch (_: Exception) {}
            }
        }

        return if (deleteResult.isSuccess || updateResult.isSuccess) {
            Result.success(Unit)
        } else {
            deleteResult
        }
    }

    /**
     * Trusted Contact removes a monitored user (Approved or Pending).
     * Cleans up the TC's connectedUserIds list, marks status as REVOKED, and deletes the relationship.
     */
    suspend fun removeRelationshipByTc(
        tcId: String,
        relationshipId: String,
        targetUserId: String? = null
    ): Result<Unit> {
        val trimmedRelId = relationshipId.trim()
        var resolvedUserId = targetUserId?.trim() ?: ""

        if (resolvedUserId.isBlank() && trimmedRelId.contains("_")) {
            val parts = trimmedRelId.split("_", limit = 2)
            if (parts.size == 2) {
                resolvedUserId = parts[0]
            }
        }

        if (resolvedUserId.isBlank()) {
            val existing = firestoreManager.getRelationshipById(trimmedRelId).getOrNull()
            if (existing != null && existing.userId.isNotBlank()) {
                resolvedUserId = existing.userId
            }
        }

        // 1. Clean up TC's connectedUserIds array
        if (resolvedUserId.isNotBlank()) {
            try {
                firestoreManager.removeConnectedUserIdFromTc(tcId, resolvedUserId)
            } catch (e: Exception) {
                Log.w("RelationshipRepo", "removeConnectedUserIdFromTc error: ${e.message}")
            }
        }

        // 2. Soft-delete: update status in Firestore to REVOKED
        val statusFields = mutableMapOf<String, Any?>(
            "status" to PermissionState.REVOKED.name,
            "revokedAt" to System.currentTimeMillis()
        )
        if (resolvedUserId.isNotBlank()) statusFields["userId"] = resolvedUserId
        if (tcId.isNotBlank()) statusFields["trustedContactId"] = tcId

        val updateResult = firestoreManager.updateRelationshipStatus(trimmedRelId, statusFields)
        if (resolvedUserId.isNotBlank()) {
            val deterministicId = FirestoreManager.relationshipDocId(resolvedUserId, tcId)
            if (deterministicId != trimmedRelId) {
                try {
                    firestoreManager.updateRelationshipStatus(deterministicId, statusFields)
                } catch (_: Exception) {}
            }
        }

        // 3. Hard-delete document from collection
        val deleteResult = firestoreManager.deleteRelationship(trimmedRelId)
        if (resolvedUserId.isNotBlank()) {
            val deterministicId = FirestoreManager.relationshipDocId(resolvedUserId, tcId)
            if (deterministicId != trimmedRelId) {
                try {
                    firestoreManager.deleteRelationship(deterministicId)
                } catch (_: Exception) {}
            }
        }

        return if (deleteResult.isSuccess || updateResult.isSuccess) {
            Result.success(Unit)
        } else {
            deleteResult
        }
    }

    /**
     * Deletes a relationship completely from Firestore and unlinks both profiles.
     */
    suspend fun deleteRelationship(
        relationshipId: String,
        knownUserId: String? = null,
        knownTcId: String? = null
    ): Result<Unit> {
        val trimmedRelId = relationshipId.trim()
        var resolvedUserId = knownUserId?.trim() ?: ""
        var resolvedTcId = knownTcId?.trim() ?: ""

        if ((resolvedUserId.isBlank() || resolvedTcId.isBlank()) && trimmedRelId.contains("_")) {
            val parts = trimmedRelId.split("_", limit = 2)
            if (parts.size == 2) {
                if (resolvedUserId.isBlank()) resolvedUserId = parts[0]
                if (resolvedTcId.isBlank()) resolvedTcId = parts[1]
            }
        }

        if (resolvedUserId.isBlank() || resolvedTcId.isBlank()) {
            val existing = firestoreManager.getRelationshipById(trimmedRelId).getOrNull()
            if (existing != null) {
                if (resolvedUserId.isBlank()) resolvedUserId = existing.userId
                if (resolvedTcId.isBlank()) resolvedTcId = existing.trustedContactId
            }
        }

        // Clean up both arrays
        if (resolvedUserId.isNotBlank() && resolvedTcId.isNotBlank()) {
            try { firestoreManager.removeTrustedContactIdFromUser(resolvedUserId, resolvedTcId) } catch (_: Exception) {}
            try { firestoreManager.removeConnectedUserIdFromTc(resolvedTcId, resolvedUserId) } catch (_: Exception) {}
        }

        // Update status to REVOKED
        val statusFields = mutableMapOf<String, Any?>(
            "status" to PermissionState.REVOKED.name,
            "revokedAt" to System.currentTimeMillis()
        )
        if (resolvedUserId.isNotBlank()) statusFields["userId"] = resolvedUserId
        if (resolvedTcId.isNotBlank()) statusFields["trustedContactId"] = resolvedTcId
        val updateResult = firestoreManager.updateRelationshipStatus(trimmedRelId, statusFields)

        // Hard-delete document from collection
        val deleteResult = firestoreManager.deleteRelationship(trimmedRelId)
        if (resolvedUserId.isNotBlank() && resolvedTcId.isNotBlank()) {
            val deterministicId = FirestoreManager.relationshipDocId(resolvedUserId, resolvedTcId)
            if (deterministicId != trimmedRelId) {
                try { firestoreManager.deleteRelationship(deterministicId) } catch (_: Exception) {}
            }
        }

        return if (deleteResult.isSuccess || updateResult.isSuccess) {
            Result.success(Unit)
        } else {
            deleteResult
        }
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
