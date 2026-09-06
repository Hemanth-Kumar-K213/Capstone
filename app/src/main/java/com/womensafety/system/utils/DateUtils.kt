package com.womensafety.system.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Date formatting and calculation helpers.
 */
object DateUtils {

    private val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val displayTimeFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    fun formatDate(timestamp: Long): String {
        return displayFormat.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        return displayTimeFormat.format(Date(timestamp))
    }

    /**
     * Returns remaining time until expiration in a human-readable format.
     */
    fun getTimeUntilExpiry(expiresAt: Long): String {
        val remaining = expiresAt - System.currentTimeMillis()
        if (remaining <= 0) return "Expired"

        val days = TimeUnit.MILLISECONDS.toDays(remaining)
        val hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24

        return when {
            days > 0 -> "${days}d ${hours}h remaining"
            hours > 0 -> "${hours}h remaining"
            else -> "Less than 1h remaining"
        }
    }

    fun isExpired(expiresAt: Long): Boolean {
        return System.currentTimeMillis() > expiresAt
    }
}
