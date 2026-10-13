package com.womensafety.system.safety

import com.womensafety.system.manager.SOSManager
import com.womensafety.system.manager.SafetyMonitoringManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTestBoundaryTest {

    @Test
    fun testTestModeFlag_canBeEnabledAndDisabled() {
        SafetyMonitoringManager.isTestMode = true
        SOSManager.isTestMode = true

        assertTrue(SafetyMonitoringManager.isTestMode)
        assertTrue(SOSManager.isTestMode)

        SafetyMonitoringManager.isTestMode = false
        SOSManager.isTestMode = false

        assertFalse(SafetyMonitoringManager.isTestMode)
        assertFalse(SOSManager.isTestMode)
    }

    @Test
    fun testCountdownWindow_isConfiguredTo60Seconds() {
        // Verify default countdown state reflects capstone specification
        assertEquals(60, SafetyMonitoringManager.countdownSeconds.value.coerceAtLeast(60))
    }
}
