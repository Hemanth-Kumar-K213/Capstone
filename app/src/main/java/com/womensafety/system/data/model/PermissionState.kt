package com.womensafety.system.data.model

/**
 * Permission states for User ↔ Trusted Contact relationships.
 * Each relationship has its own independent state.
 */
enum class PermissionState {
    PENDING,
    APPROVED,
    DENIED,
    REVOKED,
    EXPIRED;

    companion object {
        fun fromString(value: String): PermissionState? {
            return entries.find { it.name == value.uppercase() }
        }
    }
}
