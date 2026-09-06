package com.womensafety.system.data.model

/**
 * Defines the four operating safety modes in Trinetra.
 */
enum class SafetyMode(val displayName: String, val iconEmoji: String) {
    REGULAR("Regular", "🏠"),
    TRAVELLING("Travelling", "🚗"),
    WORKING("Working/College", "💼"),
    SLEEPING("Sleeping", "😴");

    /**
     * Centralized configuration configuration defining monitoring rules for each mode.
     */
    val config: SafetyModeConfig
        get() = when (this) {
            REGULAR -> SafetyModeConfig(
                isGPSActive = true,
                isDeviationMonitoringActive = false,
                isStopDetectionActive = false,
                isShakeDetectionActive = true,
                shakeSensitivityOverride = null, // Uses user-defined default
                description = "Balanced safety monitoring for everyday use."
            )
            TRAVELLING -> SafetyModeConfig(
                isGPSActive = true,
                isDeviationMonitoringActive = true,
                isStopDetectionActive = true,
                isShakeDetectionActive = true,
                shakeSensitivityOverride = "HIGH", // Higher sensitivity during travel
                description = "Active route deviation, stops, and travel tracking."
            )
            WORKING -> SafetyModeConfig(
                isGPSActive = true,
                isDeviationMonitoringActive = false,
                isStopDetectionActive = false,
                isShakeDetectionActive = true,
                shakeSensitivityOverride = "LOW", // Lower sensitivity at work to avoid false alerts
                description = "Monitors safety around your saved workplace or college."
            )
            SLEEPING -> SafetyModeConfig(
                isGPSActive = false, // Stop passive location updates
                isDeviationMonitoringActive = false,
                isStopDetectionActive = false,
                isShakeDetectionActive = false, // Ignore shake events
                shakeSensitivityOverride = null,
                description = "Reduced alerts while sleeping. SOS and contacts remain available."
            )
        }

    companion object {
        fun fromString(value: String): SafetyMode {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                REGULAR
            }
        }
    }
}

/**
 * Settings configuration data class for safety modes.
 */
data class SafetyModeConfig(
    val isGPSActive: Boolean,
    val isDeviationMonitoringActive: Boolean,
    val isStopDetectionActive: Boolean,
    val isShakeDetectionActive: Boolean,
    val shakeSensitivityOverride: String?,
    val description: String
)
