package com.womensafety.system.data.model

/**
 * Supported shake sensitivity thresholds in m/s².
 */
enum class ShakeSensitivity(val threshold: Float, val displayName: String, val description: String) {
    LOW(20f, "Low (Hard Shake)", "Requires a forceful shake to trigger. Best to prevent accidental triggers."),
    MEDIUM(15f, "Medium (Moderate Shake)", "Balanced sensitivity suitable for most everyday situations."),
    HIGH(12f, "High (Light Shake)", "Triggers quickly with minimal effort. Recommended in high-risk zones.");

    companion object {
        fun fromString(name: String): ShakeSensitivity {
            return try {
                valueOf(name.uppercase())
            } catch (e: Exception) {
                MEDIUM
            }
        }
    }
}
