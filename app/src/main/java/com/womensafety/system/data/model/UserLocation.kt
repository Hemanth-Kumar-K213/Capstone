package com.womensafety.system.data.model

import com.google.firebase.firestore.Exclude

/**
 * Represents a single GPS location coordinate fix for a user.
 */
data class UserLocation(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float? = null,
    val address: String? = null,
    val isSOSLocation: Boolean = false
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "userName" to userName,
            "latitude" to latitude,
            "longitude" to longitude,
            "accuracy" to accuracy,
            "timestamp" to timestamp,
            "speed" to speed,
            "address" to address,
            "isSOSLocation" to isSOSLocation
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): UserLocation {
            return UserLocation(
                id = id,
                userId = map["userId"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0,
                accuracy = (map["accuracy"] as? Number)?.toFloat() ?: 0f,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                speed = (map["speed"] as? Number)?.toFloat(),
                address = map["address"] as? String,
                isSOSLocation = map["isSOSLocation"] as? Boolean ?: false
            )
        }
    }
}
