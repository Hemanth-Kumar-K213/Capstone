package com.womensafety.system.risk

import com.womensafety.system.data.health.model.HealthDataFreshness
import com.womensafety.system.data.health.model.HealthRiskSignal
import com.womensafety.system.data.model.SafetyMode
import com.womensafety.system.manager.RiskAssessmentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskAssessmentEngineTest {

    @Test
    fun testCapstoneReportDocumentedScenario() {
        // Step 1: Initial state (Route deviation +3, Late-night +2 = 5)
        val initial = RiskAssessmentEngine.evaluateCapstoneScenario(
            isRouteDeviated = true,
            isLateNight = true,
            isHeartRateSpike = false
        )
        assertEquals(3, initial.routeDeviationContribution)
        assertEquals(2, initial.lateNightContribution)
        assertEquals(0, initial.heartRateSpikeContribution)
        assertEquals(5, initial.totalRiskScore)
        assertTrue("Initial score of 5 must trigger safety confirmation prompt", initial.isConfirmationPromptTriggered)
        assertFalse("Initial score of 5 is not yet critical", initial.isCriticalRisk)

        // Step 2: Abnormal heart-rate spike adds +4, resulting in critical score 9
        val escalated = RiskAssessmentEngine.evaluateCapstoneScenario(
            isRouteDeviated = true,
            isLateNight = true,
            isHeartRateSpike = true
        )
        assertEquals(3, escalated.routeDeviationContribution)
        assertEquals(2, escalated.lateNightContribution)
        assertEquals(4, escalated.heartRateSpikeContribution)
        assertEquals(9, escalated.totalRiskScore)
        assertTrue(escalated.isConfirmationPromptTriggered)
        assertTrue("Score of 9 is classified as critical risk per report", escalated.isCriticalRisk)
    }

    @Test
    fun testObservationMode_doesNotAlterRiskScore() {
        val now = System.currentTimeMillis()
        val observationSignal = HealthRiskSignal(
            heartRateBpm = 110,
            timestamp = now,
            sampleAgeMs = 1000L,
            freshness = HealthDataFreshness.FRESH,
            isAnomalousSpike = true,
            isObservationOnly = true // Observation mode enabled
        )

        val resultWithoutSignal = RiskAssessmentEngine.assessRisk(
            currentLocation = null,
            tripStart = null,
            destination = null,
            recentShakeDetected = false,
            safetyMode = "NORMAL",
            isLateNight = false,
            lastMovementTime = now,
            currentTime = now,
            healthSignal = null
        )

        val resultWithObservationSignal = RiskAssessmentEngine.assessRisk(
            currentLocation = null,
            tripStart = null,
            destination = null,
            recentShakeDetected = false,
            safetyMode = "NORMAL",
            isLateNight = false,
            lastMovementTime = now,
            currentTime = now,
            healthSignal = observationSignal
        )

        assertEquals("Observation mode must not inflate risk score", resultWithoutSignal.riskScore, resultWithObservationSignal.riskScore)
        assertTrue("Diagnostic note should be captured in anomalies", resultWithObservationSignal.activeAnomalies.any { it.contains("Observation Mode") })
    }

    @Test
    fun testMissingOrStaleHealthData_neverInflatesRiskScore() {
        val now = System.currentTimeMillis()

        // 1. Stale reading
        val staleSignal = HealthRiskSignal(
            heartRateBpm = 95,
            timestamp = now - (15 * 60 * 1000L),
            sampleAgeMs = 15 * 60 * 1000L,
            freshness = HealthDataFreshness.STALE,
            isAnomalousSpike = true,
            isObservationOnly = false
        )

        val resultStale = RiskAssessmentEngine.assessRisk(
            currentLocation = null,
            tripStart = null,
            destination = null,
            recentShakeDetected = false,
            safetyMode = "NORMAL",
            isLateNight = false,
            lastMovementTime = now,
            currentTime = now,
            healthSignal = staleSignal
        )

        assertEquals("Stale reading must contribute 0 to risk score", 0, resultStale.riskScore)

        // 2. Unavailable reading
        val unavailableSignal = HealthRiskSignal(
            heartRateBpm = null,
            timestamp = null,
            sampleAgeMs = null,
            freshness = HealthDataFreshness.UNAVAILABLE,
            isAnomalousSpike = false,
            isObservationOnly = false
        )

        val resultUnavailable = RiskAssessmentEngine.assessRisk(
            currentLocation = null,
            tripStart = null,
            destination = null,
            recentShakeDetected = false,
            safetyMode = "NORMAL",
            isLateNight = false,
            lastMovementTime = now,
            currentTime = now,
            healthSignal = unavailableSignal
        )

        assertEquals("Unavailable reading must contribute 0 to risk score", 0, resultUnavailable.riskScore)
    }
}
