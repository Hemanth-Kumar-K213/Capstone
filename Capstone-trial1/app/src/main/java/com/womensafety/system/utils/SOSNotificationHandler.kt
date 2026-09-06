package com.womensafety.system.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.womensafety.system.MainActivity
import com.womensafety.system.R

/**
 * Helper to display urgent heads-up notifications when a Trusted Contact receives an SOS alert.
 */
object SOSNotificationHandler {

    private const val NOTIFICATION_ID_BASE = 5000

    fun showSOSAlertNotification(
        context: Context,
        eventId: String,
        userId: String,
        userName: String,
        lat: String?,
        lng: String?
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannelManager.createNotificationChannels(context)

        // Open app intent to SOS Received Screen
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route_event_id", eventId)
            putExtra("route_user_id", userId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Map external intent action
        val mapActionPendingIntent = if (!lat.isNullOrBlank() && !lng.isNullOrBlank()) {
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($userName+SOS)")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            PendingIntent.getActivity(
                context,
                (eventId + "_map").hashCode(),
                mapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val builder = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_SOS_ALERTS)
            .setContentTitle("🚨 EMERGENCY: $userName needs help!")
            .setContentText("Emergency SOS alert triggered. Tap immediately to view location.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(openPendingIntent, true)
            .setContentIntent(openPendingIntent)

        if (mapActionPendingIntent != null) {
            builder.addAction(android.R.drawable.ic_dialog_map, "View Map", mapActionPendingIntent)
        }

        notificationManager.notify(NOTIFICATION_ID_BASE + (eventId.hashCode() % 1000), builder.build())
    }

    fun showResolutionNotification(context: Context, userName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_SOS_ALERTS)
            .setContentTitle("✅ ALL CLEAR: $userName is Safe")
            .setContentText("$userName has resolved their emergency alert.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID_BASE + 999, builder.build())
    }
}
