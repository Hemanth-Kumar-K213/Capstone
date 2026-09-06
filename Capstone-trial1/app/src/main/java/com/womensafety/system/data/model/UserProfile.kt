package com.womensafety.system.data.model

/**
 * User profile stored in Firestore under users/{authUserId}.
 *
 * The [authUserId] is the permanent Firebase Authentication UID —
 * the sole internal identifier for this user across all collections.
 *
 * [currentWeeklyUid] is set by Cloud Functions when a new UID is generated/rotated.
 * The Android client reads this to know which weeklyUids/{uid} document to fetch.
 *
 * [trustedContactIds] tracks which TC auth UIDs have relationships with this user.
 * Used to iterate and fetch individual relationship documents.
 */
data class UserProfile(
    val authUserId: String = "",
    val name: String = "",
    val primaryEmail: String = "",
    val phone: String = "",
    val accountType: String = AccountType.USER.name,
    val createdAt: Long = System.currentTimeMillis(),
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val currentWeeklyUid: String = "",
    val trustedContactIds: List<String> = emptyList(),
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val setupCompleted: Boolean = false,
    val locationTrackingEnabled: Boolean = false,
    val shakeDetectionEnabled: Boolean = false,
    val shakeSensitivity: String = "MEDIUM",
    val sosAutoCall: Boolean = false,
    val proactiveMonitoringEnabled: Boolean = false,
    val safetyMode: String = "NORMAL",
    val routeDeviationToleranceMeters: Int = 300,
    val fcmToken: String? = null,
    val activeSafetyMode: String = "REGULAR",
    val defaultSafetyMode: String = "REGULAR",
    val autoModeSuggestionsEnabled: Boolean = true,
    val savedWorkplaceLatitude: Double? = null,
    val savedWorkplaceLongitude: Double? = null,
    val savedWorkplaceAddress: String? = null,
    val savedHomeLatitude: Double? = null,
    val savedHomeLongitude: Double? = null,
    val savedHomeAddress: String? = null
) {
    /** No-arg constructor required by Firestore deserialization. */
    constructor() : this(authUserId = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "authUserId" to authUserId,
        "name" to name,
        "primaryEmail" to primaryEmail,
        "phone" to phone,
        "accountType" to accountType,
        "createdAt" to createdAt,
        "emailVerified" to emailVerified,
        "phoneVerified" to phoneVerified,
        "currentWeeklyUid" to currentWeeklyUid,
        "trustedContactIds" to trustedContactIds,
        "emergencyContacts" to emergencyContacts.map { it.toMap() },
        "setupCompleted" to setupCompleted,
        "locationTrackingEnabled" to locationTrackingEnabled,
        "shakeDetectionEnabled" to shakeDetectionEnabled,
        "shakeSensitivity" to shakeSensitivity,
        "sosAutoCall" to sosAutoCall,
        "proactiveMonitoringEnabled" to proactiveMonitoringEnabled,
        "safetyMode" to safetyMode,
        "routeDeviationToleranceMeters" to routeDeviationToleranceMeters,
        "fcmToken" to fcmToken,
        "activeSafetyMode" to activeSafetyMode,
        "defaultSafetyMode" to defaultSafetyMode,
        "autoModeSuggestionsEnabled" to autoModeSuggestionsEnabled,
        "savedWorkplaceLatitude" to savedWorkplaceLatitude,
        "savedWorkplaceLongitude" to savedWorkplaceLongitude,
        "savedWorkplaceAddress" to savedWorkplaceAddress,
        "savedHomeLatitude" to savedHomeLatitude,
        "savedHomeLongitude" to savedHomeLongitude,
        "savedHomeAddress" to savedHomeAddress
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): UserProfile {
            val rawContacts = map["emergencyContacts"] as? List<Map<String, Any?>> ?: emptyList()
            return UserProfile(
                authUserId = map["authUserId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                primaryEmail = map["primaryEmail"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                accountType = map["accountType"] as? String ?: AccountType.USER.name,
                createdAt = map["createdAt"] as? Long ?: System.currentTimeMillis(),
                emailVerified = map["emailVerified"] as? Boolean ?: false,
                phoneVerified = map["phoneVerified"] as? Boolean ?: false,
                currentWeeklyUid = map["currentWeeklyUid"] as? String ?: "",
                trustedContactIds = map["trustedContactIds"] as? List<String> ?: emptyList(),
                emergencyContacts = rawContacts.map { EmergencyContact.fromMap(it) },
                setupCompleted = map["setupCompleted"] as? Boolean ?: false,
                locationTrackingEnabled = map["locationTrackingEnabled"] as? Boolean ?: false,
                shakeDetectionEnabled = map["shakeDetectionEnabled"] as? Boolean ?: false,
                shakeSensitivity = map["shakeSensitivity"] as? String ?: "MEDIUM",
                sosAutoCall = map["sosAutoCall"] as? Boolean ?: false,
                proactiveMonitoringEnabled = map["proactiveMonitoringEnabled"] as? Boolean ?: false,
                safetyMode = map["safetyMode"] as? String ?: "NORMAL",
                routeDeviationToleranceMeters = (map["routeDeviationToleranceMeters"] as? Number)?.toInt() ?: 300,
                fcmToken = map["fcmToken"] as? String,
                activeSafetyMode = map["activeSafetyMode"] as? String ?: "REGULAR",
                defaultSafetyMode = map["defaultSafetyMode"] as? String ?: "REGULAR",
                autoModeSuggestionsEnabled = map["autoModeSuggestionsEnabled"] as? Boolean ?: true,
                savedWorkplaceLatitude = (map["savedWorkplaceLatitude"] as? Number)?.toDouble(),
                savedWorkplaceLongitude = (map["savedWorkplaceLongitude"] as? Number)?.toDouble(),
                savedWorkplaceAddress = map["savedWorkplaceAddress"] as? String,
                savedHomeLatitude = (map["savedHomeLatitude"] as? Number)?.toDouble(),
                savedHomeLongitude = (map["savedHomeLongitude"] as? Number)?.toDouble(),
                savedHomeAddress = map["savedHomeAddress"] as? String
            )
        }
    }
}
