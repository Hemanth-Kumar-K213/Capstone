package com.womensafety.system.data.model

import java.util.UUID

/**
 * Data models for Smart Emergency Override & Sleep Protection in SLEEPING Safety Mode.
 */

data class SleepSession(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var durationMs: Long? = null,
    var quality: SleepQuality? = null,
    var emergencyWakeupsCount: Int = 0,
    var disruptions: Int = 0
)

enum class SleepQuality(val displayName: String, val iconEmoji: String) {
    DEEP("Deep Restful Sleep", "🌙"),
    LIGHT("Light Sleep", "⭐"),
    RESTLESS("Restless / Interrupted", "⚡"),
    POOR("Poor / High Disruptions", "⚠️")
}

data class EmergencyOverrideConfig(
    val userId: String = "",
    val enabledDuringNightHours: Boolean = true,
    val nightStartHour: Int = 22, // 10 PM
    val nightEndHour: Int = 6,    // 6 AM
    val overrideContacts: List<String> = emptyList(), // Contact phone numbers / IDs
    val callThresholdForAlert: Int = 3, // 3 calls within 1 minute
    val timeWindowForCallsMs: Long = 60_000L, // 1 minute
    val allowSOSOverride: Boolean = true,
    val allowTrustedContactSOS: Boolean = true
)

data class FallDetection(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val deviceMovement: Float = 0f, // Acceleration magnitude in m/s^2
    val confidence: Float = 0.85f,
    val alertSent: Boolean = false,
    val userConfirmed: Boolean = false
)

data class CriticalAlertData(
    val title: String = "CRITICAL EMERGENCY ALERT",
    val message: String = "Emergency override triggered during sleep mode.",
    val reason: String = "MULTIPLE_EMERGENCY_CALLS", // "MULTIPLE_EMERGENCY_CALLS", "TRUSTED_CONTACT_SOS", "UNRESPONSIVE_FALL"
    val callerName: String? = null
)
