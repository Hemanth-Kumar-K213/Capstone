package com.womensafety.system.health

import com.womensafety.system.data.health.HealthConnectManager
import com.womensafety.system.data.health.model.HealthDataFreshness
import com.womensafety.system.data.health.model.HeartRateMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HeartRateMonitoringTest {

    @Before
    fun setUp() {
        HealthConnectManager.clearSession()
    }

    @Test
    fun testMeasurementFreshness_under10MinutesIsFresh() {
        val now = System.currentTimeMillis()
        val freshMeasurement = HeartRateMeasurement(
            bpm = 78,
            timestamp = now - (5 * 60 * 1000L), // 5 minutes ago
            dataOriginPackage = "com.google.android.apps.fitness",
            recordId = "rec_1"
        )

        assertEquals(HealthDataFreshness.FRESH, freshMeasurement.freshness(now))
        assertTrue(freshMeasurement.ageMs(now) < 600_000L)
    }

    @Test
    fun testMeasurementFreshness_10MinutesOrMoreIsStale() {
        val now = System.currentTimeMillis()
        val staleMeasurement = HeartRateMeasurement(
            bpm = 82,
            timestamp = now - (15 * 60 * 1000L), // 15 minutes ago
            dataOriginPackage = "com.google.android.apps.fitness",
            recordId = "rec_2"
        )

        assertEquals(HealthDataFreshness.STALE, staleMeasurement.freshness(now))
        assertTrue(staleMeasurement.ageMs(now) >= 600_000L)
    }

    @Test
    fun testSortingAndDeduplicationOfSamples() {
        val now = System.currentTimeMillis()
        val rawSamples = listOf(
            HeartRateMeasurement(72, now - 30_000, "com.boat.crest", "r1"),
            HeartRateMeasurement(85, now - 10_000, "com.boat.crest", "r2"), // Newest
            HeartRateMeasurement(72, now - 30_000, "com.boat.crest", "r1_duplicate"), // Duplicate timestamp & bpm
            HeartRateMeasurement(68, now - 60_000, "com.boat.crest", "r0")  // Oldest
        )

        val deduplicatedSorted = rawSamples
            .distinctBy { Pair(it.timestamp, it.bpm) }
            .sortedByDescending { it.timestamp }

        assertEquals(3, deduplicatedSorted.size)
        assertEquals(85, deduplicatedSorted[0].bpm) // Newest first
        assertEquals(72, deduplicatedSorted[1].bpm)
        assertEquals(68, deduplicatedSorted[2].bpm)
    }

    @Test
    fun testMultiUserSessionIsolation_clearsCacheOnAccountSwitch() {
        // Set user 1
        HealthConnectManager.onUserSessionChanged("user_alpha_123")
        // Trigger session clear on account switch
        HealthConnectManager.onUserSessionChanged("user_beta_456")

        assertNull(HealthConnectManager.latestMeasurement.value)
        assertTrue(HealthConnectManager.recentMeasurements.value.isEmpty())
        assertNull(HealthConnectManager.lastQueryTimestamp.value)
    }

    @Test
    fun testExplicitClearSession_resetsAllState() {
        HealthConnectManager.clearSession()
        assertNull(HealthConnectManager.latestMeasurement.value)
        assertTrue(HealthConnectManager.recentMeasurements.value.isEmpty())
        assertNull(HealthConnectManager.lastQueryTimestamp.value)
    }
}
