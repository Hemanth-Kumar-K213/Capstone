package com.womensafety.system.data.health.model

import java.time.Instant

/**
 * Domain representation of an individual heart rate measurement sample.
 */
data class HeartRateMeasurement(
    val bpm: Int,
    val timestamp: Long, // Epoch milliseconds
    val dataOriginPackage: String,
    val recordId: String
) {
    /**
     * Calculates the age of this measurement in milliseconds relative to now.
     */
    fun ageMs(currentTime: Long = System.currentTimeMillis()): Long {
        return (currentTime - timestamp).coerceAtLeast(0L)
    }

    /**
     * Freshness determination:
     * - FRESH: < 10 minutes (600_000 ms)
     * - STALE: >= 10 minutes
     */
    fun freshness(currentTime: Long = System.currentTimeMillis()): HealthDataFreshness {
        return if (ageMs(currentTime) < 600_000L) {
            HealthDataFreshness.FRESH
        } else {
            HealthDataFreshness.STALE
        }
    }
}

/**
 * Categorical freshness state of physiological sensor data.
 */
enum class HealthDataFreshness {
    FRESH,       // < 10 minutes old
    STALE,       // >= 10 minutes old
    UNAVAILABLE  // No records available
}

/**
 * Android Health Connect SDK provider status.
 */
sealed class HealthConnectStatus {
    object Available : HealthConnectStatus()
    object UpdateRequired : HealthConnectStatus()
    object Unavailable : HealthConnectStatus()
    data class Error(val message: String) : HealthConnectStatus()
}

/**
 * Testable, decoupled physiological signal payload passed to the Risk Assessment Engine.
 */
data class HealthRiskSignal(
    val heartRateBpm: Int?,
    val timestamp: Long?,
    val sampleAgeMs: Long?,
    val freshness: HealthDataFreshness,
    val isAnomalousSpike: Boolean = false,
    val isObservationOnly: Boolean = true // Defaults to true (observation only; does not affect live risk score)
)

/**
 * Diagnostic summary of a completed Health Connect query operation.
 */
data class HealthConnectQueryResult(
    val samples: List<HeartRateMeasurement>,
    val windowHours: Long,
    val queryTimestamp: Long,
    val distinctOriginPackages: Set<String>
)

/**
 * Detailed representation of a BLE GATT service discovered on a connected wearable.
 */
data class BleDiscoveredService(
    val uuid: String,
    val name: String,
    val isStandardHeartRateService: Boolean = false,
    val characteristics: List<String> = emptyList()
)

/**
 * Truthful, granular lifecycle state of Direct BLE connectivity.
 */
sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    data class Connecting(val deviceName: String, val deviceAddress: String) : BleConnectionState()
    data class ConnectedDiscovering(val deviceName: String, val deviceAddress: String) : BleConnectionState()
    data class ConnectedUnsupported(
        val deviceName: String,
        val deviceAddress: String,
        val reason: String
    ) : BleConnectionState()
    data class ConnectedSubscribedWaiting(
        val deviceName: String,
        val deviceAddress: String
    ) : BleConnectionState()
    data class Streaming(
        val deviceName: String,
        val deviceAddress: String,
        val bpm: Int,
        val lastPacketTimestamp: Long
    ) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

