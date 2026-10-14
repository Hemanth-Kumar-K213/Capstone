package com.womensafety.system.manager

import android.content.Context
import android.util.Log
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.*
import com.womensafety.system.data.repository.RoutineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar

/**
 * Core engine for Routine-Based Anomaly Detection in REGULAR Safety Mode.
 * Learns daily spatiotemporal habits and identifies unexpected locations or timings.
 */
object RoutineAnalyzer {

    private const val TAG = "RoutineAnalyzer"

    private val routineRepository = RoutineRepository()
    private val authManager = FirebaseAuthManager()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _activeAnomaly = MutableStateFlow<AnomalyDetected?>(null)
    val activeAnomaly: StateFlow<AnomalyDetected?> = _activeAnomaly.asStateFlow()

    private val _lastEvaluationTime = MutableStateFlow<Long?>(null)
    val lastEvaluationTime: StateFlow<Long?> = _lastEvaluationTime.asStateFlow()

    fun initialize(context: Context) {
        routineRepository.initialize(context)
    }

    /**
     * Checks an incoming GPS location against today's learned routine.
     */
    suspend fun checkForAnomalies(location: UserLocation): AnomalyDetected? {
        val today = LocalDate.now().dayOfWeek
        val routine = routineRepository.getRoutineForDay(today)

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val anomaly = detectAnomaly(location, routine, currentHour, currentMinute)
        _lastEvaluationTime.value = System.currentTimeMillis()

        if (anomaly != null) {
            Log.w(TAG, "Routine Anomaly detected: ${anomaly.anomalyType} (severity=${anomaly.severity}, conf=${anomaly.confidenceScore})")
            routineRepository.recordAnomaly(anomaly)

            // Alert user if high confidence
            if (anomaly.confidenceScore >= 0.70f) {
                _activeAnomaly.value = anomaly
            }
        } else {
            // Clear active alert if user returned to a known normal place
            if (_activeAnomaly.value?.userResponded == false) {
                _activeAnomaly.value = null
            }
        }

        return anomaly
    }

    /**
     * Algorithmic evaluation of location against routine patterns.
     */
    fun detectAnomaly(
        location: UserLocation,
        routine: DailyRoutine,
        currentHour: Int,
        currentMinute: Int
    ): AnomalyDetected? {
        val isNight = isNightTime(currentHour)

        // 1. Check if user is within radius of any known routine location
        var matchingPattern: LocationPattern? = null
        var minDistance = Double.MAX_VALUE

        for (pattern in routine.typicalLocations) {
            val dist = haversineDistance(
                location.latitude, location.longitude,
                pattern.latitude, pattern.longitude
            )
            if (dist < minDistance) {
                minDistance = dist
            }
            if (dist <= pattern.radiusMeters) {
                matchingPattern = pattern
                break
            }
        }

        return when {
            // A. Known location at the expected time range -> Normal
            matchingPattern != null && matchingPattern.typicalTimeRange.contains(currentHour, currentMinute) -> {
                null
            }

            // B. Known location, but unusual time slot
            matchingPattern != null && !matchingPattern.typicalTimeRange.contains(currentHour, currentMinute) -> {
                val confidence = 0.65f + if (isNight) 0.15f else 0.0f
                val severity = if (isNight) AnomalySeverity.HIGH else AnomalySeverity.MEDIUM
                AnomalyDetected(
                    userId = authManager.currentUserId ?: "",
                    timestamp = System.currentTimeMillis(),
                    currentLatitude = location.latitude,
                    currentLongitude = location.longitude,
                    currentAddress = location.address ?: "${matchingPattern.name} (Off-hours)",
                    expectedLocation = "${matchingPattern.name} (${matchingPattern.typicalTimeRange.displayString()})",
                    anomalyType = AnomalyType.UNUSUAL_TIME,
                    confidenceScore = confidence.coerceIn(0f, 1f),
                    severity = severity
                )
            }

            // C. Unknown / Unrecognized location
            matchingPattern == null -> {
                val confidence = calculateAnomalyConfidence(minDistance, routine.confidenceScore, isNight)
                val type = if (isNight) AnomalyType.UNUSUAL_BOTH else AnomalyType.UNUSUAL_LOCATION
                val severity = when {
                    confidence > 0.85f && isNight -> AnomalySeverity.HIGH
                    confidence > 0.70f -> AnomalySeverity.MEDIUM
                    else -> AnomalySeverity.LOW
                }

                AnomalyDetected(
                    userId = authManager.currentUserId ?: "",
                    timestamp = System.currentTimeMillis(),
                    currentLatitude = location.latitude,
                    currentLongitude = location.longitude,
                    currentAddress = location.address ?: "Unknown Location (${location.latitude.take(7)}, ${location.longitude.take(7)})",
                    expectedLocation = routine.typicalLocations.firstOrNull { it.typicalTimeRange.contains(currentHour, currentMinute) }?.name,
                    anomalyType = type,
                    confidenceScore = confidence,
                    severity = severity
                )
            }

            else -> null
        }
    }

    /**
     * Calculates Bayesian-style confidence based on spatiotemporal indicators.
     */
    fun calculateAnomalyConfidence(
        nearestKnownDistanceMeters: Double,
        routineConsistencyScore: Float,
        isNightTime: Boolean
    ): Float {
        var confidence = 0.50f

        // Unusual nighttime hours (10 PM - 5 AM) increase certainty of risk
        if (isNightTime) confidence += 0.25f

        // High routine predictability increases anomaly certainty
        confidence += routineConsistencyScore * 0.15f

        // Distance from nearest routine anchor point
        when {
            nearestKnownDistanceMeters > 5000.0 -> confidence += 0.20f // > 5 km away
            nearestKnownDistanceMeters > 2000.0 -> confidence += 0.10f // > 2 km away
            nearestKnownDistanceMeters > 500.0 -> confidence += 0.05f  // > 500 m away
        }

        return confidence.coerceIn(0.1f, 1.0f)
    }

    /**
     * Dispatches user feedback on an anomaly and triggers auto-learning if verified safe.
     */
    fun submitAnomalyResponse(anomalyId: String, response: String) {
        scope.launch {
            routineRepository.updateAnomalyFeedback(anomalyId, response)
            val current = _activeAnomaly.value
            if (current != null && current.id == anomalyId) {
                _activeAnomaly.value = current.copy(userResponded = true, userResponse = response)
                routineRepository.autoLearnNewPattern(current.copy(userResponded = true, userResponse = response))
            }
        }
    }

    fun dismissActiveAnomaly() {
        _activeAnomaly.value = null
    }

    /**
     * Capstone Demo helper: immediately simulates an anomaly for live testing.
     */
    fun simulateDemoAnomaly(
        type: AnomalyType = AnomalyType.UNUSUAL_LOCATION,
        severity: AnomalySeverity = AnomalySeverity.HIGH,
        address: String = "Industrial Ring Road, Sector 4 (Unscheduled Stop)"
    ): AnomalyDetected {
        val anomaly = AnomalyDetected(
            userId = authManager.currentUserId ?: "demo_user",
            timestamp = System.currentTimeMillis(),
            currentLatitude = 12.9250,
            currentLongitude = 77.5850,
            currentAddress = address,
            expectedLocation = "Home Residence",
            anomalyType = type,
            confidenceScore = if (severity == AnomalySeverity.HIGH) 0.88f else 0.72f,
            severity = severity
        )

        scope.launch {
            routineRepository.recordAnomaly(anomaly)
            _activeAnomaly.value = anomaly
        }

        return anomaly
    }

    private fun isNightTime(hour: Int): Boolean {
        return hour >= 22 || hour < 5
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

    private fun Double.take(n: Int): String {
        val str = this.toString()
        return if (str.length > n) str.substring(0, n) else str
    }
}
