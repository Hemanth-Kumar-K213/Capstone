package com.womensafety.system.data.model

/**
 * Weekly rotating UID stored in Firestore under weeklyUids/{uidDocId}.
 *
 * IMPORTANT: This document is WRITTEN ONLY by Firebase Cloud Functions.
 * The Android client has READ-ONLY access.
 *
 * The [userId] is the permanent Firebase Auth UID (internal identity).
 * The [uid] is the 12-digit rotating external identifier (YYYYMMDDXXXX).
 *
 * UID rotation does NOT affect existing Trusted Contact relationships,
 * which are keyed by the stable [userId].
 */
data class WeeklyUid(
    val userId: String = "",
    val uid: String = "",
    val userName: String = "",
    val generatedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val active: Boolean = false
) {
    /** No-arg constructor required by Firestore deserialization. */
    constructor() : this(userId = "")

    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt

    val isCurrentlyActive: Boolean
        get() = active && !isExpired

    companion object {
        fun fromMap(map: Map<String, Any?>): WeeklyUid {
            return WeeklyUid(
                userId = map["userId"] as? String ?: "",
                uid = map["uid"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                generatedAt = map["generatedAt"] as? Long ?: 0L,
                expiresAt = map["expiresAt"] as? Long ?: 0L,
                active = map["active"] as? Boolean ?: false
            )
        }
    }
}
