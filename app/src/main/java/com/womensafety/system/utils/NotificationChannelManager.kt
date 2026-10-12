package com.womensafety.system.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * Creates and configures notification channels for foreground services and emergency SOS alerts.
 */
object NotificationChannelManager {

    const val CHANNEL_LOCATION_TRACKING = "location_tracking"
    const val CHANNEL_SHAKE_DETECTION = "shake_detection"
    const val CHANNEL_SOS_ALERTS = "sos_alerts"
    const val CHANNEL_SAFETY_CONFIRMATION = "safety_confirmation"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

            // 1. Location Tracking Channel (Low importance so it doesn't chime repeatedly)
            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION_TRACKING,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays ongoing location tracking status in the notification shade"
                setShowBadge(false)
            }

            // 2. Shake Detection Channel
            val shakeChannel = NotificationChannel(
                CHANNEL_SHAKE_DETECTION,
                "Shake Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays ongoing shake detection monitoring status"
                setShowBadge(false)
            }

            // 3. Emergency SOS Alerts Channel (Highest Priority)
            val defaultAlarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val sosChannel = NotificationChannel(
                CHANNEL_SOS_ALERTS,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical emergency notifications and SOS alarms"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(defaultAlarmSound, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // 4. Safety Confirmation Channel ("Are You Safe?" Prompts)
            val safetyChannel = NotificationChannel(
                CHANNEL_SAFETY_CONFIRMATION,
                "Safety Check Prompts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent safety confirmation prompts requiring user acknowledgement"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannels(
                listOf(locationChannel, shakeChannel, sosChannel, safetyChannel)
            )
        }
    }
}
