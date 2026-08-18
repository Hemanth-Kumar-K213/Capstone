package com.womensafety.system.data.model

/**
 * Trusted Contact profile stored in Firestore under trustedContacts/{authUserId}.
 *
 * The [authUserId] is the permanent Firebase Authentication UID.
 * Trusted Contacts authenticate via phone OTP only.
 *
 * [connectedUserIds] tracks which User auth UIDs have relationships with this TC.
 * Used to iterate and fetch individual relationship documents (since Firestore rules
 * deny collection-wide list queries on relationships).
 */
data class TrustedContactProfile(
    val authUserId: String = "",
    val name: String = "",
    val phone: String = "",
    val accountType: String = AccountType.TRUSTED_CONTACT.name,
    val createdAt: Long = System.currentTimeMillis(),
    val phoneVerified: Boolean = false,
    val connectedUserIds: List<String> = emptyList()
) {
    /** No-arg constructor required by Firestore deserialization. */
    constructor() : this(authUserId = "")

    fun toMap(): Map<String, Any?> = mapOf(
        "authUserId" to authUserId,
        "name" to name,
        "phone" to phone,
        "accountType" to accountType,
        "createdAt" to createdAt,
        "phoneVerified" to phoneVerified,
        "connectedUserIds" to connectedUserIds
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): TrustedContactProfile {
            return TrustedContactProfile(
                authUserId = map["authUserId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                accountType = map["accountType"] as? String ?: AccountType.TRUSTED_CONTACT.name,
                createdAt = map["createdAt"] as? Long ?: System.currentTimeMillis(),
                phoneVerified = map["phoneVerified"] as? Boolean ?: false,
                connectedUserIds = map["connectedUserIds"] as? List<String> ?: emptyList()
            )
        }
    }
}
