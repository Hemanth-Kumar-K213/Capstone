package com.womensafety.system.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.womensafety.system.MainActivity
import com.womensafety.system.R
import com.womensafety.system.receiver.SafetyNotificationActionReceiver

/**
 * Manages heads-up Android system notifications for proactive safety confirmations.
 */
object SafetyNotificationManager {

    const val NOTIFICATION_ID_SAFETY_CHECK = 7701
    const val ACTION_CONFIRM_SAFE = "com.womensafety.system.ACTION_CONFIRM_SAFE"
    const val ACTION_CONFIRM_HELP = "com.womensafety.system.ACTION_CONFIRM_HELP"
    const val EXTRA_EVENT_ID = "extra_event_id"

    fun showSafetyCheckNotification(
        context: Context,
        eventId: String,
        riskScore: Int,
        countdownSeconds: Int = 60
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannelManager.createNotificationChannels(context)

        // 1. Content Intent (Tapping notification body opens app)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_safety_check", true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. "I'M SAFE" Action
        val safeIntent = Intent(context, SafetyNotificationActionReceiver::class.java).apply {
            action = ACTION_CONFIRM_SAFE
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val safePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            safeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. "I NEED HELP" Action
        val helpIntent = Intent(context, SafetyNotificationActionReceiver::class.java).apply {
            action = ACTION_CONFIRM_HELP
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val helpPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            helpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_SAFETY_CONFIRMATION)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ ARE YOU SAFE? — Trinetra Check")
            .setContentText("Elevated risk detected (Score $riskScore). Auto-escalating in $countdownSeconds seconds.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Elevated safety risk detected (Risk Score: $riskScore).\nPlease confirm your status within $countdownSeconds seconds. If no response is received, emergency contacts will be alerted.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(0, "✅ I'M SAFE", safePendingIntent)
            .addAction(0, "🚨 NEED HELP", helpPendingIntent)

        notificationManager.notify(NOTIFICATION_ID_SAFETY_CHECK, builder.build())
    }

    fun cancelSafetyCheckNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_SAFETY_CHECK)
    }
}
