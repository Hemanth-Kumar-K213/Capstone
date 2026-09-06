package com.womensafety.system.utils

/**
 * Application-wide constants.
 */
object Constants {

    /** Minimum number of Trusted Contacts a User must have. */
    const val MIN_TRUSTED_CONTACTS = 2

    /** UID is exactly 12 digits: YYYYMMDDXXXX */
    const val UID_LENGTH = 12

    /** OTP is exactly 6 digits */
    const val OTP_LENGTH = 6

    /** OTP resend cooldown in seconds */
    const val OTP_RESEND_COOLDOWN_SECONDS = 60

    /** UID validity duration in days */
    const val UID_VALIDITY_DAYS = 7

    /** Password minimum length */
    const val PASSWORD_MIN_LENGTH = 8
}
