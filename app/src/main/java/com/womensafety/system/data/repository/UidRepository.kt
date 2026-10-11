package com.womensafety.system.data.repository

import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.WeeklyUid

/**
 * READ-ONLY repository for accessing the weekly rotating UID.
 *
 * IMPORTANT: This repository does NOT generate or rotate UIDs in production.
 * UID generation and rotation are handled exclusively by Firebase Cloud Functions.
 * The Android client can only READ the current active UID from Firestore.
 *
 * DEV/DEMO MODE (Spark plan):
 * When Cloud Functions are not deployed, [generateDevUid] creates a UID
 * on the client and saves it to the devUidMappings collection (which has
 * appropriate security rules: owner-only write, authenticated get, no listing).
 */
class UidRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {

    /**
     * Fetches the current active weekly UID for the given user.
     * Returns null if no active UID exists (e.g., Cloud Function hasn't run yet
     * and dev UID hasn't been generated).
     */
    suspend fun getCurrentUid(userId: String): Result<WeeklyUid?> {
        return firestoreManager.getActiveUidForUser(userId)
    }

    /**
     * Looks up a User by their current active weekly UID.
     * Used by Trusted Contacts when connecting to a User.
     *
     * Returns the WeeklyUid document which contains the stable userId (Firebase Auth UID).
     * The weekly UID is resolved to the permanent internal identity here.
     */
    suspend fun findUserByUid(uid: String): Result<WeeklyUid?> {
        return firestoreManager.findUserByActiveUid(uid)
    }

    /**
     * Requests on-demand UID verification or rotation from the authoritative backend Cloud Function.
     * Recovers from missing initial UIDs or delayed scheduled rotation without client-side generation.
     */
    suspend fun refreshBackendUid(): Result<WeeklyUid?> {
        return firestoreManager.requestBackendUidRefresh()
    }

    /**
     * DEV/DEMO ONLY: Generates a UID on the client and saves it to devUidMappings.
     * Also updates the user's profile with currentWeeklyUid.
     *
     * This is the Spark-plan fallback when Cloud Functions are not deployed.
     * The devUidMappings rules ensure users can only write their own mapping.
     */
    suspend fun generateDevUid(userId: String, userName: String = ""): Result<WeeklyUid> {
        return firestoreManager.generateAndSaveDevUid(userId, userName)
    }
}
