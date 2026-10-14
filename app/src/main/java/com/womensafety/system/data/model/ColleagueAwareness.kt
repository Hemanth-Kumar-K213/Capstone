package com.womensafety.system.data.model

import java.util.UUID

/**
 * Data models for Colleague Awareness & Late-Stay Protection in WORKING Safety Mode.
 */

data class Colleague(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String = "Colleague",
    val workplaceName: String = "Office / Campus",
    val lastSeen: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isTrustedContact: Boolean = false,
    val isSafetyBuddy: Boolean = false,
    val currentStatus: ColleagueStatus = ColleagueStatus.AT_WORKPLACE,
    val phone: String? = null,
    val distanceMeters: Float? = null,
    val rssi: Int? = null,
    val dataSource: String = "BLE" // "BLE", "Geofence", "Firestore", "Simulator"
)

enum class ColleagueStatus(val displayName: String, val iconEmoji: String) {
    AT_WORKPLACE("At Workplace", "🏢"),
    LEAVING("Leaving Soon", "🚶"),
    LEFT("Departed", "🚗"),
    AWAY_FROM_DESK("Away from Desk", "⏳"),
    ON_BREAK("On Break", "☕")
}

data class SafetyBuddyAgreement(
    val id: String = UUID.randomUUID().toString(),
    val user1Id: String = "",
    val user2Id: String = "",
    val workplaceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val mutualCheckIns: Boolean = true,
    val automatedAlerts: Boolean = true,
    val status: String = "ACTIVE", // PENDING_ACCEPTANCE, ACTIVE, DECLINED, REVOKED
    val initiatedBy: String = "",
    val initiatedAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null,
    val sharedTransportNotif: Boolean = true,
    val eveningCheckIn: Boolean = true
)

data class BuddyRequest(
    val id: String = UUID.randomUUID().toString(),
    val agreementId: String = "",
    val fromUserId: String = "",
    val fromName: String = "",
    val fromPhone: String = "",
    val workplaceId: String = "",
    val receivedAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING_ACCEPTANCE"
)

data class LateStayAlert(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val workplaceId: String = "",
    val estimatedDeparture: Long = System.currentTimeMillis() + (60 * 60 * 1000L), // +1 hour
    val reason: String? = null,
    val emergencyContactsNotified: Boolean = false,
    val transportArranged: Boolean = false,
    val transportType: TransportType? = null
)

enum class TransportType(val displayName: String, val iconEmoji: String) {
    OWN_VEHICLE("Personal Vehicle", "🛵"),
    COLLEAGUE_SHARED("Shared with Colleague", "👥"),
    COMPANY_CAB("Company Security Cab", "🚕"),
    RIDESHARE_OLA("Ola Verified Cab", "🚖"),
    RIDESHARE_UBER("Uber Safety Shield", "🚗"),
    PUBLIC_TRANSIT("Public Metro / Bus", "🚇")
}

sealed class TransportSuggestion(
    val title: String,
    val description: String,
    val transportType: TransportType,
    val etaMinutes: Int,
    val isSafetyVerified: Boolean = true
) {
    data class SharedWithBuddy(val buddy: Colleague) : TransportSuggestion(
        title = "Share Ride with Safety Buddy (${buddy.name})",
        description = "Departing in 15 mins • Verified coworker mutual monitoring",
        transportType = TransportType.COLLEAGUE_SHARED,
        etaMinutes = 15,
        isSafetyVerified = true
    )

    data class SharedWithColleague(val colleague: Colleague) : TransportSuggestion(
        title = "Carpool with ${colleague.name}",
        description = "Leaving now • Verified colleague traveling in your direction",
        transportType = TransportType.COLLEAGUE_SHARED,
        etaMinutes = 10,
        isSafetyVerified = true
    )

    object CompanyCab : TransportSuggestion(
        title = "Official Company Security Escort Cab",
        description = "Security guard escorted late-night drop with GPS beacon",
        transportType = TransportType.COMPANY_CAB,
        etaMinutes = 12,
        isSafetyVerified = true
    )

    data class RideShare(val provider: String) : TransportSuggestion(
        title = "$provider Verified Ride (24/7 Safety)",
        description = "Ride tracking auto-shared with emergency contacts & trusted circle",
        transportType = if (provider.contains("Ola", ignoreCase = true)) TransportType.RIDESHARE_OLA else TransportType.RIDESHARE_UBER,
        etaMinutes = 6,
        isSafetyVerified = true
    )
}
