package com.womensafety.system.data.model

/**
 * Weekly rotating UID stored in Firestore under weeklyUids/{uidDocId}.
 *
 * IMPORTANT: This document is WRITTEN ONLY by Firebase Cloud Functions in production.
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
    val active: Boolean = false,
    val retiredAt: Long = 0L,
    val replacedBy: String = ""
) {
    /** No-arg constructor required by Firestore deserialization. */
    constructor() : this(userId = "")

    val isExpired: Boolean
        get() = expiresAt > 0L && System.currentTimeMillis() > expiresAt

    /**
     * An UID is currently active if:
     * 1. It is within its 7-day validity period (!isExpired).
     * 2. AND either marked active, OR within the 2-hour zero-downtime transition grace period.
     */
    val isCurrentlyActive: Boolean
        get() {
            if (isExpired) return false
            if (active) return true
            if (retiredAt > 0L) {
                val elapsedSinceRetired = System.currentTimeMillis() - retiredAt
                return elapsedSinceRetired in 0..TRANSITION_GRACE_PERIOD_MS
            }
            return false
        }

    companion object {
        const val TRANSITION_GRACE_PERIOD_MS = 2 * 60 * 60 * 1000L // 2-hour transition grace period

        fun fromMap(map: Map<String, Any?>): WeeklyUid {
            return WeeklyUid(
                userId = map["userId"] as? String ?: "",
                uid = map["uid"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                generatedAt = (map["generatedAt"] as? Number)?.toLong() ?: 0L,
                expiresAt = (map["expiresAt"] as? Number)?.toLong() ?: 0L,
                active = map["active"] as? Boolean ?: false,
                retiredAt = (map["retiredAt"] as? Number)?.toLong() ?: 0L,
                replacedBy = map["replacedBy"] as? String ?: ""
            )
        }
    }
}
