package com.womensafety.system.manager

import com.womensafety.system.data.health.model.HealthDataFreshness
import com.womensafety.system.data.health.model.HealthRiskSignal
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.model.SafetyMode
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision, transparent rule-based risk assessment engine.
 * Calculates safety risk scores (0-100) based on location context, deviation, sensor events, and time.
 */
object RiskAssessmentEngine {

    data class AssessmentResult(
        val riskScore: Int,
        val activeAnomalies: List<String>
    )

    data class CapstoneScenarioResult(
        val routeDeviationContribution: Int,
        val lateNightContribution: Int,
        val heartRateSpikeContribution: Int,
        val totalRiskScore: Int,
        val isConfirmationPromptTriggered: Boolean,
        val isCriticalRisk: Boolean
    )

    /**
     * Exact evaluation of the Capstone Report documented scenario:
     * - Route deviation: +3
     * - Late-night travel: +2
     * - Initial risk score: 5 (triggers safety confirmation prompt)
     * - 60-second response window
     * - Abnormal heart-rate spike: +4
     * - Updated risk score: 9 (Critical risk)
     */
    fun evaluateCapstoneScenario(
        isRouteDeviated: Boolean,
        isLateNight: Boolean,
        isHeartRateSpike: Boolean
    ): CapstoneScenarioResult {
        val routeScore = if (isRouteDeviated) 3 else 0
        val lateNightScore = if (isLateNight) 2 else 0
        val hrScore = if (isHeartRateSpike) 4 else 0
        val total = routeScore + lateNightScore + hrScore
        return CapstoneScenarioResult(
            routeDeviationContribution = routeScore,
            lateNightContribution = lateNightScore,
            heartRateSpikeContribution = hrScore,
            totalRiskScore = total,
            isConfirmationPromptTriggered = total >= 5,
            isCriticalRisk = total >= 9
        )
    }

    /**
     * Assesses overall safety risk based on current state parameters.
     */
    fun assessRisk(
        currentLocation: UserLocation?,
        tripStart: UserLocation?,
        destination: UserLocation?,
        recentShakeDetected: Boolean,
        safetyMode: String,
        isLateNight: Boolean,
        lastMovementTime: Long,
        currentTime: Long,
        isRouteDeviated: Boolean = false,
        isNoResponseToDeviation: Boolean = false,
        activeOperatingMode: SafetyMode = SafetyMode.REGULAR,
        healthSignal: HealthRiskSignal? = null
    ): AssessmentResult {
        val anomalies = mutableListOf<String>()
        var score = 0

        // Configurable multiplier depending on safety mode (sensitivity level)
        val weightMultiplier = when (safetyMode.uppercase()) {
            "CAUTIOUS" -> 1.3f
            "HIGH_RISK" -> 1.6f
            else -> 1.0f // NORMAL
        }

        // Bypasses based on operating safety mode
        val checkTravelAnomalies = activeOperatingMode == SafetyMode.TRAVELLING

        // 1. Significant Route Deviation (Only in Travelling Mode or if active journey is explicitly flagged)
        if (activeOperatingMode != SafetyMode.SLEEPING) {
            if (isRouteDeviated) {
                anomalies.add("Travel Route Deviation")
                score += (35 * weightMultiplier).toInt()
            } else if (checkTravelAnomalies && currentLocation != null && destination != null && tripStart != null) {
                val deviationMeters = calculateCrossTrackDistance(
                    currentLocation.latitude, currentLocation.longitude,
                    tripStart.latitude, tripStart.longitude,
                    destination.latitude, destination.longitude
                )
                val threshold = 200.0
                if (deviationMeters > threshold) {
                    anomalies.add("Significant Route Deviation (${deviationMeters.toInt()}m)")
                    score += (30 * weightMultiplier).toInt()
                }
            }
        }

        // 1.5. No Response to Route Deviation Alert
        if (activeOperatingMode != SafetyMode.SLEEPING && isNoResponseToDeviation) {
            anomalies.add("No Response to Route Deviation Alert")
            score += (50 * weightMultiplier).toInt()
        }

        // 2. Unexpected Prolonged Stop / Inactivity (Bypassed in Sleeping and Working Modes)
        if (activeOperatingMode != SafetyMode.SLEEPING && activeOperatingMode != SafetyMode.WORKING && currentLocation != null && lastMovementTime > 0) {
            val stopDurationMs = currentTime - lastMovementTime
            val prolongedStopThresholdMs = 300_000L // 5 minutes
            val currentSpeedKmh = (currentLocation.speed ?: 0f) * 3.6f

            if (stopDurationMs >= prolongedStopThresholdMs && currentSpeedKmh < 1.5f) {
                val durationMins = stopDurationMs / 1000 / 60
                anomalies.add("Unexpected Prolonged Stop (${durationMins}m)")
                score += (40 * weightMultiplier).toInt()
            }
        }

        // 3. Sensor-triggered Anomaly (Recent shake) - Bypassed in Sleeping Mode
        if (activeOperatingMode.config.isShakeDetectionActive && recentShakeDetected) {
            anomalies.add("Abnormal Movement / Shake Event")
            score += (30 * weightMultiplier).toInt()
        }

        // 4. Late-Night Travel Context (10 PM to 5 AM) - Bypassed in Sleeping Mode
        if (activeOperatingMode != SafetyMode.SLEEPING && isLateNight) {
            anomalies.add("Late-Night Travel Context")
            score += (15 * weightMultiplier).toInt()
        }

        // 4.5. Physiological Health Signal (Smartwatch Heart Rate)
        if (healthSignal != null && activeOperatingMode != SafetyMode.SLEEPING) {
            when {
                // Missing or stale data MUST NEVER be converted to danger
                healthSignal.freshness == HealthDataFreshness.UNAVAILABLE || healthSignal.freshness == HealthDataFreshness.STALE -> {
                    // Do not inflate risk score; preserve safety baseline
                }
                healthSignal.isObservationOnly -> {
                    // Observation Mode: log diagnostic anomaly without escalating risk score
                    if (healthSignal.heartRateBpm != null) {
                        anomalies.add("Observed Heart Rate: ${healthSignal.heartRateBpm} BPM (Observation Mode)")
                    }
                }
                healthSignal.isAnomalousSpike -> {
                    // Evaluated physiological anomaly
                    anomalies.add("Abnormal Physiological Heart-Rate Spike")
                    score += (25 * weightMultiplier).toInt()
                }
            }
        }

        // 5. Compounding Risk for multiple simultaneous anomalies
        if (anomalies.size >= 2) {
            anomalies.add("Compounding Risk Factors")
            score += (20 * weightMultiplier).toInt()
        }

        // Clamp the final score to [0, 100]
        val finalScore = score.coerceIn(0, 100)
        return AssessmentResult(finalScore, anomalies)
    }

    /**
     * Calculates the perpendicular distance (in meters) from point P (current location)
     * to the great-circle path from point A (trip start) to point B (destination).
     */
    private fun calculateCrossTrackDistance(
        latP: Double, lonP: Double,
        latA: Double, lonA: Double,
        latB: Double, lonB: Double
    ): Double {
        val r = 6371000.0 // Earth's mean radius in meters
        val latPRad = Math.toRadians(latP)
        val lonPRad = Math.toRadians(lonP)
        val latARad = Math.toRadians(latA)
        val lonARad = Math.toRadians(lonA)
        val latBRad = Math.toRadians(latB)
        val lonBRad = Math.toRadians(lonB)

        // Distance from start to current location
        val d13 = haversineDistance(latA, lonA, latP, lonP) / r

        // Bearings
        val bearing12 = calculateBearing(latARad, lonARad, latBRad, lonBRad)
        val bearing13 = calculateBearing(latARad, lonARad, latPRad, lonPRad)

        // Cross-track distance formula on a sphere
        val xt = asin(sin(d13) * sin(bearing13 - bearing12))
        return abs(xt * r)
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return atan2(y, x)
    }
}
