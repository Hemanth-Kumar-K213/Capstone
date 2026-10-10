package com.womensafety.system.utils

/**
 * Client-side validation utilities.
 * These are UI-level format checks only. Backend authorization is independent.
 */
object Validators {

    /**
     * Validates email format. Must be a valid email.
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() &&
                android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Validates phone number. Expects E.164 format or common formats.
     * Must start with + and country code for Firebase Phone Auth.
     */
    fun isValidPhone(phone: String): Boolean {
        if (phone.isBlank()) return false
        val cleaned = phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        return cleaned.matches(Regex("^\\+[1-9]\\d{6,14}$")) || cleaned.matches(Regex("^\\d{10}$"))
    }

    /**
     * Normalizes a phone number to E.164 format.
     */
    fun normalizePhone(phone: String): String {
        val cleaned = phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        return if (!cleaned.startsWith("+") && cleaned.length == 10) {
            "+91$cleaned"
        } else {
            cleaned
        }
    }

    /**
     * Validates name — must not be blank and at least 2 characters.
     */
    fun isValidName(name: String): Boolean {
        return name.trim().length >= 2
    }

    /**
     * Strong password validation:
     * - At least 8 characters
     * - At least 1 uppercase letter
     * - At least 1 lowercase letter
     * - At least 1 digit
     * - At least 1 special character
     */
    fun isValidPassword(password: String): Boolean {
        if (password.length < 8) return false
        val hasUppercase = password.any { it.isUpperCase() }
        val hasLowercase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return hasUppercase && hasLowercase && hasDigit && hasSpecial
    }

    /**
     * Validates passwords match.
     */
    fun doPasswordsMatch(password: String, confirmPassword: String): Boolean {
        return password == confirmPassword && password.isNotBlank()
    }

    /**
     * Validates UID format: exactly 12 digits, YYYYMMDDXXXX structure.
     * Client-side format check only — existence/activity is verified via Firestore.
     */
    fun isValidUidFormat(uid: String): Boolean {
        if (uid.length != 12) return false
        if (!uid.all { it.isDigit() }) return false

        // Validate date portion (first 8 digits)
        return try {
            val year = uid.substring(0, 4).toInt()
            val month = uid.substring(4, 6).toInt()
            val day = uid.substring(6, 8).toInt()

            year in 2024..2100 &&
                    month in 1..12 &&
                    day in 1..31
        } catch (e: NumberFormatException) {
            false
        }
    }

    /**
     * Validates OTP — exactly 6 digits.
     */
    fun isValidOtp(otp: String): Boolean {
        return otp.length == 6 && otp.all { it.isDigit() }
    }
}
