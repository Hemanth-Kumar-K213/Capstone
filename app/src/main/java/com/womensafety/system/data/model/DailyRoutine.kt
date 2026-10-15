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

/**
 * Routine location schema matching Firestore routines/{userId}/{dayOfWeek}/{locationId}
 */
data class RoutineLocation(
    val locationId: String = UUID.randomUUID().toString(),
    val name: String = "Location",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val radiusMeters: Float = 100f,
    val visitFrequency: Int = 1, // times visited in 30 days
    val typicalArrivalTime: String = "21:30",
    val typicalDepartureTime: String = "07:00",
    val confidence: Float = 0.90f,
    val learnedAt: Long = System.currentTimeMillis(),
    val source: String = "auto-learned" // "auto-learned" or "manual"
) {
    fun toLocationPattern(): LocationPattern {
        val arrParts = typicalArrivalTime.split(":")
        val depParts = typicalDepartureTime.split(":")
        val startH = arrParts.getOrNull(0)?.trim()?.toIntOrNull() ?: 9
        val startM = arrParts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        val endH = depParts.getOrNull(0)?.trim()?.toIntOrNull() ?: 17
        val endM = depParts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0

        val startTotal = startH * 60 + startM
        val endTotal = endH * 60 + endM
        val duration = if (endTotal >= startTotal) (endTotal - startTotal) else (24 * 60 - startTotal + endTotal)

        return LocationPattern(
            name = name,
            latitude = lat,
            longitude = lon,
            radiusMeters = radiusMeters,
            averageTimeSpentMinutes = duration,
            frequency = visitFrequency,
            typicalTimeRange = TimeRange(startH, startM, endH, endM),
            confidenceScore = confidence
        )
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "locationId" to locationId,
        "name" to name,
        "lat" to lat,
        "lon" to lon,
        "latitude" to lat,
        "longitude" to lon,
        "radiusMeters" to radiusMeters,
        "visitFrequency" to visitFrequency,
        "typicalArrivalTime" to typicalArrivalTime,
        "typicalDepartureTime" to typicalDepartureTime,
        "confidence" to confidence,
        "learnedAt" to learnedAt,
        "source" to source
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): RoutineLocation {
            return RoutineLocation(
                locationId = map["locationId"] as? String ?: id,
                name = map["name"] as? String ?: "Location",
                lat = (map["lat"] as? Number)?.toDouble() ?: (map["latitude"] as? Number)?.toDouble() ?: 0.0,
                lon = (map["lon"] as? Number)?.toDouble() ?: (map["longitude"] as? Number)?.toDouble() ?: 0.0,
                radiusMeters = (map["radiusMeters"] as? Number)?.toFloat() ?: 100f,
                visitFrequency = (map["visitFrequency"] as? Number)?.toInt() ?: 1,
                typicalArrivalTime = map["typicalArrivalTime"] as? String ?: "09:00",
                typicalDepartureTime = map["typicalDepartureTime"] as? String ?: "17:00",
                confidence = (map["confidence"] as? Number)?.toFloat() ?: 0.85f,
                learnedAt = (map["learnedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                source = map["source"] as? String ?: "auto-learned"
            )
        }
    }
}

data class AutoLearnStatus(
    val lastAutoLearnTime: Long = 0L,
    val locationsDiscovered: Int = 0,
    val nextScheduledUpdate: Long = 0L,
    val isCollecting: Boolean = false,
    val collectionDay: Int = 1,
    val message: String = "Routine learning active"
)

