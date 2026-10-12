package com.womensafety.system.data.model

/**
 * Represents a User ↔ Trusted Contact relationship stored in
 * Firestore under relationships/{relationshipDocId}.
 *
 * CRITICAL: Both [userId] and [trustedContactId] are STABLE Firebase Auth UIDs.
 * The weekly rotating UID is NEVER stored here. This ensures that
 * UID rotation does NOT break existing relationships.
 */
data class Relationship(
    val id: String = "",
    val userId: String = "",
    val trustedContactId: String = "",
    val userName: String = "",
    val trustedContactName: String = "",
    val trustedContactPhone: String = "",
    val userPhone: String = "",
    val status: String = PermissionState.PENDING.name,
    val requestedAt: Long = System.currentTimeMillis(),
    val respondedAt: Long? = null,
    val revokedAt: Long? = null
) {
    /** No-arg constructor required by Firestore deserialization. */
    constructor() : this(id = "")

    val permissionState: PermissionState
        get() = PermissionState.fromString(status) ?: PermissionState.PENDING

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "trustedContactId" to trustedContactId,
        "userName" to userName,
        "trustedContactName" to trustedContactName,
        "trustedContactPhone" to trustedContactPhone,
        "userPhone" to userPhone,
        "status" to status,
        "requestedAt" to requestedAt,
        "respondedAt" to respondedAt,
        "revokedAt" to revokedAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Relationship {
            return Relationship(
                id = id,
                userId = map["userId"] as? String ?: "",
                trustedContactId = map["trustedContactId"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                trustedContactName = map["trustedContactName"] as? String ?: "",
                trustedContactPhone = map["trustedContactPhone"] as? String ?: "",
                userPhone = map["userPhone"] as? String ?: "",
                status = map["status"] as? String ?: PermissionState.PENDING.name,
                requestedAt = (map["requestedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                respondedAt = (map["respondedAt"] as? Number)?.toLong(),
                revokedAt = (map["revokedAt"] as? Number)?.toLong()
            )
        }
    }
}
