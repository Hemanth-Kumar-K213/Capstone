package com.womensafety.system.safety

import com.womensafety.system.data.model.SleepQuality
import com.womensafety.system.data.model.SleepSession
import com.womensafety.system.data.repository.SleepModeRepository
import org.junit.Assert.*
import org.junit.Test

class EmergencyOverrideTest {

    private val repository = SleepModeRepository()

    @Test
    fun testDeepSleepQualityEvaluation() {
        val session = SleepSession(
            startTime = System.currentTimeMillis() - (8 * 60 * 60 * 1000L), // 8 hours ago
            endTime = System.currentTimeMillis(),
            durationMs = 8 * 60 * 60 * 1000L,
            disruptions = 0
        )

        val quality = repository.assessSleepQuality(session)
        assertEquals(SleepQuality.DEEP, quality)
    }

    @Test
    fun testRestlessSleepQualityWithDisruptions() {
        val session = SleepSession(
            startTime = System.currentTimeMillis() - (6 * 60 * 60 * 1000L),
            endTime = System.currentTimeMillis(),
            durationMs = 6 * 60 * 60 * 1000L,
            disruptions = 4 // Multiple wakeups/disruptions
        )

        val quality = repository.assessSleepQuality(session)
        assertEquals(SleepQuality.RESTLESS, quality)
    }

    @Test
    fun testShortPoorSleepQuality() {
        val session = SleepSession(
            startTime = System.currentTimeMillis() - (60 * 60 * 1000L), // Only 1 hour
            endTime = System.currentTimeMillis(),
            durationMs = 60 * 60 * 1000L,
            disruptions = 1
        )

        val quality = repository.assessSleepQuality(session)
        assertEquals(SleepQuality.POOR, quality)
    }
}
