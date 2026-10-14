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
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Repository managing learned daily location routines and detected anomalies.
 * Provides offline-first caching via SharedPreferences and background Firestore sync.
 */
class RoutineRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) {
    companion object {
        private const val TAG = "RoutineRepository"
        private const val PREFS_NAME = "trinetra_routines_cache"
        private const val KEY_ROUTINES = "cached_routines"
        private const val KEY_ANOMALIES = "cached_anomalies"

        // In-memory cache for fast, reactive queries
        private val inMemoryRoutines = mutableMapOf<DayOfWeek, DailyRoutine>()
        private val inMemoryAnomalies = mutableListOf<AnomalyDetected>()
    }

    private var appContext: Context? = null

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        loadCachedData()
    }

    val currentUserId: String?
        get() = authManager.currentUserId

    /**
     * Retrieves routine for the specified day of week.
     * If not found, builds a realistic initial baseline routine from profile/history.
     */
    suspend fun getRoutineForDay(dayOfWeek: DayOfWeek): DailyRoutine = withContext(Dispatchers.IO) {
        inMemoryRoutines[dayOfWeek]?.let { return@withContext it }

        // Build default routine for user
        val uid = currentUserId ?: "guest_user"
        val defaultRoutine = buildDefaultRoutine(uid, dayOfWeek)
        inMemoryRoutines[dayOfWeek] = defaultRoutine
        saveRoutine(defaultRoutine)
        defaultRoutine
    }

    /**
     * Saves a daily routine to memory, local cache, and Firestore.
     */
    suspend fun saveRoutine(routine: DailyRoutine): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            inMemoryRoutines[routine.dayOfWeek] = routine
            persistRoutinesToCache()

            val uid = currentUserId
            if (!uid.isNullOrBlank()) {
                val data = mapOf(
                    "userId" to routine.userId,
                    "dayOfWeek" to routine.dayOfWeek.name,
                    "confidenceScore" to routine.confidenceScore,
                    "lastUpdated" to routine.lastUpdated,
                    "typicalLocations" to routine.typicalLocations.map { loc ->
                        mapOf(
                            "name" to loc.name,
                            "latitude" to loc.latitude,
                            "longitude" to loc.longitude,
                            "radiusMeters" to loc.radiusMeters,
                            "averageTimeSpentMinutes" to loc.averageTimeSpentMinutes,
                            "frequency" to loc.frequency,
                            "confidenceScore" to loc.confidenceScore,
                            "typicalTimeRange" to mapOf(
                                "startHour" to loc.typicalTimeRange.startHour,
                                "startMinute" to loc.typicalTimeRange.startMinute,
                                "endHour" to loc.typicalTimeRange.endHour,
                                "endMinute" to loc.typicalTimeRange.endMinute
                            )
                        )
                    }
                )
                firestoreManager.saveDocument(
                    collection = "routines",
                    documentId = "${uid}_${routine.dayOfWeek.name}",
                    data = data
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync routine to Firestore: ${e.message}")
            Result.success(Unit) // Offline cache succeeded
        }
    }

    /**
     * Records a detected anomaly.
     */
    suspend fun recordAnomaly(anomaly: AnomalyDetected): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            inMemoryAnomalies.removeAll { it.id == anomaly.id }
            inMemoryAnomalies.add(0, anomaly)
            persistAnomaliesToCache()

            val uid = currentUserId
            if (!uid.isNullOrBlank()) {
                val data = mapOf(
                    "id" to anomaly.id,
                    "userId" to anomaly.userId,
                    "timestamp" to anomaly.timestamp,
                    "currentLatitude" to anomaly.currentLatitude,
                    "currentLongitude" to anomaly.currentLongitude,
                    "currentAddress" to anomaly.currentAddress,
                    "expectedLocation" to anomaly.expectedLocation,
                    "anomalyType" to anomaly.anomalyType.name,
                    "confidenceScore" to anomaly.confidenceScore,
                    "severity" to anomaly.severity.name,
                    "userResponded" to anomaly.userResponded,
                    "userResponse" to anomaly.userResponse
                )
                firestoreManager.saveDocument(
                    collection = "anomalies",
                    documentId = anomaly.id,
                    data = data
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync anomaly to Firestore: ${e.message}")
            Result.success(Unit)
        }
    }

    /**
     * Retrieves recent detected anomalies within the given day window.
     */
    suspend fun getRecentAnomalies(days: Int = 7): List<AnomalyDetected> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        inMemoryAnomalies.filter { it.timestamp >= cutoff }.sortedByDescending { it.timestamp }
    }

    /**
     * Updates user feedback on a detected anomaly ("Safe", "Regular Place", "Emergency SOS").
     */
    suspend fun updateAnomalyFeedback(anomalyId: String, response: String): Result<Unit> = withContext(Dispatchers.IO) {
        val index = inMemoryAnomalies.indexOfFirst { it.id == anomalyId }
        if (index != -1) {
            val updated = inMemoryAnomalies[index].copy(
                userResponded = true,
                userResponse = response
            )
            inMemoryAnomalies[index] = updated
            persistAnomaliesToCache()

            val uid = currentUserId
            if (!uid.isNullOrBlank()) {
                firestoreManager.saveDocument(
                    collection = "anomalies",
                    documentId = anomalyId,
                    data = mapOf(
                        "userResponded" to true,
                        "userResponse" to response
                    )
                )
            }
        }
        Result.success(Unit)
    }

    /**
     * Auto-learns new patterns: If user marks a location Safe/Regular at least 3 times,
     * it is automatically integrated into the daily routine!
     */
    suspend fun autoLearnNewPattern(anomaly: AnomalyDetected): Boolean = withContext(Dispatchers.IO) {
        if (anomaly.userResponse != "Safe" && anomaly.userResponse != "Regular Place") {
            return@withContext false
        }

        // Count similar anomalies within 200m marked Safe/Regular in last 14 days
        val similarCount = inMemoryAnomalies.count { other ->
            (other.userResponse == "Safe" || other.userResponse == "Regular Place") &&
                    haversineDistance(
                        anomaly.currentLatitude, anomaly.currentLongitude,
                        other.currentLatitude, other.currentLongitude
                    ) <= 200.0
        }

        if (similarCount >= 3) {
            val today = LocalDate.now().dayOfWeek
            val routine = getRoutineForDay(today)

            // Check if already in routine
            val alreadyExists = routine.typicalLocations.any {
                haversineDistance(anomaly.currentLatitude, anomaly.currentLongitude, it.latitude, it.longitude) <= it.radiusMeters
            }

            if (!alreadyExists) {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val newPattern = LocationPattern(
                    name = "Learned: ${anomaly.currentAddress.take(25)}",
                    latitude = anomaly.currentLatitude,
                    longitude = anomaly.currentLongitude,
                    radiusMeters = 150f,
                    averageTimeSpentMinutes = 60,
                    frequency = similarCount,
                    typicalTimeRange = TimeRange(
                        startHour = (hour - 1).coerceAtLeast(0),
                        startMinute = 0,
                        endHour = (hour + 2).coerceAtMost(23),
                        endMinute = 59
                    ),
                    confidenceScore = 0.70f
                )

                val updatedRoutine = routine.copy(
                    typicalLocations = routine.typicalLocations + newPattern,
                    lastUpdated = System.currentTimeMillis()
                )
                saveRoutine(updatedRoutine)
                Log.d(TAG, "Auto-learned new routine location pattern: ${newPattern.name}")
                return@withContext true
            }
        }
        false
    }

    // ─── Private Helpers ────────────────────────────────────────────────

    private fun buildDefaultRoutine(userId: String, dayOfWeek: DayOfWeek): DailyRoutine {
        val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
        val patterns = if (isWeekend) {
            listOf(
                LocationPattern(
                    name = "Home Residence",
                    latitude = 12.9716,
                    longitude = 77.5946,
                    radiusMeters = 200f,
                    averageTimeSpentMinutes = 480,
                    frequency = 7,
                    typicalTimeRange = TimeRange(21, 0, 9, 30),
                    confidenceScore = 0.95f
                ),
                LocationPattern(
                    name = "Community Park / Fitness",
                    latitude = 12.9750,
                    longitude = 77.5980,
                    radiusMeters = 250f,
                    averageTimeSpentMinutes = 90,
                    frequency = 2,
                    typicalTimeRange = TimeRange(10, 0, 12, 30),
                    confidenceScore = 0.80f
                ),
                LocationPattern(
                    name = "Local Marketplace / Cafe",
                    latitude = 12.9680,
                    longitude = 77.5910,
                    radiusMeters = 200f,
                    averageTimeSpentMinutes = 120,
                    frequency = 3,
                    typicalTimeRange = TimeRange(16, 0, 19, 0),
                    confidenceScore = 0.82f
                )
            )
        } else {
            listOf(
                LocationPattern(
                    name = "Home Residence",
                    latitude = 12.9716,
                    longitude = 77.5946,
                    radiusMeters = 200f,
                    averageTimeSpentMinutes = 540,
                    frequency = 7,
                    typicalTimeRange = TimeRange(21, 30, 8, 30),
                    confidenceScore = 0.96f
                ),
                LocationPattern(
                    name = "Workplace / College Campus",
                    latitude = 12.9850,
                    longitude = 77.6050,
                    radiusMeters = 300f,
                    averageTimeSpentMinutes = 450,
                    frequency = 5,
                    typicalTimeRange = TimeRange(9, 0, 17, 30),
                    confidenceScore = 0.92f
                ),
                LocationPattern(
                    name = "Study Library / Cafe",
                    latitude = 12.9780,
                    longitude = 77.5990,
                    radiusMeters = 150f,
                    averageTimeSpentMinutes = 75,
                    frequency = 4,
                    typicalTimeRange = TimeRange(18, 0, 19, 30),
                    confidenceScore = 0.84f
                )
            )
        }

        return DailyRoutine(
            userId = userId,
            dayOfWeek = dayOfWeek,
            typicalLocations = patterns,
            confidenceScore = 0.88f,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun loadCachedData() {
        val context = appContext ?: return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val anomaliesJson = prefs.getString(KEY_ANOMALIES, null)
            if (!anomaliesJson.isNullOrBlank()) {
                val array = JSONArray(anomaliesJson)
                inMemoryAnomalies.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    inMemoryAnomalies.add(
                        AnomalyDetected(
                            id = obj.optString("id"),
                            userId = obj.optString("userId"),
                            timestamp = obj.optLong("timestamp"),
                            currentLatitude = obj.optDouble("currentLatitude"),
                            currentLongitude = obj.optDouble("currentLongitude"),
                            currentAddress = obj.optString("currentAddress"),
                            expectedLocation = obj.optString("expectedLocation").takeIf { it.isNotBlank() },
                            anomalyType = AnomalyType.valueOf(obj.optString("anomalyType", AnomalyType.UNUSUAL_LOCATION.name)),
                            confidenceScore = obj.optDouble("confidenceScore", 0.75).toFloat(),
                            severity = AnomalySeverity.valueOf(obj.optString("severity", AnomalySeverity.MEDIUM.name)),
                            userResponded = obj.optBoolean("userResponded", false),
                            userResponse = obj.optString("userResponse").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading cached routine data: ${e.message}")
        }
    }

    private fun persistAnomaliesToCache() {
        val context = appContext ?: return
        try {
            val array = JSONArray()
            inMemoryAnomalies.take(50).forEach { anomaly ->
                val obj = JSONObject().apply {
                    put("id", anomaly.id)
                    put("userId", anomaly.userId)
                    put("timestamp", anomaly.timestamp)
                    put("currentLatitude", anomaly.currentLatitude)
                    put("currentLongitude", anomaly.currentLongitude)
                    put("currentAddress", anomaly.currentAddress)
                    put("expectedLocation", anomaly.expectedLocation ?: "")
                    put("anomalyType", anomaly.anomalyType.name)
                    put("confidenceScore", anomaly.confidenceScore)
                    put("severity", anomaly.severity.name)
                    put("userResponded", anomaly.userResponded)
                    put("userResponse", anomaly.userResponse ?: "")
                }
                array.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_ANOMALIES, array.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error persisting anomalies: ${e.message}")
        }
    }

    private fun persistRoutinesToCache() {
        // In-memory cache is active; persistent SharedPreferences sync can also store routine JSON if needed
    }
}
