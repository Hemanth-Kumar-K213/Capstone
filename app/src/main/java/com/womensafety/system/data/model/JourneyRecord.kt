package com.womensafety.system.data.model

import com.womensafety.system.data.model.UserLocation

/**
 * Model representing a completed or escalated Travel Mode journey.
 * Saved to Firestore for travel history purposes.
 */
data class JourneyRecord(
    val id: String = "",
    val userId: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    val destLatitude: Double = 0.0,
    val destLongitude: Double = 0.0,
    val status: String = "COMPLETED", // COMPLETED, ESCALATED, CANCELLED
    val routePoints: List<UserLocation> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "startTime" to startTime,
        "endTime" to endTime,
        "startLatitude" to startLatitude,
        "startLongitude" to startLongitude,
        "destLatitude" to destLatitude,
        "destLongitude" to destLongitude,
        "status" to status,
        "routePoints" to routePoints.map { it.toMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): JourneyRecord {
            val rawPoints = map["routePoints"] as? List<Map<String, Any?>> ?: emptyList()
            return JourneyRecord(
                id = id,
                userId = map["userId"] as? String ?: "",
                startTime = (map["startTime"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                endTime = (map["endTime"] as? Number)?.toLong(),
                startLatitude = (map["startLatitude"] as? Number)?.toDouble() ?: 0.0,
                startLongitude = (map["startLongitude"] as? Number)?.toDouble() ?: 0.0,
                destLatitude = (map["destLatitude"] as? Number)?.toDouble() ?: 0.0,
                destLongitude = (map["destLongitude"] as? Number)?.toDouble() ?: 0.0,
                status = map["status"] as? String ?: "COMPLETED",
                routePoints = rawPoints.map { UserLocation.fromMap(it["id"] as? String ?: "", it) }
            )
        }
    }
}
