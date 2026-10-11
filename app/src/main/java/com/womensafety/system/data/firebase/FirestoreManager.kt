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
            if (currentUid.isNullOrBlank()) return Result.success(null)

            // Step 1: Check authoritative production collection weeklyUids FIRST
            val uidDoc = db.collection(WEEKLY_UIDS_COLLECTION)
                .document(currentUid)
                .get()
                .await()

            if (uidDoc.exists()) {
                val data = uidDoc.data ?: return Result.success(null)
                val weeklyUid = WeeklyUid.fromMap(data)
                return if (weeklyUid.isCurrentlyActive) {
                    Result.success(weeklyUid)
                } else {
                    Result.success(null)
                }
            }

            // Step 2: Fall back to devUidMappings (Spark development/demo fallback)
            val devDoc = db.collection(DEV_UID_MAPPINGS_COLLECTION)
                .document(currentUid)
                .get()
                .await()

            if (devDoc.exists()) {
                val data = devDoc.data ?: return Result.success(null)
                val weeklyUid = WeeklyUid.fromMap(data)
                return if (weeklyUid.isCurrentlyActive) {
                    Result.success(weeklyUid)
                } else {
                    Result.success(null)
                }
            }

            Result.success(null)
        } catch (e: Exception) {
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
            val doc = db.collection(WEEKLY_UIDS_COLLECTION)
                .document(trimmedUid)
                .get()
                .await()

            if (doc.exists()) {
                val data = doc.data
                if (data != null) {
                    val weeklyUid = WeeklyUid.fromMap(data)
                    if (weeklyUid.isCurrentlyActive) {
                        Log.d("UID_DEBUG", "SUCCESS: Active UID found in weeklyUids")
                        return Result.success(weeklyUid)
                    } else {
                        Log.d("UID_DEBUG", "UID found in weeklyUids but is expired or inactive")
                        return Result.success(null)
                    }
                }
            }

            // ============================================================
            // 2. Fall back to devUidMappings (Spark development mode only)
            // ============================================================
            val devDoc = db.collection(DEV_UID_MAPPINGS_COLLECTION)
                .document(trimmedUid)
                .get()
                .await()

            if (devDoc.exists()) {
                val data = devDoc.data
                if (data != null) {
                    val weeklyUid = WeeklyUid.fromMap(data)
                    if (weeklyUid.isCurrentlyActive) {
                        Log.d("UID_DEBUG", "SUCCESS: Active UID found in devUidMappings")
                        return Result.success(weeklyUid)
                    } else {
                        Log.d("UID_DEBUG", "UID found in devUidMappings but is expired or inactive")
                        return Result.success(null)
                    }
                }
            }

            Log.d("UID_DEBUG", "UID NOT FOUND in either collection: $trimmedUid")
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
                .set(locationToSave.toMap())
                .await()

            // 2. Also mirror to uid_locations/{uid} for alias compatibility
            db.collection(UID_LOCATIONS_COLLECTION)
                .document(docId)
                .set(data, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreManager", "saveUidLocation failed for uid: $uid", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves the latest known location by UID from child_locations.
     */
    suspend fun getLatestUidLocation(uid: String): Result<UserLocation?> {
        if (uid.isBlank()) return Result.success(null)
        return try {
            val snapshot = db.collection(CHILD_LOCATIONS_COLLECTION)
                .document(uid.trim())
                .get()
                .await()

            if (snapshot.exists()) {
                val data = snapshot.data
                val loc = data?.let { UserLocation.fromMap(snapshot.id, it) }
                Result.success(loc)
            } else {
                Result.success(null)
            }
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
     * Dual-mode listener that observes location by UID (from child_locations)
     * or by auth userId (from userLocations).
     */
    fun listenToLocationByUserIdOrUid(identifier: String): Flow<UserLocation?> = callbackFlow {
        if (identifier.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val target = identifier.trim()

        // 1. Try child_locations/{target}
        val childDocRef = db.collection(CHILD_LOCATIONS_COLLECTION).document(target)
        val childListener = childDocRef.addSnapshotListener { childSnap, error ->
            if (error == null && childSnap != null && childSnap.exists()) {
                val data = childSnap.data
                if (data != null) {
                    val loc = UserLocation.fromMap(childSnap.id, data)
                    trySend(loc)
                }
            }
        }

        // 2. Also listen to userLocations/{target}/locations (subcollection)
        val userListener = db.collection(USER_LOCATIONS_COLLECTION)
            .document(target)
            .collection(LOCATIONS_SUBCOLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { userSnap, error ->
                if (error == null && userSnap != null && !userSnap.isEmpty) {
                    val doc = userSnap.documents.first()
                    val loc = doc.data?.let { UserLocation.fromMap(doc.id, it) }
                    if (loc != null) {
                        trySend(loc)
                    }
                }
            }

        awaitClose {
            childListener.remove()
            userListener.remove()
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
            db.collection(SOS_EVENTS_COLLECTION)
                .document(eventId)
                .update(updates)
                .await()
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
}

