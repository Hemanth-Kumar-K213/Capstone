package com.womensafety.system.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.utils.SOSNotificationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging Service handling incoming SOS alerts and push notifications.
 */
class WomenSafetyMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "WomenSafetyFCM"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firestoreManager = FirestoreManager()
    private val authManager = FirebaseAuthManager()

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM Message received from: ${message.from}")

        val data = message.data
        val type = data["type"] ?: return

        when (type) {
            "SOS_ALERT" -> {
                val eventId = data["eventId"] ?: ""
                val userId = data["userId"] ?: ""
                val userName = data["userName"] ?: "Connected User"
                val lat = data["latitude"]
                val lng = data["longitude"]

                SOSNotificationHandler.showSOSAlertNotification(
                    context = applicationContext,
                    eventId = eventId,
                    userId = userId,
                    userName = userName,
                    lat = lat,
                    lng = lng
                )
            }
            "SOS_RESOLVED" -> {
                val userName = data["userName"] ?: "Connected User"
                SOSNotificationHandler.showResolutionNotification(
                    context = applicationContext,
                    userName = userName
                )
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token generated: $token")

        val currentUserId = authManager.currentUserId ?: return
        serviceScope.launch {
            try {
                firestoreManager.saveFCMToken(currentUserId, token, "USER")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving new FCM token", e)
            }
        }
    }
}
