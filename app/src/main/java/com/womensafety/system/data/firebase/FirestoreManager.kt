package com.womensafety.system.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import android.util.Log
import com.womensafety.system.data.model.EmergencyContact
import com.womensafety.system.data.model.Relationship
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.data.model.TrustedContactProfile
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.model.UserProfile
import com.womensafety.system.data.model.WeeklyUid
import com.womensafety.system.data.model.RiskEvent
import com.womensafety.system.data.model.JourneyRecord
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wraps all Cloud Firestore operations.
 * This is the ONLY class that directly imports Firestore SDK.
 * No Screen or ViewModel may access Firestore directly.
 *
 * IMPORTANT: All methods in this class are designed to comply with the
 * deployed Firestore security rules:
 * - No collection-wide list queries (users, trustedContacts, weeklyUids, relationships)
 * - Only single-document get operations
 * - Deterministic relationship document IDs: {userId}_{trustedContactId}
 * - Weekly UID document IDs = the UID value itself
 */
class FirestoreManager {

    val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        const val USERS_COLLECTION = "users"
        const val TRUSTED_CONTACTS_COLLECTION = "trustedContacts"
        const val WEEKLY_UIDS_COLLECTION = "weeklyUids"
        const val DEV_UID_MAPPINGS_COLLECTION = "devUidMappings"
        const val RELATIONSHIPS_COLLECTION = "relationships"
        const val USER_LOCATIONS_COLLECTION = "userLocations"
        const val LOCATIONS_SUBCOLLECTION = "locations"
        const val CHILD_LOCATIONS_COLLECTION = "child_locations"
        const val UID_LOCATIONS_COLLECTION = "uid_locations"
        const val SOS_EVENTS_COLLECTION = "sosEvents"
        const val RISK_EVENTS_COLLECTION = "riskEvents"
        const val JOURNEYS_COLLECTION = "journeys"

        private const val UID_VALIDITY_DAYS = 7

        /**
         * Generates the deterministic relationship document ID.
         * Format: {userId}_{trustedContactId}
         */
        fun relationshipDocId(userId: String, trustedContactId: String): String {
            return "${userId}_${trustedContactId}"
        }
    }

    // ─── User Profiles ───────────────────────────────────────────

    suspend fun createUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            val data = profile.toMap().filterValues { it != null }
            db.collection(USERS_COLLECTION)
                .document(profile.authUserId)
                .set(data, SetOptions.merge())
                .await()
            Log.d("FirestoreManager", "User profile successfully created/merged for ${profile.authUserId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "Failed to create user profile for ${profile.authUserId}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(authUserId: String): Result<UserProfile?> {
        return try {
            val doc = db.collection(USERS_COLLECTION)
                .document(authUserId)
                .get()
                .await()
            if (doc.exists()) {
                val data = doc.data ?: return Result.success(null)
                Result.success(UserProfile.fromMap(data))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(authUserId: String, fields: Map<String, Any?>): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION)
                .document(authUserId)
                .update(fields)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Adds a trusted contact ID to the user's trustedContactIds array.
     * Uses FieldValue.arrayUnion for atomic append.
     */
    suspend fun addTrustedContactIdToUser(userId: String, tcId: String): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION)
                .document(userId)
                .update("trustedContactIds", FieldValue.arrayUnion(tcId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Removes a trusted contact ID from the user's trustedContactIds array.
     */
    suspend fun removeTrustedContactIdFromUser(userId: String, tcId: String): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION)
                .document(userId)
                .update("trustedContactIds", FieldValue.arrayRemove(tcId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserSetupPreferences(userId: String, updates: Map<String, Any?>): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION)
                .document(userId)
                .set(updates, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEmergencyContactsForUser(userId: String): Result<List<EmergencyContact>> {
        return try {
            val userResult = getUserProfile(userId)
            val contacts = userResult.getOrNull()?.emergencyContacts ?: emptyList()
            Result.success(contacts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addEmergencyContactToUser(userId: String, contact: EmergencyContact): Result<Unit> {
        return try {
            val currentContacts = getEmergencyContactsForUser(userId).getOrNull() ?: emptyList()
            val updatedContacts = currentContacts.map {
                if (contact.isPrimary) it.copy(isPrimary = false) else it
            }.toMutableList().apply { add(contact) }
            db.collection(USERS_COLLECTION)
                .document(userId)
                .update("emergencyContacts", updatedContacts.map { it.toMap() })
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEmergencyContactInUser(
        userId: String,
        contactId: String,
        updates: Map<String, Any?>
    ): Result<Unit> {
        return try {
            val currentContacts = getEmergencyContactsForUser(userId).getOrNull() ?: emptyList()
            val willBePrimary = updates["isPrimary"] as? Boolean ?: false
            val updatedContacts = currentContacts.map { contact ->
                if (contact.id == contactId) {
                    val newName = updates["name"] as? String ?: contact.name
                    val newPhone = updates["phone"] as? String ?: contact.phone
                    val newIsPrimary = updates["isPrimary"] as? Boolean ?: contact.isPrimary
                    val newEnableEscalation = updates["enableEscalation"] as? Boolean ?: contact.enableEscalation
                    contact.copy(
                        name = newName,
                        phone = newPhone,
                        isPrimary = newIsPrimary,
                        enableEscalation = newEnableEscalation
                    )
                } else {
                    if (willBePrimary) {
                        contact.copy(isPrimary = false)
                    } else {
                        contact
                    }
                }
            }
            db.collection(USERS_COLLECTION)
                .document(userId)
                .update("emergencyContacts", updatedContacts.map { it.toMap() })
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEmergencyContactFromUser(userId: String, contactId: String): Result<Unit> {
        return try {
            val currentContacts = getEmergencyContactsForUser(userId).getOrNull() ?: emptyList()
            val updatedContacts = currentContacts.filterNot { it.id == contactId }
            db.collection(USERS_COLLECTION)
                .document(userId)
                .update("emergencyContacts", updatedContacts.map { it.toMap() })
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Trusted Contact Profiles ────────────────────────────────

    suspend fun createTrustedContactProfile(profile: TrustedContactProfile): Result<Unit> {
        return try {
            db.collection(TRUSTED_CONTACTS_COLLECTION)
                .document(profile.authUserId)
                .set(profile.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTrustedContactProfile(authUserId: String): Result<TrustedContactProfile?> {
        return try {
            val doc = db.collection(TRUSTED_CONTACTS_COLLECTION)
                .document(authUserId)
                .get()
                .await()
            if (doc.exists()) {
                val data = doc.data ?: return Result.success(null)
                Result.success(TrustedContactProfile.fromMap(data))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Adds a user ID to the TC's connectedUserIds array.
     * Uses FieldValue.arrayUnion for atomic append.
     */
    suspend fun addConnectedUserIdToTc(tcId: String, userId: String): Result<Unit> {
        return try {
            db.collection(TRUSTED_CONTACTS_COLLECTION)
                .document(tcId)
                .update("connectedUserIds", FieldValue.arrayUnion(userId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Removes a user ID from the TC's connectedUserIds array.
     * Uses FieldValue.arrayRemove for atomic removal.
     */
    suspend fun removeConnectedUserIdFromTc(tcId: String, userId: String): Result<Unit> {
        return try {
            db.collection(TRUSTED_CONTACTS_COLLECTION)
                .document(tcId)
                .update("connectedUserIds", FieldValue.arrayRemove(userId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Weekly UIDs (READ-ONLY from client in production) ─────

    /**
     * Generates a 12-digit UID string in YYYYMMDDXXXX format using cryptographically secure random numbers.
     * Used for dev/demo mode when Cloud Functions are not deployed.
     */
    private fun generateUidString(): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val year = cal.get(java.util.Calendar.YEAR).toString()
        val month = (cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val secureRandom = java.security.SecureRandom()
        val random = secureRandom.nextInt(10000).toString().padStart(4, '0')
        return "$year$month$day$random"
    }

    /**
     * Generates a UID on the client and saves it to devUidMappings.
     * Also updates the user's profile with currentWeeklyUid.
     *
     * SECURITY: devUidMappings rules enforce:
     * - userId == request.auth.uid (can only write own mapping)
     * - uid == document ID (can't fake the UID value)
     * - No listing (lookup by known UID only)
     *
     * This is the Spark-plan fallback for local development. On Blaze/production,
     * Cloud Functions write to weeklyUids instead.
     */
    suspend fun generateAndSaveDevUid(userId: String, userName: String = ""): Result<WeeklyUid> {
        return try {
            val uid = generateUidString()
            val now = System.currentTimeMillis()
            val expiresAt = now + UID_VALIDITY_DAYS * 24L * 60 * 60 * 1000

            val weeklyUid = WeeklyUid(
                userId = userId,
                uid = uid,
                userName = userName,
                generatedAt = now,
                expiresAt = expiresAt,
                active = true
            )

            val uidData = mapOf(
                "userId" to userId,
                "uid" to uid,
                "userName" to userName,
                "generatedAt" to now,
                "expiresAt" to expiresAt,
                "active" to true
            )

            // Write to devUidMappings/{uid}
            db.collection(DEV_UID_MAPPINGS_COLLECTION)
                .document(uid)
                .set(uidData)
                .await()

            // Update user profile with the current UID pointer
            db.collection(USERS_COLLECTION)
                .document(userId)
                .update("currentWeeklyUid", uid)
                .await()

            Result.success(weeklyUid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads the current active UID for a given user.
     *
     * Strategy:
     * 1. Read user profile to get currentWeeklyUid pointer.
     * 2. Check weeklyUids (authoritative production collection) FIRST.
     * 3. Fall back to devUidMappings (Spark plan dev collection) only if not in weeklyUids.
     * 4. Enforce expiration: returns null if the record has expired or is inactive.
     */
    suspend fun getActiveUidForUser(userId: String): Result<WeeklyUid?> {
        return try {
            val profileDoc = db.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (!profileDoc.exists()) return Result.success(null)

            val currentUid = profileDoc.data?.get("currentWeeklyUid") as? String
            val userName = profileDoc.data?.get("name") as? String ?: ""

            if (!currentUid.isNullOrBlank()) {
                // Step 1: Check weeklyUids
                try {
                    val uidDoc = db.collection(WEEKLY_UIDS_COLLECTION)
                        .document(currentUid)
                        .get()
                        .await()

                    if (uidDoc.exists()) {
                        val data = uidDoc.data
                        if (data != null) {
                            val weeklyUid = WeeklyUid.fromMap(data)
                            if (weeklyUid.isCurrentlyActive) {
                                return Result.success(weeklyUid)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirestoreManager", "weeklyUids check skipped: ${e.message}")
                }

                // Step 2: Fall back to devUidMappings
                try {
                    val devDoc = db.collection(DEV_UID_MAPPINGS_COLLECTION)
                        .document(currentUid)
                        .get()
                        .await()

                    if (devDoc.exists()) {
                        val data = devDoc.data
                        if (data != null) {
                            val weeklyUid = WeeklyUid.fromMap(data)
                            if (weeklyUid.isCurrentlyActive) {
                                return Result.success(weeklyUid)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirestoreManager", "devUidMappings check skipped: ${e.message}")
                }

                // Step 3: UID is already assigned in user profile; create and persist mapping
                val now = System.currentTimeMillis()
                val weeklyUid = WeeklyUid(
                    userId = userId,
                    uid = currentUid,
                    userName = userName,
                    generatedAt = now,
                    expiresAt = now + UID_VALIDITY_DAYS * 24L * 60 * 60 * 1000,
                    active = true
                )

                try {
                    db.collection(DEV_UID_MAPPINGS_COLLECTION)
                        .document(currentUid)
                        .set(weeklyUid.toMap())
                } catch (e: Exception) {
                    Log.w("FirestoreManager", "Persisting devUidMapping skipped: ${e.message}")
                }

                return Result.success(weeklyUid)
            }

            // Step 4: No UID in profile yet; generate and save immediately
            generateAndSaveDevUid(userId, userName)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getActiveUidForUser failed", e)
            Result.failure(e)
        }
    }

    /**
     * Requests on-demand UID verification or rotation from the authoritative backend Cloud Function.
     * Recovers from missing initial UIDs or delayed scheduled rotation without client-side generation.
     */
    suspend fun requestBackendUidRefresh(): Result<WeeklyUid?> {
        return try {
            val functions = FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("ensureActiveUidFunction")
                .call()
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any?>
            if (data != null && data["success"] == true) {
                val uid = data["uid"] as? String ?: ""
                val expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L
                val generatedAt = (data["generatedAt"] as? Number)?.toLong() ?: 0L
                val authUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val weeklyUid = WeeklyUid(
                    userId = authUserId,
                    uid = uid,
                    userName = data["userName"] as? String ?: "",
                    generatedAt = generatedAt,
                    expiresAt = expiresAt,
                    active = true
                )
                Result.success(weeklyUid)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Looks up a user by their weekly UID.
     * Used by Trusted Contacts when connecting to a User.
     *
     * Authoritative Strategy:
     * 1. Validates strict 12-digit format (YYYYMMDDXXXX).
     * 2. Checks weeklyUids (production collection) FIRST.
     * 3. Falls back to devUidMappings (Spark collection) only if not in weeklyUids.
     * 4. Enforces expiration and transition grace periods (!isExpired && isCurrentlyActive).
     */
    suspend fun findUserByActiveUid(uid: String): Result<WeeklyUid?> {
        return try {
            val trimmedUid = uid.trim()

            // Validate format: exactly 12 numeric digits
            if (!trimmedUid.matches(Regex("^\\d{12}$"))) {
                Log.d("UID_DEBUG", "Invalid UID format entered: $trimmedUid")
                return Result.success(null)
            }

            val authUser = FirebaseAuth.getInstance().currentUser
            if (authUser == null) {
                Log.e("UID_DEBUG", "NO AUTHENTICATED FIREBASE USER")
                return Result.failure(Exception("User is not authenticated with Firebase"))
            }

            // ============================================================
            // 1. Authoritative: Check production weeklyUids collection FIRST
            // ============================================================
            try {
                val doc = withTimeoutOrNull(3000L) {
                    db.collection(WEEKLY_UIDS_COLLECTION)
                        .document(trimmedUid)
                        .get()
                        .await()
                }
                if (doc != null && doc.exists()) {
                    val data = doc.data
                    if (data != null) {
                        val weeklyUid = WeeklyUid.fromMap(data)
                        if (weeklyUid.isCurrentlyActive || (weeklyUid.userId.isNotBlank() && weeklyUid.active)) {
                            Log.d("UID_DEBUG", "SUCCESS: Active UID found in weeklyUids: ${weeklyUid.userId}")
                            return Result.success(weeklyUid)
                        } else {
                            Log.d("UID_DEBUG", "UID found in weeklyUids but is expired or inactive")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("UID_DEBUG", "weeklyUids check skipped/failed: ${e.message}")
            }

            // ============================================================
            // 2. Fall back to devUidMappings (Spark development mode)
            // ============================================================
            try {
                val devDoc = withTimeoutOrNull(3500L) {
                    db.collection(DEV_UID_MAPPINGS_COLLECTION)
                        .document(trimmedUid)
                        .get()
                        .await()
                }
                if (devDoc != null && devDoc.exists()) {
                    val data = devDoc.data
                    if (data != null) {
                        val weeklyUid = WeeklyUid.fromMap(data)
                        if (weeklyUid.isCurrentlyActive || (weeklyUid.userId.isNotBlank() && weeklyUid.active)) {
                            Log.d("UID_DEBUG", "SUCCESS: Active UID found in devUidMappings: ${weeklyUid.userId}")
                            return Result.success(weeklyUid)
                        } else {
                            Log.d("UID_DEBUG", "UID found in devUidMappings but expired/inactive: ${weeklyUid.expiresAt}")
                            // Return the mapping if valid user id is attached
                            if (weeklyUid.userId.isNotBlank()) {
                                return Result.success(weeklyUid)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("UID_DEBUG", "devUidMappings check skipped/failed: ${e.message}")
            }

            // ============================================================
            // 3. Fall back to child_locations (Live location UID collection)
            // ============================================================
            try {
                val locDoc = withTimeoutOrNull(3500L) {
                    db.collection(CHILD_LOCATIONS_COLLECTION)
                        .document(trimmedUid)
                        .get()
                        .await()
                }
                if (locDoc != null && locDoc.exists()) {
                    val data = locDoc.data
                    val userId = data?.get("userId") as? String ?: ""
                    val userName = data?.get("name") as? String ?: data?.get("childName") as? String ?: ""
                    if (userId.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        val weeklyUid = WeeklyUid(
                            userId = userId,
                            uid = trimmedUid,
                            userName = userName,
                            generatedAt = now,
                            expiresAt = now + UID_VALIDITY_DAYS * 24L * 60 * 60 * 1000,
                            active = true
                        )
                        Log.d("UID_DEBUG", "SUCCESS: Active UID resolved from child_locations: $userId")
                        return Result.success(weeklyUid)
                    }
                }
            } catch (e: Exception) {
                Log.w("UID_DEBUG", "child_locations check skipped/failed: ${e.message}")
            }

            Log.d("UID_DEBUG", "UID NOT FOUND in any collection: $trimmedUid")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("UID_DEBUG", "UID LOOKUP FAILED with exception", e)
            Result.failure(e)
        }
    }


    // ─── Relationships ───────────────────────────────────────────

    /**
     * Creates a relationship with a deterministic document ID.
     * Format: {userId}_{trustedContactId}
     *
     * Uses set() instead of add() because the rules enforce:
     * relationshipId == request.resource.data.userId + "_" + request.resource.data.trustedContactId
     */
    suspend fun createRelationship(relationship: Relationship): Result<String> {
        return try {
            val docId = relationshipDocId(relationship.userId, relationship.trustedContactId)
            db.collection(RELATIONSHIPS_COLLECTION)
                .document(docId)
                .set(relationship.toMap())
                .await()
            Result.success(docId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates a relationship status.
     *
     * IMPORTANT: Firestore rules require that the update data includes userId and
     * trustedContactId (they validate request.resource.data contains these).
     * Uses set-with-merge to ensure the full document satisfies the rules.
     */
    suspend fun updateRelationshipStatus(
        relationshipId: String,
        fields: Map<String, Any?>
    ): Result<Unit> {
        val trimmedId = relationshipId.trim()
        if (trimmedId.isBlank()) return Result.failure(IllegalArgumentException("Empty relationship ID"))
        return try {
            db.collection(RELATIONSHIPS_COLLECTION)
                .document(trimmedId)
                .set(fields, SetOptions.merge())
                .await()
            Log.d("FirestoreManager", "updateRelationshipStatus succeeded via set(merge) for $trimmedId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirestoreManager", "set(merge) failed for $trimmedId: ${e.message}. Trying update()...")
            try {
                db.collection(RELATIONSHIPS_COLLECTION)
                    .document(trimmedId)
                    .update(fields)
                    .await()
                Log.d("FirestoreManager", "update() succeeded for $trimmedId")
                Result.success(Unit)
            } catch (e2: Exception) {
                Log.e("FirestoreManager", "updateRelationshipStatus completely failed for $trimmedId: ${e2.message}", e2)
                Result.failure(e2)
            }
        }
    }

    /**
     * Deletes a relationship document.
     */
    suspend fun deleteRelationship(relationshipId: String): Result<Unit> {
        val trimmedId = relationshipId.trim()
        if (trimmedId.isBlank()) return Result.failure(IllegalArgumentException("Empty relationship ID"))
        return try {
            db.collection(RELATIONSHIPS_COLLECTION)
                .document(trimmedId)
                .delete()
                .await()
            Log.d("FirestoreManager", "deleteRelationship succeeded for doc: $trimmedId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "deleteRelationship failed for doc $trimmedId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves a single relationship by document ID.
     */
    suspend fun getRelationshipById(relationshipId: String): Result<Relationship?> {
        val trimmedId = relationshipId.trim()
        if (trimmedId.isBlank()) return Result.success(null)
        return try {
            val doc = db.collection(RELATIONSHIPS_COLLECTION)
                .document(trimmedId)
                .get()
                .await()
            if (doc.exists()) {
                val data = doc.data ?: return Result.success(null)
                Result.success(Relationship.fromMap(doc.id, data))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.w("FirestoreManager", "getRelationshipById failed for $trimmedId: ${e.message}")
            Result.failure(e)
        }
    }
    /**
     * Gets all relationships for a User.
     *
     * Uses a Firestore query: .where("userId", "==", userId)
     *
     * This works because the Firestore rules use `allow read` (which includes `list`),
     * and the query constraint guarantees all results satisfy:
     *   resource.data.userId == request.auth.uid
     */
    suspend fun getRelationshipsForUser(userId: String): Result<List<Relationship>> {
        return try {
            val snapshot = db.collection(RELATIONSHIPS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val relationships = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Relationship.fromMap(doc.id, it) }
            }

            Result.success(relationships)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets all relationships for a Trusted Contact.
     *
     * Uses a Firestore query: .where("trustedContactId", "==", trustedContactId)
     *
     * This works because the Firestore rules use `allow read` (which includes `list`),
     * and the query constraint guarantees all results satisfy:
     *   resource.data.trustedContactId == request.auth.uid
     */
    suspend fun getRelationshipsForTrustedContact(trustedContactId: String): Result<List<Relationship>> {
        return try {
            val snapshot = db.collection(RELATIONSHIPS_COLLECTION)
                .whereEqualTo("trustedContactId", trustedContactId)
                .get()
                .await()

            val relationships = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Relationship.fromMap(doc.id, it) }
            }

            Result.success(relationships)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time listener for a User's relationships using callbackFlow.
     * Fires immediately whenever a Trusted Contact sends, modifies, or deletes a request.
     */
    fun observeRelationshipsForUser(userId: String): Flow<List<Relationship>> = callbackFlow {
        val listener = db.collection(RELATIONSHIPS_COLLECTION)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreManager", "observeRelationshipsForUser error: ${error.message}")
                    return@addSnapshotListener
                }
                val relationships = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Relationship.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(relationships)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Real-time listener for a Trusted Contact's relationships using callbackFlow.
     * Fires immediately when monitored user approves, denies, or deletes a relationship.
     */
    fun observeRelationshipsForTrustedContact(trustedContactId: String): Flow<List<Relationship>> = callbackFlow {
        val listener = db.collection(RELATIONSHIPS_COLLECTION)
            .whereEqualTo("trustedContactId", trustedContactId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreManager", "observeRelationshipsForTrustedContact error: ${error.message}")
                    return@addSnapshotListener
                }
                val relationships = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Relationship.fromMap(doc.id, it) }
                } ?: emptyList()
                trySend(relationships)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Checks if a relationship already exists between a User and TC.
     * Uses a direct document get with the deterministic ID.
     *
     * IMPORTANT: If the document does NOT exist, Firestore security rules that
     * reference resource.data will deny the read (resource is null when doc
     * doesn't exist). This is expected Firestore behavior. We catch the
     * PERMISSION_DENIED error and treat it as "no existing relationship".
     */
    suspend fun findExistingRelationship(
        userId: String,
        trustedContactId: String
    ): Result<Relationship?> {
        return try {
            val docId = relationshipDocId(userId, trustedContactId)
            val doc = db.collection(RELATIONSHIPS_COLLECTION)
                .document(docId)
                .get()
                .await()
            if (doc.exists()) {
                val data = doc.data ?: return Result.success(null)
                Result.success(Relationship.fromMap(doc.id, data))
            } else {
                Result.success(null)
            }
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            // When a document doesn't exist, Firestore rules that check resource.data
            // will deny the read because resource is null. This is NOT a real permission
            // error — it means the relationship doesn't exist yet.
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.d("FirestoreManager", "findExistingRelationship: doc not found (PERMISSION_DENIED on non-existent doc). Treating as no existing relationship.")
                Result.success(null)
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Location Tracking ──────────────────────────────────────

    /**
     * Saves a user's location point to their location subcollection.
     * Path: userLocations/{userId}/locations/{locationId}
     */
    suspend fun saveUserLocation(userId: String, location: UserLocation): Result<Unit> {
        return try {
            val docId = if (location.id.isNotBlank()) location.id else location.timestamp.toString()
            val locationToSave = location.copy(id = docId, userId = userId)

            db.collection(USER_LOCATIONS_COLLECTION)
                .document(userId)
                .collection(LOCATIONS_SUBCOLLECTION)
                .document(docId)
                .set(locationToSave.toMap())
                .await()

            // Also mirror latest coordinates to child_locations for instantaneous trusted-contact & web access
            val data = locationToSave.toMap().toMutableMap().apply {
                put("lastUpdated", FieldValue.serverTimestamp())
                put("status", "ONLINE")
                put("lat", location.latitude)
                put("lng", location.longitude)
                put("name", location.userName)
                put("childName", location.userName)
            }
            try {
                db.collection(CHILD_LOCATIONS_COLLECTION)
                    .document(userId)
                    .set(data, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w("FirestoreManager", "Mirroring to child_locations/$userId skipped: ${e.message}")
            }
            if (location.uid.isNotBlank() && location.uid != userId) {
                try {
                    db.collection(CHILD_LOCATIONS_COLLECTION)
                        .document(location.uid)
                        .set(data, SetOptions.merge())
                        .await()
                } catch (e: Exception) {
                    Log.w("FirestoreManager", "Mirroring to child_locations/${location.uid} skipped: ${e.message}")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "saveUserLocation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves the latest known location point for a user.
     */
    suspend fun getLatestUserLocation(userId: String): Result<UserLocation?> {
        return try {
            val snapshot = db.collection(USER_LOCATIONS_COLLECTION)
                .document(userId)
                .collection(LOCATIONS_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val doc = snapshot.documents.first()
                val loc = doc.data?.let { UserLocation.fromMap(doc.id, it) }
                Result.success(loc)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getLatestUserLocation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves recent location history points for a user.
     */
    suspend fun getUserLocationHistory(userId: String, limit: Int = 50): Result<List<UserLocation>> {
        return try {
            val snapshot = db.collection(USER_LOCATIONS_COLLECTION)
                .document(userId)
                .collection(LOCATIONS_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val locations = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { UserLocation.fromMap(doc.id, it) }
            }
            Result.success(locations)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getUserLocationHistory failed", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time listener for a user's latest location using callbackFlow.
     */
    fun listenToUserLocation(userId: String): Flow<UserLocation?> = callbackFlow {
        val listener = db.collection(USER_LOCATIONS_COLLECTION)
            .document(userId)
            .collection(LOCATIONS_SUBCOLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreManager", "listenToUserLocation error", error)
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    val loc = doc.data?.let { UserLocation.fromMap(doc.id, it) }
                    trySend(loc)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Saves a user's location to the dedicated child_locations collection indexed by UID.
     * Updated every 10 seconds so the tracking website can track children by UID directly.
     * Document Path: child_locations/{uid}
     * History Path: child_locations/{uid}/history/{timestamp}
     * Also mirrors to uid_locations/{uid} for alias compatibility.
     */
    suspend fun saveUidLocation(uid: String, location: UserLocation): Result<Unit> {
        if (uid.isBlank()) return Result.failure(IllegalArgumentException("UID cannot be blank"))
        return try {
            val docId = uid.trim()
            val locationToSave = location.copy(uid = docId)
            val data = locationToSave.toMap().toMutableMap().apply {
                put("lastUpdated", FieldValue.serverTimestamp())
                put("status", "ONLINE")
                put("lat", location.latitude)
                put("lng", location.longitude)
                put("name", location.userName)
                put("childName", location.userName)
                val currentBattery = com.womensafety.system.manager.BatteryMonitoringManager.getCurrentBatteryPercentage()
                val isCharging = com.womensafety.system.manager.BatteryMonitoringManager.isDeviceCharging()
                put("batteryLevel", currentBattery)
                put("isCharging", isCharging)
                put("networkStatus", "ONLINE")
            }

            // 1. Primary dedicated collection for website tracking: child_locations/{uid}
            db.collection(CHILD_LOCATIONS_COLLECTION)
                .document(docId)
                .set(data, SetOptions.merge())
                .await()

            // Also record breadcrumb history for path replay: child_locations/{uid}/history/{timestamp}
            val historyId = location.timestamp.toString()
            db.collection(CHILD_LOCATIONS_COLLECTION)
                .document(docId)
                .collection("history")
                .document(historyId)
                .set(data)
                .await()

            // 2. Also mirror to uid_locations/{uid} for alias compatibility
            db.collection(UID_LOCATIONS_COLLECTION)
                .document(docId)
                .set(data, SetOptions.merge())
                .await()

            // Also mirror history to uid_locations/{uid}/history/{timestamp}
            db.collection(UID_LOCATIONS_COLLECTION)
                .document(docId)
                .collection("history")
                .document(historyId)
                .set(data)
                .await()

            // Also mirror under userId so lookups by auth userId find it directly
            if (location.userId.isNotBlank() && location.userId != docId) {
                try {
                    db.collection(CHILD_LOCATIONS_COLLECTION)
                        .document(location.userId)
                        .set(data, SetOptions.merge())
                        .await()
                } catch (e: Exception) {
                    Log.w("FirestoreManager", "Mirroring child location under userId skipped: ${e.message}")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "saveUidLocation failed for uid: $uid", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves the latest known location by UID or userId from child_locations.
     */
    suspend fun getLatestUidLocation(uid: String): Result<UserLocation?> {
        if (uid.isBlank()) return Result.success(null)
        val target = uid.trim()
        return try {
            // 1. Check child_locations/{target}
            val snapshot = db.collection(CHILD_LOCATIONS_COLLECTION)
                .document(target)
                .get()
                .await()

            if (snapshot.exists()) {
                val data = snapshot.data
                val loc = data?.let { UserLocation.fromMap(snapshot.id, it) }
                if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                    return Result.success(loc)
                }
            }

            // 2. Check child_locations where userId == target
            val querySnap = db.collection(CHILD_LOCATIONS_COLLECTION)
                .whereEqualTo("userId", target)
                .limit(1)
                .get()
                .await()

            if (!querySnap.isEmpty) {
                val doc = querySnap.documents.first()
                val loc = doc.data?.let { UserLocation.fromMap(doc.id, it) }
                if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                    return Result.success(loc)
                }
            }

            // 3. Check uid_locations/{target}
            val uidSnap = db.collection(UID_LOCATIONS_COLLECTION)
                .document(target)
                .get()
                .await()

            if (uidSnap.exists()) {
                val data = uidSnap.data
                val loc = data?.let { UserLocation.fromMap(uidSnap.id, it) }
                if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                    return Result.success(loc)
                }
            }

            // 4. Fallback: check userLocations/{target}/locations subcollection
            val userLocSnap = db.collection(USER_LOCATIONS_COLLECTION)
                .document(target)
                .collection(LOCATIONS_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!userLocSnap.isEmpty) {
                val doc = userLocSnap.documents.first()
                val loc = doc.data?.let { UserLocation.fromMap(doc.id, it) }
                if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                    return Result.success(loc)
                }
            }

            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getLatestUidLocation failed for uid: $uid", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time listener for child's live location by UID.
     * Directly streams updates as they occur every 10 seconds.
     */
    fun listenToUidLocation(uid: String): Flow<UserLocation?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val docRef = db.collection(CHILD_LOCATIONS_COLLECTION).document(uid.trim())
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirestoreManager", "listenToUidLocation error for $uid", error)
                trySend(null)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data
                if (data != null) {
                    val loc = UserLocation.fromMap(snapshot.id, data)
                    trySend(loc)
                    return@addSnapshotListener
                }
            }
            trySend(null)
        }

        awaitClose { listener.remove() }
    }

    /**
     * Multi-path real-time listener that observes location by UID (from child_locations/uid_locations)
     * or by auth userId (from child_locations query, userLocations subcollection, and weeklyUid resolution).
     */
    fun listenToLocationByUserIdOrUid(identifier: String): Flow<UserLocation?> = callbackFlow {
        if (identifier.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val target = identifier.trim()
        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

        // 1. Direct child_locations/{target}
        val childDocRef = db.collection(CHILD_LOCATIONS_COLLECTION).document(target)
        listeners.add(
            childDocRef.addSnapshotListener { childSnap, error ->
                if (error == null && childSnap != null && childSnap.exists()) {
                    val data = childSnap.data
                    if (data != null) {
                        val loc = UserLocation.fromMap(childSnap.id, data)
                        if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                            trySend(loc)
                        }
                    }
                }
            }
        )

        // 2. Query child_locations where userId == target (when target is auth UID)
        listeners.add(
            db.collection(CHILD_LOCATIONS_COLLECTION)
                .whereEqualTo("userId", target)
                .limit(1)
                .addSnapshotListener { querySnap, error ->
                    if (error == null && querySnap != null && !querySnap.isEmpty) {
                        val doc = querySnap.documents.first()
                        val data = doc.data
                        if (data != null) {
                            val loc = UserLocation.fromMap(doc.id, data)
                            if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                                trySend(loc)
                            }
                        }
                    }
                }
        )

        // 3. Direct uid_locations/{target}
        listeners.add(
            db.collection(UID_LOCATIONS_COLLECTION).document(target)
                .addSnapshotListener { uidSnap, error ->
                    if (error == null && uidSnap != null && uidSnap.exists()) {
                        val data = uidSnap.data
                        if (data != null) {
                            val loc = UserLocation.fromMap(uidSnap.id, data)
                            if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                                trySend(loc)
                            }
                        }
                    }
                }
        )

        // 4. Query uid_locations where userId == target
        listeners.add(
            db.collection(UID_LOCATIONS_COLLECTION)
                .whereEqualTo("userId", target)
                .limit(1)
                .addSnapshotListener { querySnap, error ->
                    if (error == null && querySnap != null && !querySnap.isEmpty) {
                        val doc = querySnap.documents.first()
                        val data = doc.data
                        if (data != null) {
                            val loc = UserLocation.fromMap(doc.id, data)
                            if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                                trySend(loc)
                            }
                        }
                    }
                }
        )

        // 5. Also listen to userLocations/{target}/locations (subcollection)
        listeners.add(
            db.collection(USER_LOCATIONS_COLLECTION)
                .document(target)
                .collection(LOCATIONS_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { userSnap, error ->
                    if (error == null && userSnap != null && !userSnap.isEmpty) {
                        val doc = userSnap.documents.first()
                        val loc = doc.data?.let { UserLocation.fromMap(doc.id, it) }
                        if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                            trySend(loc)
                        }
                    }
                }
        )

        // 6. Look up currentWeeklyUid from users collection (if target is auth UID)
        var weeklyListener: com.google.firebase.firestore.ListenerRegistration? = null
        val userProfileListener = db.collection(USERS_COLLECTION).document(target)
            .addSnapshotListener { userDoc, error ->
                if (error == null && userDoc != null && userDoc.exists()) {
                    val weeklyUid = userDoc.getString("currentWeeklyUid")
                    if (!weeklyUid.isNullOrBlank() && weeklyUid != target) {
                        weeklyListener?.remove()
                        weeklyListener = db.collection(CHILD_LOCATIONS_COLLECTION)
                            .document(weeklyUid.trim())
                            .addSnapshotListener { snap, err ->
                                if (err == null && snap != null && snap.exists()) {
                                    val data = snap.data
                                    if (data != null) {
                                        val loc = UserLocation.fromMap(snap.id, data)
                                        if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                                            trySend(loc)
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        listeners.add(userProfileListener)

        awaitClose {
            weeklyListener?.remove()
            listeners.forEach { it.remove() }
        }
    }

    /**
     * Deletes location points older than a given timestamp.
     */
    suspend fun deleteOldLocations(userId: String, beforeTimestamp: Long): Result<Unit> {
        return try {
            val snapshot = db.collection(USER_LOCATIONS_COLLECTION)
                .document(userId)
                .collection(LOCATIONS_SUBCOLLECTION)
                .whereLessThan("timestamp", beforeTimestamp)
                .get()
                .await()

            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "deleteOldLocations failed", e)
            Result.failure(e)
        }
    }

    // ─── SOS Events ─────────────────────────────────────────────

    /**
     * Creates a new emergency SOS event document.
     */
    suspend fun createSOSEvent(event: SOSEvent): Result<Unit> {
        return try {
            db.collection(SOS_EVENTS_COLLECTION)
                .document(event.id)
                .set(event.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "createSOSEvent failed", e)
            Result.failure(e)
        }
    }

    /**
     * Updates an SOS event's resolution status and timestamp.
     */
    suspend fun updateSOSEventStatus(
        eventId: String,
        status: SOSStatus,
        resolvedAt: Long = System.currentTimeMillis()
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "status" to status.name,
                "resolvedAt" to resolvedAt
            )
            withTimeoutOrNull(3000L) {
                db.collection(SOS_EVENTS_COLLECTION)
                    .document(eventId)
                    .set(updates, SetOptions.merge())
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "updateSOSEventStatus failed", e)
            Result.failure(e)
        }
    }

    /**
     * Updates notification, SMS, and auto-call delivery statistics for an SOS event.
     */
    suspend fun updateSOSDeliveryStats(
        eventId: String,
        notificationsSent: Int,
        smsSent: Int,
        callMade: Boolean,
        fcmTokens: List<String>
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "notificationsSent" to notificationsSent,
                "smsSent" to smsSent,
                "callMade" to callMade,
                "fcmTokensSent" to fcmTokens
            )
            db.collection(SOS_EVENTS_COLLECTION)
                .document(eventId)
                .set(updates, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "updateSOSDeliveryStats failed", e)
            Result.failure(e)
        }
    }

    /**
     * Gets the currently active SOS event for a user if one exists.
     */
    suspend fun getActiveUserSOSEvent(userId: String): Result<SOSEvent?> {
        return try {
            val snapshot = db.collection(SOS_EVENTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", SOSStatus.ACTIVE.name)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val doc = snapshot.documents.first()
                val event = doc.data?.let { SOSEvent.fromMap(doc.id, it) }
                Result.success(event)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getActiveUserSOSEvent failed", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves past SOS events for a user in reverse chronological order.
     */
    suspend fun getUserSOSHistory(userId: String, limit: Int = 50): Result<List<SOSEvent>> {
        return try {
            val snapshot = db.collection(SOS_EVENTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("triggeredAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val events = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { SOSEvent.fromMap(doc.id, it) }
            }
            Result.success(events)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getUserSOSHistory failed", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves a single SOS event by its ID.
     */
    suspend fun getSOSEventById(eventId: String): Result<SOSEvent?> {
        return try {
            val doc = db.collection(SOS_EVENTS_COLLECTION).document(eventId).get().await()
            val event = if (doc.exists() && doc.data != null) {
                SOSEvent.fromMap(doc.id, doc.data!!)
            } else {
                null
            }
            Result.success(event)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getSOSEventById failed", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time listener for active SOS events of a user.
     */
    fun listenToUserSOSEvents(userId: String): Flow<SOSEvent?> = callbackFlow {
        val listener = db.collection(SOS_EVENTS_COLLECTION)
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", SOSStatus.ACTIVE.name)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreManager", "listenToUserSOSEvents error", error)
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    val event = doc.data?.let { SOSEvent.fromMap(doc.id, it) }
                    trySend(event)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    // ─── FCM Token Management ───────────────────────────────────

    /**
     * Updates the FCM device push token for a user or trusted contact.
     */
    suspend fun saveFCMToken(userId: String, token: String, accountType: String): Result<Unit> {
        return try {
            val collection = if (accountType == "TRUSTED_CONTACT") {
                TRUSTED_CONTACTS_COLLECTION
            } else {
                USERS_COLLECTION
            }

            db.collection(collection)
                .document(userId)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .await()
            Log.d("FirestoreManager", "Saved FCM token for $accountType ($userId)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "saveFCMToken failed for $accountType ($userId)", e)
            Result.failure(e)
        }
    }

    suspend fun createRiskEvent(event: RiskEvent): Result<Unit> {
        return try {
            db.collection(RISK_EVENTS_COLLECTION)
                .document(event.id)
                .set(event.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "createRiskEvent failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateRiskEventStatus(
        eventId: String,
        status: String
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "status" to status
            )
            db.collection(RISK_EVENTS_COLLECTION)
                .document(eventId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "updateRiskEventStatus failed", e)
            Result.failure(e)
        }
    }

    suspend fun createJourneyRecord(record: JourneyRecord): Result<Unit> {
        return try {
            db.collection(JOURNEYS_COLLECTION)
                .document(record.id)
                .set(record.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "createJourneyRecord failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserJourneys(userId: String): Result<List<JourneyRecord>> {
        return try {
            val snapshot = db.collection(JOURNEYS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .get()
                .await()
            val journeys = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { JourneyRecord.fromMap(doc.id, it) }
            }
            Result.success(journeys)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getUserJourneys failed", e)
            Result.failure(e)
        }
    }

    suspend fun submitFeedback(
        userId: String,
        userName: String,
        feedbackMessage: String
    ): Result<Unit> {
        var anySuccess = false
        var lastError: Exception? = null

        val docId = java.util.UUID.randomUUID().toString()
        val feedbackData = mapOf(
            "id" to docId,
            "userId" to userId,
            "userName" to userName,
            "feedbackMessage" to feedbackMessage,
            "createdAt" to System.currentTimeMillis()
        )

        // 1. Write to "feedback" collection (singular)
        try {
            db.collection("feedback")
                .document(docId)
                .set(feedbackData)
                .await()
            anySuccess = true
            Log.i("FirestoreManager", "Successfully created doc in 'feedback' collection: $docId")
        } catch (e: Exception) {
            Log.e("FirestoreManager", "Failed writing to 'feedback' collection", e)
            lastError = e
        }

        // 2. Write to "feedbacks" collection (plural)
        try {
            db.collection("feedbacks")
                .document(docId)
                .set(feedbackData)
                .await()
            anySuccess = true
            Log.i("FirestoreManager", "Successfully created doc in 'feedbacks' collection: $docId")
        } catch (e: Exception) {
            Log.e("FirestoreManager", "Failed writing to 'feedbacks' collection", e)
            if (lastError == null) lastError = e
        }

        // 3. Also update lastFeedback in user profile document
        try {
            db.collection(USERS_COLLECTION)
                .document(userId)
                .update(
                    mapOf(
                        "lastFeedback" to feedbackMessage,
                        "lastFeedbackAt" to System.currentTimeMillis()
                    )
                )
                .await()
            anySuccess = true
        } catch (e: Exception) {
            Log.w("FirestoreManager", "User document lastFeedback notice: ${e.message}")
        }

        return if (anySuccess) {
            Result.success(Unit)
        } else {
            Result.failure(lastError ?: Exception("Unable to write feedback document to Firestore"))
        }
    }

    /**
     * Generic helper to save or merge data into a document.
     */
    suspend fun saveDocument(collection: String, documentId: String, data: Map<String, Any?>): Result<Unit> {
        return try {
            db.collection(collection)
                .document(documentId)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "saveDocument failed for $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Generic helper to retrieve a document as a Map.
     */
    suspend fun getDocument(collection: String, documentId: String): Result<Map<String, Any>?> {
        return try {
            val snapshot = db.collection(collection).document(documentId).get().await()
            if (snapshot.exists()) {
                Result.success(snapshot.data)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getDocument failed for $collection/$documentId", e)
            Result.failure(e)
        }
    }

    suspend fun setDocument(fullPath: String, data: Map<String, Any?>): Result<Unit> {
        return try {
            db.document(fullPath).set(data.filterValues { it != null }, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "setDocument failed for $fullPath", e)
            Result.failure(e)
        }
    }

    suspend fun updateDocument(fullPath: String, data: Map<String, Any?>): Result<Unit> {
        return try {
            db.document(fullPath).update(data.filterValues { it != null }).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "updateDocument failed for $fullPath", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(fullPath: String): Result<Unit> {
        return try {
            db.document(fullPath).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "deleteDocument failed for $fullPath", e)
            Result.failure(e)
        }
    }

    suspend fun getDocuments(collectionPath: String): List<Map<String, Any>> {
        return try {
            val snapshot = db.collection(collectionPath).get().await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getDocuments failed for $collectionPath", e)
            emptyList()
        }
    }

    suspend fun getLastLocation(userId: String): UserLocation? {
        return try {
            val snapshot = db.collection(USER_LOCATIONS_COLLECTION)
                .document(userId)
                .collection(LOCATIONS_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.toObject(UserLocation::class.java)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getLastLocation failed for $userId", e)
            null
        }
    }

    suspend fun isTrustedContact(userId: String, contactId: String): Boolean {
        return try {
            val userProfile = getUserProfile(userId).getOrNull()
            userProfile?.trustedContactIds?.contains(contactId) == true ||
                userProfile?.emergencyContacts?.any { it.id == contactId } == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getApprovedTrustedContacts(userId: String): List<TrustedContactProfile> {
        return try {
            val userProfile = getUserProfile(userId).getOrNull() ?: return emptyList()
            val contactIds = userProfile.trustedContactIds
            contactIds.mapNotNull { contactId ->
                getTrustedContactProfile(contactId).getOrNull()
            }
        } catch (e: Exception) {
            Log.e("FirestoreManager", "getApprovedTrustedContacts failed for $userId", e)
            emptyList()
        }
    }

    suspend fun addTrustedContact(currentUserId: String, contactUserId: String, contactName: String) {
        try {
            db.collection("trustedContacts")
                .document(currentUserId)
                .collection("contacts")
                .document(contactUserId)
                .set(mapOf(
                    "userId" to contactUserId,
                    "name" to contactName,
                    "addedAt" to FieldValue.serverTimestamp(),
                    "source" to "BLE_AUTO_DISCOVERY"
                ), SetOptions.merge())
                .await()
            Log.d("FirestoreManager", "Auto-added trusted contact: $contactUserId ($contactName)")
        } catch (e: Exception) {
            Log.e("FirestoreManager", "Error adding trusted contact: ${e.message}", e)
        }
    }
}

