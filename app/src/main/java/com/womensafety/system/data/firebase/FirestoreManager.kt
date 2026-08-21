package com.womensafety.system.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.util.Log
import com.womensafety.system.data.model.EmergencyContact
import com.womensafety.system.data.model.Relationship
import com.womensafety.system.data.model.TrustedContactProfile
import com.womensafety.system.data.model.UserProfile
import com.womensafety.system.data.model.WeeklyUid
import kotlinx.coroutines.tasks.await

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

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        const val USERS_COLLECTION = "users"
        const val TRUSTED_CONTACTS_COLLECTION = "trustedContacts"
        const val WEEKLY_UIDS_COLLECTION = "weeklyUids"
        const val DEV_UID_MAPPINGS_COLLECTION = "devUidMappings"
        const val RELATIONSHIPS_COLLECTION = "relationships"

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
            db.collection(USERS_COLLECTION)
                .document(profile.authUserId)
                .set(profile.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
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
            val updatedContacts = currentContacts + contact
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
            val updatedContacts = currentContacts.map { contact ->
                if (contact.id == contactId) {
                    val newName = updates["name"] as? String ?: contact.name
                    val newPhone = updates["phone"] as? String ?: contact.phone
                    contact.copy(name = newName, phone = newPhone)
                } else {
                    contact
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

    // ─── Weekly UIDs (READ-ONLY from client) ─────────────────────

    /**
     * Generates a 12-digit UID string in YYYYMMDDXXXX format.
     * Used for dev/demo mode when Cloud Functions are not deployed.
     */
    private fun generateUidString(): String {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR).toString()
        val month = (cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val random = (0..9999).random().toString().padStart(4, '0')
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
     * This is the Spark-plan fallback. On Blaze, Cloud Functions
     * write to weeklyUids instead.
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

            // Update user profile with the current UID
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
     * Strategy: Read the user's profile to get currentWeeklyUid,
     * then try devUidMappings first (Spark plan), fall back to weeklyUids (Blaze plan).
     */
    suspend fun getActiveUidForUser(userId: String): Result<WeeklyUid?> {
        return try {
            // Step 1: Read user profile to get currentWeeklyUid
            val profileDoc = db.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (!profileDoc.exists()) return Result.success(null)

            val currentUid = profileDoc.data?.get("currentWeeklyUid") as? String
            if (currentUid.isNullOrBlank()) return Result.success(null)

            // Step 2: Try devUidMappings first (Spark plan)
            val devDoc = db.collection(DEV_UID_MAPPINGS_COLLECTION)
                .document(currentUid)
                .get()
                .await()

            if (devDoc.exists()) {
                val data = devDoc.data ?: return Result.success(null)
                return Result.success(WeeklyUid.fromMap(data))
            }

            // Step 3: Fall back to weeklyUids (Blaze plan with Cloud Functions)
            val uidDoc = db.collection(WEEKLY_UIDS_COLLECTION)
                .document(currentUid)
                .get()
                .await()

            if (uidDoc.exists()) {
                val data = uidDoc.data ?: return Result.success(null)
                Result.success(WeeklyUid.fromMap(data))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Looks up a user by their weekly UID.
     * Used by Trusted Contacts when adding a User by UID.
     *
     * Tries devUidMappings first (Spark), falls back to weeklyUids (Blaze).
     */
    suspend fun findUserByActiveUid(uid: String): Result<WeeklyUid?> {
    return try {

        Log.d("UID_DEBUG", "====================================")
        Log.d("UID_DEBUG", "Starting UID lookup")
        Log.d("UID_DEBUG", "Entered Safety UID: $uid")

        // Check Firebase Authentication
        val authUser = FirebaseAuth.getInstance().currentUser

        Log.d("UID_DEBUG", "Firebase Auth UID: ${authUser?.uid}")
        Log.d("UID_DEBUG", "Firebase Auth Email: ${authUser?.email}")

        if (authUser == null) {
            Log.e("UID_DEBUG", "NO AUTHENTICATED FIREBASE USER")
            return Result.failure(
                Exception("User is not authenticated with Firebase")
            )
        }

        // ============================================================
        // 1. Try devUidMappings first
        // ============================================================

        Log.d(
            "UID_DEBUG",
            "Checking $DEV_UID_MAPPINGS_COLLECTION/$uid"
        )

        val devDoc = db.collection(DEV_UID_MAPPINGS_COLLECTION)
            .document(uid)
            .get()
            .await()

        Log.d(
            "UID_DEBUG",
            "devUidMappings document exists: ${devDoc.exists()}"
        )

        if (devDoc.exists()) {

            val data = devDoc.data

            if (data == null) {
                Log.d(
                    "UID_DEBUG",
                    "devUidMappings document has no data"
                )
                return Result.success(null)
            }

            val weeklyUid = WeeklyUid.fromMap(data)

            Log.d(
                "UID_DEBUG",
                "devUidMappings UID found. Active: ${weeklyUid.isCurrentlyActive}"
            )

            if (weeklyUid.isCurrentlyActive) {
                Log.d(
                    "UID_DEBUG",
                    "SUCCESS: Active UID found in devUidMappings"
                )

                return Result.success(weeklyUid)
            }

            Log.d(
                "UID_DEBUG",
                "UID found in devUidMappings but it is inactive"
            )
        }

        // ============================================================
        // 2. Fall back to weeklyUids
        // ============================================================

        Log.d(
            "UID_DEBUG",
            "Checking $WEEKLY_UIDS_COLLECTION/$uid"
        )

        val doc = db.collection(WEEKLY_UIDS_COLLECTION)
            .document(uid)
            .get()
            .await()

        Log.d(
            "UID_DEBUG",
            "weeklyUids document exists: ${doc.exists()}"
        )

        if (doc.exists()) {

            val data = doc.data

            if (data == null) {
                Log.d(
                    "UID_DEBUG",
                    "weeklyUids document has no data"
                )
                return Result.success(null)
            }

            val weeklyUid = WeeklyUid.fromMap(data)

            Log.d(
                "UID_DEBUG",
                "weeklyUids UID found. Active: ${weeklyUid.isCurrentlyActive}"
            )

            if (weeklyUid.isCurrentlyActive) {
                Log.d(
                    "UID_DEBUG",
                    "SUCCESS: Active UID found in weeklyUids"
                )

                return Result.success(weeklyUid)
            }

            Log.d(
                "UID_DEBUG",
                "UID found in weeklyUids but it is inactive"
            )

            return Result.success(null)
        }

        // ============================================================
        // 3. UID does not exist
        // ============================================================

        Log.d(
            "UID_DEBUG",
            "UID NOT FOUND in either collection"
        )

        return Result.success(null)

    } catch (e: Exception) {

        Log.e(
            "UID_DEBUG",
            "===================================="
        )

        Log.e(
            "UID_DEBUG",
            "UID LOOKUP FAILED"
        )

        Log.e(
            "UID_DEBUG",
            "Error type: ${e::class.java.simpleName}"
        )

        Log.e(
            "UID_DEBUG",
            "Error message: ${e.message}"
        )

        Log.e(
            "UID_DEBUG",
            "Full exception:",
            e
        )

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
        return try {
            db.collection(RELATIONSHIPS_COLLECTION)
                .document(relationshipId)
                .set(fields, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
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
}
