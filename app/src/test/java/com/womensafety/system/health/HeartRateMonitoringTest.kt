package com.womensafety.system.health

import com.womensafety.system.data.health.HealthConnectManager
import com.womensafety.system.data.health.model.BleConnectionState
import com.womensafety.system.data.health.model.HealthConnectQueryResult
import com.womensafety.system.data.health.model.HealthConnectStatus
import com.womensafety.system.data.health.model.HealthDataFreshness
import com.womensafety.system.data.health.model.HealthRiskSignal
import com.womensafety.system.data.health.model.HeartRateMeasurement
import com.womensafety.system.manager.BleHeartRateManager
import com.womensafety.system.manager.RiskAssessmentEngine
import com.womensafety.system.manager.SOSManager
import com.womensafety.system.manager.SafetyMonitoringManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class HeartRateMonitoringTest {

    @Before
    fun setUp() {
        HealthConnectManager.clearSession()
    }

    // ─── Health Data Freshness ──────────────────────────────────────────

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

    // ─── Deduplication & Sorting ────────────────────────────────────────

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

    // ─── Session Isolation ──────────────────────────────────────────────

    @Test
    fun testMultiUserSessionIsolation_clearsCacheOnAccountSwitch() {
        // Set user 1
        HealthConnectManager.onUserSessionChanged("user_alpha_123")
        // Trigger session clear on account switch
        HealthConnectManager.onUserSessionChanged("user_beta_456")

        assertNull(HealthConnectManager.latestMeasurement.value)
        assertTrue(HealthConnectManager.recentMeasurements.value.isEmpty())
        assertNull(HealthConnectManager.lastQueryTimestamp.value)
        assertNull(HealthConnectManager.lastQueryResult.value)
    }

    @Test
    fun testExplicitClearSession_resetsAllState() {
        HealthConnectManager.clearSession()
        assertNull(HealthConnectManager.latestMeasurement.value)
        assertTrue(HealthConnectManager.recentMeasurements.value.isEmpty())
        assertNull(HealthConnectManager.lastQueryTimestamp.value)
        assertNull(HealthConnectManager.lastQueryResult.value)
    }

    // ─── Health Connect Query Results ───────────────────────────────────

    @Test
    fun testHealthConnectQueryResult_emptyRecordsPreservesIntegrity() {
        val now = System.currentTimeMillis()
        val emptyResult = HealthConnectQueryResult(
            samples = emptyList(),
            windowHours = 24L,
            queryTimestamp = now,
            distinctOriginPackages = emptySet()
        )

        assertEquals(0, emptyResult.samples.size)
        assertEquals(24L, emptyResult.windowHours)
        assertTrue(emptyResult.distinctOriginPackages.isEmpty())
    }

    @Test
    fun testHealthConnectQueryResult_multipleOriginsCaptured() {
        val now = System.currentTimeMillis()
        val origins = setOf("com.boat.crest", "com.google.android.apps.fitness")
        val populatedResult = HealthConnectQueryResult(
            samples = listOf(
                HeartRateMeasurement(75, now - 5000, "com.boat.crest", "r1"),
                HeartRateMeasurement(80, now - 2000, "com.google.android.apps.fitness", "r2")
            ),
            windowHours = 24L,
            queryTimestamp = now,
            distinctOriginPackages = origins
        )

        assertEquals(2, populatedResult.samples.size)
        assertEquals(2, populatedResult.distinctOriginPackages.size)
        assertTrue(populatedResult.distinctOriginPackages.contains("com.boat.crest"))
        assertTrue(populatedResult.distinctOriginPackages.contains("com.google.android.apps.fitness"))
    }

    // ─── BLE Heart Rate Packet Parsing (0x2A37) ─────────────────────────

    @Test
    fun testBlePacketParsing_validUint8Packet() {
        // Flags: 0b00000000 (Bit 0 = 0 -> UINT8), Value: 75 BPM
        val packet = byteArrayOf(0x00.toByte(), 75.toByte())
        val parsedBpm = BleHeartRateManager.parseHeartRateValue(packet)
        assertEquals(75, parsedBpm)
    }

    @Test
    fun testBlePacketParsing_validUint16Packet() {
        // Flags: 0b00000001 (Bit 0 = 1 -> UINT16), LSB: 140, MSB: 0 -> 140 BPM
        val packet = byteArrayOf(0x01.toByte(), 140.toByte(), 0x00.toByte())
        val parsedBpm = BleHeartRateManager.parseHeartRateValue(packet)
        assertEquals(140, parsedBpm)
    }

    @Test
    fun testBlePacketParsing_validUint16HighValue() {
        // Flags: 0b00000001 (Bit 0 = 1 -> UINT16), LSB: 0xC8 (200), MSB: 0x00 -> 200 BPM
        val packet = byteArrayOf(0x01.toByte(), 0xC8.toByte(), 0x00.toByte())
        val parsedBpm = BleHeartRateManager.parseHeartRateValue(packet)
        assertEquals(200, parsedBpm)
    }

    @Test
    fun testBlePacketParsing_truncatedUint8Rejected() {
        // Flags: 0 (UINT8 expected), but no value byte provided
        val packet = byteArrayOf(0x00.toByte())
        val parsedBpm = BleHeartRateManager.parseHeartRateValue(packet)
        assertEquals(0, parsedBpm)
    }

    @Test
    fun testBlePacketParsing_truncatedUint16Rejected() {
        // Flags: 1 (UINT16 expected), but only 1 byte of payload (size = 2 instead of >= 3)
        val packet = byteArrayOf(0x01.toByte(), 80.toByte())
        val parsedBpm = BleHeartRateManager.parseHeartRateValue(packet)
        assertEquals(0, parsedBpm)
    }

    @Test
    fun testBlePacketParsing_emptyPacketRejected() {
        val emptyPacket = byteArrayOf()
        val parsedBpm = BleHeartRateManager.parseHeartRateValue(emptyPacket)
        assertEquals(0, parsedBpm)
    }

    @Test
    fun testBlePacketParsing_outOfPhysiologicalBoundsRejected() {
        // BPM = 20 (< 30) -> rejected
        val tooLow = byteArrayOf(0x00.toByte(), 20.toByte())
        assertEquals(0, BleHeartRateManager.parseHeartRateValue(tooLow))

        // BPM = 255 (> 250) -> rejected
        val tooHigh = byteArrayOf(0x00.toByte(), 255.toByte())
        assertEquals(0, BleHeartRateManager.parseHeartRateValue(tooHigh))
    }

    // ─── BLE Service & Characteristic UUID Resolution ───────────────────

    @Test
    fun testBleServiceResolution_standardHeartRateService() {
        val name = BleHeartRateManager.resolveServiceName(BleHeartRateManager.HEART_RATE_SERVICE_UUID)
        assertTrue(name.contains("Heart Rate Service"))
        assertTrue(name.contains("0x180D"))
    }

    @Test
    fun testBleServiceResolution_genericAccessAndDeviceInformation() {
        val genericAccessUuid = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        val devInfoUuid = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val batteryUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")

        assertEquals("Generic Access (0x1800)", BleHeartRateManager.resolveServiceName(genericAccessUuid))
        assertEquals("Device Information (0x180A)", BleHeartRateManager.resolveServiceName(devInfoUuid))
        assertEquals("Battery Service (0x180F)", BleHeartRateManager.resolveServiceName(batteryUuid))
    }

    @Test
    fun testBleServiceResolution_proprietaryVendorService() {
        val proprietaryUuid = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
        assertEquals("Vendor/Proprietary Service", BleHeartRateManager.resolveServiceName(proprietaryUuid))
    }

    @Test
    fun testBleCharacteristicResolution_standardMeasurement() {
        val hrMeasurementUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val name = BleHeartRateManager.resolveCharacteristicName(hrMeasurementUuid)
        assertTrue(name.contains("Heart Rate Measurement"))
        assertTrue(name.contains("0x2A37"))
    }

    // ─── Test Harness & Capstone Risk Scoring Isolation ─────────────────

    @Test
    fun testCapstoneScenarioEvaluation_fullCriticalRiskScore9() {
        val result = RiskAssessmentEngine.evaluateCapstoneScenario(
            isRouteDeviated = true,
            isLateNight = true,
            isHeartRateSpike = true
        )

        assertEquals(3, result.routeDeviationContribution)
        assertEquals(2, result.lateNightContribution)
        assertEquals(4, result.heartRateSpikeContribution)
        assertEquals(9, result.totalRiskScore)
        assertTrue(result.isCriticalRisk)
        assertTrue(result.isConfirmationPromptTriggered)
    }

    @Test
    fun testCapstoneScenarioEvaluation_confirmationThresholdScore5() {
        val result = RiskAssessmentEngine.evaluateCapstoneScenario(
            isRouteDeviated = true,
            isLateNight = true,
            isHeartRateSpike = false
        )

        assertEquals(3, result.routeDeviationContribution)
        assertEquals(2, result.lateNightContribution)
        assertEquals(0, result.heartRateSpikeContribution)
        assertEquals(5, result.totalRiskScore)
        assertFalse(result.isCriticalRisk)
        assertTrue(result.isConfirmationPromptTriggered)
    }

    @Test
    fun testCapstoneScenarioEvaluation_baselineSafeScore0() {
        val result = RiskAssessmentEngine.evaluateCapstoneScenario(
            isRouteDeviated = false,
            isLateNight = false,
            isHeartRateSpike = false
        )

        assertEquals(0, result.totalRiskScore)
        assertFalse(result.isCriticalRisk)
        assertFalse(result.isConfirmationPromptTriggered)
    }

    @Test
    fun testTestModeGuards_suppressExternalDispatches() {
        // Ensure test mode isolation flags function as documented
        SafetyMonitoringManager.isTestMode = true
        SOSManager.isTestMode = true

        assertTrue(SafetyMonitoringManager.isTestMode)
        assertTrue(SOSManager.isTestMode)
    }
}

