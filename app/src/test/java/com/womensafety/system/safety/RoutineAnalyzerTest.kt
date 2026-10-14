package com.womensafety.system.safety

import com.womensafety.system.data.model.*
import com.womensafety.system.manager.RiskAssessmentEngine
import com.womensafety.system.manager.RoutineAnalyzer
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek

class RoutineAnalyzerTest {

    private val homePattern = LocationPattern(
        name = "Home",
        latitude = 12.9716,
        longitude = 77.5946,
        radiusMeters = 200f,
        averageTimeSpentMinutes = 480,
        frequency = 7,
        typicalTimeRange = TimeRange(21, 0, 8, 0), // 9 PM - 8 AM
        confidenceScore = 0.95f
    )

    private val workPattern = LocationPattern(
        name = "Workplace",
        latitude = 12.9850,
        longitude = 77.6050,
        radiusMeters = 250f,
        averageTimeSpentMinutes = 450,
        frequency = 5,
        typicalTimeRange = TimeRange(9, 0, 17, 30), // 9 AM - 5:30 PM
        confidenceScore = 0.90f
    )

    private val testRoutine = DailyRoutine(
        userId = "test_user",
        dayOfWeek = DayOfWeek.MONDAY,
        typicalLocations = listOf(homePattern, workPattern),
        confidenceScore = 0.90f
    )

    @Test
    fun testNoAnomalyForKnownLocationAtRightTime() {
        // User at Home at 10:30 PM (22:30)
        val homeLocation = UserLocation(
            latitude = 12.9717,
            longitude = 77.5945,
            accuracy = 10f,
            timestamp = System.currentTimeMillis()
        )

        val result = RoutineAnalyzer.detectAnomaly(
            location = homeLocation,
            routine = testRoutine,
            currentHour = 22,
            currentMinute = 30
        )

        assertNull("Known location at right time should not produce an anomaly", result)
    }

    @Test
    fun testAnomalyForKnownLocationAtWrongTime() {
        // User at Workplace at 3:00 AM (03:00)
        val workLocation = UserLocation(
            latitude = 12.9850,
            longitude = 77.6050,
            accuracy = 10f,
            timestamp = System.currentTimeMillis()
        )

        val result = RoutineAnalyzer.detectAnomaly(
            location = workLocation,
            routine = testRoutine,
            currentHour = 3,
            currentMinute = 0
        )

        assertNotNull("Known location at unexpected 3 AM should produce an anomaly", result)
        assertEquals(AnomalyType.UNUSUAL_TIME, result!!.anomalyType)
        assertEquals(AnomalySeverity.HIGH, result.severity)
    }

    @Test
    fun testAnomalyForUnknownLocationAtNight() {
        // User at isolated location far from routine at 2:00 AM (02:00)
        val remoteLocation = UserLocation(
            latitude = 13.0500, // ~10 km away
            longitude = 77.6800,
            accuracy = 15f,
            timestamp = System.currentTimeMillis()
        )

        val result = RoutineAnalyzer.detectAnomaly(
            location = remoteLocation,
            routine = testRoutine,
            currentHour = 2,
            currentMinute = 0
        )

        assertNotNull("Unknown location at 2 AM should produce an anomaly", result)
        assertEquals(AnomalyType.UNUSUAL_BOTH, result!!.anomalyType)
        assertEquals(AnomalySeverity.HIGH, result.severity)
        assertTrue("Confidence should be high (> 0.75)", result.confidenceScore > 0.75f)
    }

    @Test
    fun testConfidenceIncreasesWithDistanceAndNightTime() {
        val daytimeConfidence = RoutineAnalyzer.calculateAnomalyConfidence(
            nearestKnownDistanceMeters = 200.0,
            routineConsistencyScore = 0.9f,
            isNightTime = false
        )

        val nightRemoteConfidence = RoutineAnalyzer.calculateAnomalyConfidence(
            nearestKnownDistanceMeters = 6000.0,
            routineConsistencyScore = 0.9f,
            isNightTime = true
        )

        assertTrue(
            "Nighttime and remote distance must yield significantly higher anomaly confidence",
            nightRemoteConfidence > daytimeConfidence
        )
    }

    @Test
    fun testRiskAssessmentEngineIntegratesRoutineAnomaly() {
        val highRiskAnomaly = AnomalyDetected(
            userId = "test_user",
            anomalyType = AnomalyType.UNUSUAL_BOTH,
            severity = AnomalySeverity.HIGH,
            confidenceScore = 0.88f
        )

        val assessment = RiskAssessmentEngine.assessRisk(
            currentLocation = null,
            tripStart = null,
            destination = null,
            recentShakeDetected = false,
            safetyMode = "NORMAL",
            isLateNight = false,
            lastMovementTime = 0L,
            currentTime = System.currentTimeMillis(),
            activeOperatingMode = SafetyMode.REGULAR,
            routineAnomaly = highRiskAnomaly
        )

        assertTrue("Risk score should be escalated by routine anomaly (+40)", assessment.riskScore >= 40)
        assertTrue(assessment.activeAnomalies.any { it.contains("Routine Anomaly") })
    }
}
