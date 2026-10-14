package com.womensafety.system.data.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * Data models for Routine-Based Anomaly Detection in REGULAR Safety Mode.
 */

data class DailyRoutine(
    val userId: String = "",
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val typicalLocations: List<LocationPattern> = emptyList(),
    val confidenceScore: Float = 0.85f, // 0.0 - 1.0 based on historical consistency
    val lastUpdated: Long = System.currentTimeMillis()
)

data class LocationPattern(
    val name: String = "Location", // "Home", "Office / College", "Study Cafe", "Gym", etc.
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Float = 150f,
    val averageTimeSpentMinutes: Int = 120,
    val frequency: Int = 5, // occurrences per week
    val typicalTimeRange: TimeRange = TimeRange(9, 0, 17, 0),
    val confidenceScore: Float = 0.85f
)

data class TimeRange(
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 17,
    val endMinute: Int = 0
) {
    fun contains(hour: Int, minute: Int): Boolean {
        val currentMinutes = hour * 60 + minute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            // Spans overnight (e.g. 22:00 to 07:00)
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    fun contains(time: LocalTime): Boolean {
        return contains(time.hour, time.minute)
    }

    fun displayString(): String {
        val formatTime = { h: Int, m: Int ->
            val amPm = if (h >= 12) "PM" else "AM"
            val displayHour = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            String.format(java.util.Locale.US, "%02d:%02d %s", displayHour, m, amPm)
        }
        return "${formatTime(startHour, startMinute)} - ${formatTime(endHour, endMinute)}"
    }
}

enum class AnomalyType(val displayName: String) {
    UNUSUAL_LOCATION("Unusual Location"),
    UNUSUAL_TIME("Unusual Time"),
    UNUSUAL_BOTH("Unusual Location & Time")
}

enum class AnomalySeverity(val displayName: String) {
    LOW("Low Concern"),
    MEDIUM("Moderate Concern"),
    HIGH("High Risk")
}

data class AnomalyDetected(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val currentAddress: String = "Resolving Address...",
    val expectedLocation: String? = null,
    val anomalyType: AnomalyType = AnomalyType.UNUSUAL_LOCATION,
    val confidenceScore: Float = 0.75f, // 0.0 - 1.0
    val severity: AnomalySeverity = AnomalySeverity.MEDIUM,
    val userResponded: Boolean = false,
    val userResponse: String? = null // "Safe", "At Doctor", "Regular Place", "Emergency SOS"
)
