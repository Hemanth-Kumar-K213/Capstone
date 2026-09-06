package com.womensafety.system.data.model

/**
 * Represents a detected accelerometer shake motion.
 */
data class ShakeEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val accelerationMagnitude: Float = 0f,
    val threshold: Float = 0f,
    val triggeredSOS: Boolean = false
)
