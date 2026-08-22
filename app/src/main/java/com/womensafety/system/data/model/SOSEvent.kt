package com.womensafety.system.data.model

import java.util.UUID

/**
 * Lifecycle status of an emergency SOS event.
 */
enum class SOSStatus {
    ACTIVE,        // SOS is currently ongoing
    RESOLVED,      // User marked as safe
    FALSE_ALARM,   // User cancelled as accidental
    AUTO_RESOLVED  // Automatically resolved after 5-minute timeout
}

/**
 * The input mechanism that initiated the SOS alert.
 */
enum class SOSTriggerMethod {
    BUTTON,        // Manual SOS button press
    SHAKE,         // Shake detection sensor
    AUTO,          // Automated sensor trigger
    VOICE          // Voice command trigger
}

/**
 * Data model for emergency SOS incidents.
 */
data class SOSEvent(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val userName: String = "",
    val triggeredAt: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val address: String? = null,
    val status: SOSStatus = SOSStatus.ACTIVE,
    val resolvedAt: Long? = null,
    val triggerMethod: SOSTriggerMethod = SOSTriggerMethod.BUTTON,
    val notificationsSent: Int = 0,
    val smsSent: Int = 0,
    val callMade: Boolean = false,
    val fcmTokensSent: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "userName" to userName,
            "triggeredAt" to triggeredAt,
            "latitude" to latitude,
            "longitude" to longitude,
            "accuracy" to accuracy,
            "address" to address,
            "status" to status.name,
            "resolvedAt" to resolvedAt,
            "triggerMethod" to triggerMethod.name,
            "notificationsSent" to notificationsSent,
            "smsSent" to smsSent,
            "callMade" to callMade,
            "fcmTokensSent" to fcmTokensSent
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): SOSEvent {
            return SOSEvent(
                id = id,
                userId = map["userId"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                triggeredAt = (map["triggeredAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                latitude = (map["latitude"] as? Number)?.toDouble(),
                longitude = (map["longitude"] as? Number)?.toDouble(),
                accuracy = (map["accuracy"] as? Number)?.toFloat(),
                address = map["address"] as? String,
                status = try {
                    SOSStatus.valueOf(map["status"] as? String ?: SOSStatus.ACTIVE.name)
                } catch (e: Exception) {
                    SOSStatus.ACTIVE
                },
                resolvedAt = (map["resolvedAt"] as? Number)?.toLong(),
                triggerMethod = try {
                    SOSTriggerMethod.valueOf(map["triggerMethod"] as? String ?: SOSTriggerMethod.BUTTON.name)
                } catch (e: Exception) {
                    SOSTriggerMethod.BUTTON
                },
                notificationsSent = (map["notificationsSent"] as? Number)?.toInt() ?: 0,
                smsSent = (map["smsSent"] as? Number)?.toInt() ?: 0,
                callMade = map["callMade"] as? Boolean ?: false,
                fcmTokensSent = (map["fcmTokensSent"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }
}
