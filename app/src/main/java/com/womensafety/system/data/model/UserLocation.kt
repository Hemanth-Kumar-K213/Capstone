package com.womensafety.system.data.model

import com.google.firebase.firestore.Exclude

/**
 * Represents a single GPS location coordinate fix for a user.
 */
data class UserLocation(
    val id: String = "",
    val userId: String = "",
    val uid: String = "",
    val userName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val bearing: Float? = null,
    val altitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float? = null,
    val address: String? = null,
    val isSOSLocation: Boolean = false
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "uid" to uid,
            "userName" to userName,
            "latitude" to latitude,
            "longitude" to longitude,
            "accuracy" to accuracy,
            "bearing" to bearing,
            "altitude" to altitude,
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
                uid = map["uid"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0,
                accuracy = (map["accuracy"] as? Number)?.toFloat() ?: 0f,
                bearing = (map["bearing"] as? Number)?.toFloat(),
                altitude = (map["altitude"] as? Number)?.toDouble(),
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                speed = (map["speed"] as? Number)?.toFloat(),
                address = map["address"] as? String,
                isSOSLocation = map["isSOSLocation"] as? Boolean ?: false
            )
        }
    }
}
