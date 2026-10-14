package com.womensafety.system.data.repository

import android.content.Context
import android.util.Log
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Repository managing sleep sessions, sleep quality, emergency override configurations,
 * and nocturnal fall detections.
 */
class SleepModeRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) {
    companion object {
        private const val TAG = "SleepModeRepository"
        private const val PREFS_NAME = "trinetra_sleep_cache"
        private const val KEY_CONFIG = "override_config"
        private const val KEY_LAST_QUALITY = "last_sleep_quality"

        private var activeSession: SleepSession? = null
        private var lastSessionQuality: SleepQuality? = null
        private val inMemoryFalls = mutableListOf<FallDetection>()
    }

    private var appContext: Context? = null

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        loadCachedData()
    }

    val currentUserId: String?
        get() = authManager.currentUserId

    fun getActiveSession(): SleepSession? = activeSession

    fun getLastSleepQuality(): SleepQuality? = lastSessionQuality

    /**
     * Starts a new sleep tracking session.
     */
    suspend fun startSleepSession(): SleepSession = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: "user"
        val session = SleepSession(
            userId = uid,
            startTime = System.currentTimeMillis()
        )
        activeSession = session

        try {
            firestoreManager.saveDocument(
                collection = "sleepSessions",
                documentId = session.id,
                data = mapOf(
                    "id" to session.id,
                    "userId" to session.userId,
                    "startTime" to session.startTime,
                    "emergencyWakeupsCount" to session.emergencyWakeupsCount,
                    "disruptions" to session.disruptions
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing sleep session start: ${e.message}")
        }

        session
    }

    /**
     * Ends the active sleep session and evaluates sleep quality.
     */
    suspend fun endSleepSession(session: SleepSession): SleepSession = withContext(Dispatchers.IO) {
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - session.startTime
        session.endTime = endTime
        session.durationMs = durationMs
        val quality = assessSleepQuality(session)
        session.quality = quality
        lastSessionQuality = quality
        activeSession = null

        persistQuality(quality)

        try {
            firestoreManager.saveDocument(
                collection = "sleepSessions",
                documentId = session.id,
                data = mapOf(
                    "id" to session.id,
                    "userId" to session.userId,
                    "startTime" to session.startTime,
                    "endTime" to session.endTime,
                    "durationMs" to session.durationMs,
                    "quality" to quality.name,
                    "emergencyWakeupsCount" to session.emergencyWakeupsCount,
                    "disruptions" to session.disruptions
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing sleep session end: ${e.message}")
        }

        session
    }

    /**
     * Evaluates sleep quality based on duration and emergency disruptions.
     */
    fun assessSleepQuality(session: SleepSession): SleepQuality {
        val durationMinutes = (session.durationMs ?: 0L) / 1000 / 60
        return when {
            session.disruptions == 0 && durationMinutes >= 420 -> SleepQuality.DEEP       // 7+ hours
            session.disruptions <= 2 && durationMinutes >= 300 -> SleepQuality.LIGHT      // 5+ hours
            session.disruptions in 3..5 || durationMinutes in 120..299 -> SleepQuality.RESTLESS
            else -> SleepQuality.POOR
        }
    }

    /**
     * Records an emergency wakeup event during sleep.
     */
    suspend fun recordEmergencyWakeup(sessionId: String, callerName: String, reason: String) = withContext(Dispatchers.IO) {
        activeSession?.let {
            it.emergencyWakeupsCount += 1
            it.disruptions += 1
        }
    }

    /**
     * Records a fall detection event.
     */
    suspend fun recordFallDetection(fall: FallDetection) = withContext(Dispatchers.IO) {
        inMemoryFalls.removeAll { it.id == fall.id }
        inMemoryFalls.add(0, fall)

        activeSession?.let {
            it.disruptions += 1
        }

        val uid = currentUserId
        if (!uid.isNullOrBlank()) {
            try {
                firestoreManager.saveDocument(
                    collection = "fallDetections",
                    documentId = fall.id,
                    data = mapOf(
                        "id" to fall.id,
                        "userId" to uid,
                        "timestamp" to fall.timestamp,
                        "deviceMovement" to fall.deviceMovement,
                        "confidence" to fall.confidence,
                        "alertSent" to fall.alertSent,
                        "userConfirmed" to fall.userConfirmed
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error saving fall detection: ${e.message}")
            }
        }
    }

    suspend fun getEmergencyOverrideConfig(): EmergencyOverrideConfig = withContext(Dispatchers.IO) {
        EmergencyOverrideConfig(
            userId = currentUserId ?: "",
            enabledDuringNightHours = true,
            callThresholdForAlert = 3,
            timeWindowForCallsMs = 60_000L,
            allowSOSOverride = true,
            allowTrustedContactSOS = true
        )
    }

    private fun persistQuality(quality: SleepQuality) {
        val context = appContext ?: return
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_QUALITY, quality.name)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error persisting quality: ${e.message}")
        }
    }

    private fun loadCachedData() {
        val context = appContext ?: return
        try {
            val qStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_QUALITY, null)
            if (!qStr.isNullOrBlank()) {
                lastSessionQuality = SleepQuality.valueOf(qStr)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading cached sleep quality: ${e.message}")
        }
    }
}
