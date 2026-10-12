package com.womensafety.system.data.model

/**
5:  * Model representing a proactive safety monitoring check event.
6:  * Logged to Firestore when the system detects an anomaly and prompts the user.
7:  */
data class RiskEvent(
    val id: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val riskScore: Int = 0,
    val activeAnomalies: List<String> = emptyList(),
    val safetyMode: String = "NORMAL",
    val status: String = "PENDING", // PENDING, SAFE, ESCALATED, EXPIRED
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "timestamp" to timestamp,
        "riskScore" to riskScore,
        "activeAnomalies" to activeAnomalies,
        "safetyMode" to safetyMode,
        "status" to status,
        "latitude" to latitude,
        "longitude" to longitude
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): RiskEvent {
            return RiskEvent(
                id = id,
                userId = map["userId"] as? String ?: "",
                timestamp = map["timestamp"] as? Long ?: System.currentTimeMillis(),
                riskScore = (map["riskScore"] as? Long)?.toInt() ?: 0,
                activeAnomalies = map["activeAnomalies"] as? List<String> ?: emptyList(),
                safetyMode = map["safetyMode"] as? String ?: "NORMAL",
                status = map["status"] as? String ?: "PENDING",
                latitude = map["latitude"] as? Double,
                longitude = map["longitude"] as? Double
            )
        }
    }
}
