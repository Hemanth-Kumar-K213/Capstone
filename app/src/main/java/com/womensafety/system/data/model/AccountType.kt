package com.womensafety.system.data.model

/**
 * Defines the two account types in the Women Safety System.
 * Each user registers as exactly one of these.
 */
enum class AccountType {
    USER,
    TRUSTED_CONTACT;

    companion object {
        fun fromString(value: String): AccountType? {
            return entries.find { it.name == value.uppercase() }
        }
    }
}
