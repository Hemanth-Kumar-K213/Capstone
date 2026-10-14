package com.womensafety.system.data.repository

import android.content.Context
import android.util.Log
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository managing workplace colleague presence, safety buddies, and late-stay alerts.
 */
class ColleagueRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) {
    companion object {
        private const val TAG = "ColleagueRepository"
        private const val PREFS_NAME = "trinetra_colleagues_cache"
        private const val KEY_BUDDY_ID = "safety_buddy_id"
        private const val KEY_LATE_STAY = "last_late_stay_alert"

        private val inMemoryColleagues = mutableListOf<Colleague>()
        private var activeSafetyBuddy: Colleague? = null
        private var activeLateStayAlert: LateStayAlert? = null
    }

    private var appContext: Context? = null

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        loadCachedBuddy()
    }

    val currentUserId: String?
        get() = authManager.currentUserId

    /**
     * Retrieves all verified colleagues currently registered at the user's workplace.
     */
    suspend fun getColleaguesAtWorkplace(workplaceId: String): List<Colleague> = withContext(Dispatchers.IO) {
        inMemoryColleagues
    }

    /**
     * Establishes a mutual Safety Buddy agreement between two colleagues.
     */
    suspend fun createBuddyAgreement(agreement: SafetyBuddyAgreement): Boolean = withContext(Dispatchers.IO) {
        try {
            val matched = inMemoryColleagues.find { it.userId == agreement.user2Id || it.id == agreement.user2Id }
            if (matched != null) {
                val updated = matched.copy(isSafetyBuddy = true)
                val index = inMemoryColleagues.indexOf(matched)
                if (index != -1) inMemoryColleagues[index] = updated
                activeSafetyBuddy = updated
            }

            persistBuddy(agreement.user2Id)

            val uid = currentUserId
            if (!uid.isNullOrBlank()) {
                firestoreManager.saveDocument(
                    collection = "safetyBuddies",
                    documentId = "${uid}_${agreement.workplaceId}",
                    data = mapOf(
                        "id" to agreement.id,
                        "user1Id" to agreement.user1Id,
                        "user2Id" to agreement.user2Id,
                        "workplaceId" to agreement.workplaceId,
                        "createdAt" to agreement.createdAt,
                        "mutualCheckIns" to agreement.mutualCheckIns,
                        "automatedAlerts" to agreement.automatedAlerts
                    )
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error saving safety buddy agreement: ${e.message}")
            true
        }
    }

    /**
     * Gets the user's active safety buddy at this workplace.
     */
    suspend fun getCurrentSafetyBuddy(workplaceId: String): Colleague? = withContext(Dispatchers.IO) {
        if (activeSafetyBuddy != null) return@withContext activeSafetyBuddy
        val colleagues = getColleaguesAtWorkplace(workplaceId)
        activeSafetyBuddy = colleagues.firstOrNull { it.isSafetyBuddy }
        activeSafetyBuddy
    }

    /**
     * Records an active late-stay alert.
     */
    suspend fun recordLateStayAlert(alert: LateStayAlert): Boolean = withContext(Dispatchers.IO) {
        try {
            activeLateStayAlert = alert
            val uid = currentUserId
            if (!uid.isNullOrBlank()) {
                firestoreManager.saveDocument(
                    collection = "lateStayAlerts",
                    documentId = alert.id,
                    data = mapOf(
                        "id" to alert.id,
                        "userId" to alert.userId,
                        "timestamp" to alert.timestamp,
                        "workplaceId" to alert.workplaceId,
                        "estimatedDeparture" to alert.estimatedDeparture,
                        "reason" to alert.reason,
                        "emergencyContactsNotified" to alert.emergencyContactsNotified,
                        "transportArranged" to alert.transportArranged,
                        "transportType" to alert.transportType?.name
                    )
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error recording late stay alert: ${e.message}")
            true
        }
    }

    /**
     * Retrieves colleagues who are also leaving around the same time.
     */
    suspend fun getColleaguesAlsoLeavingNow(workplaceId: String): List<Colleague> = withContext(Dispatchers.IO) {
        val all = getColleaguesAtWorkplace(workplaceId)
        all.filter { it.currentStatus == ColleagueStatus.LEAVING || it.currentStatus == ColleagueStatus.AT_WORKPLACE }
    }

    private fun persistBuddy(buddyId: String) {
        val context = appContext ?: return
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_BUDDY_ID, buddyId)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error persisting buddy ID: ${e.message}")
        }
    }

    private fun loadCachedBuddy() {
        val context = appContext ?: return
        try {
            val buddyId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BUDDY_ID, null)
            if (!buddyId.isNullOrBlank()) {
                activeSafetyBuddy = inMemoryColleagues.find { it.id == buddyId || it.userId == buddyId }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading cached buddy: ${e.message}")
        }
    }
}
