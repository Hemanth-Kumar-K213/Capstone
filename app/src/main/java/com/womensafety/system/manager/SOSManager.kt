package com.womensafety.system.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.data.model.SOSTriggerMethod
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.repository.LocationRepository
import com.womensafety.system.data.repository.SOSRepository
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.utils.SoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Core orchestration engine for Emergency SOS alerts.
 * Coordinates GPS fixes, siren audio, cellular SMS dispatch, auto-calling, and Firestore sync.
 */
object SOSManager {

    private const val TAG = "SOSManager"
    private const val GPS_TIMEOUT_MS = 8000L

    private val authManager = FirebaseAuthManager()
    private val userRepository = UserRepository()
    private val locationRepository = LocationRepository()
    private val sosRepository = SOSRepository()

    private val _activeEvent = MutableStateFlow<SOSEvent?>(null)
    val activeEvent: StateFlow<SOSEvent?> = _activeEvent.asStateFlow()

    /**
     * Executes the complete SOS emergency alert sequence.
     */
    suspend fun triggerSOS(context: Context, triggerMethod: SOSTriggerMethod): SOSEvent? {
        val currentUserId = authManager.currentUserId ?: return null
        val userProfile = userRepository.getUserProfile(currentUserId).getOrNull()
        val userName = userProfile?.name?.ifBlank { "User" } ?: "User"

        Log.w(TAG, "Triggering SOS alert for user $userName via $triggerMethod")

        // 1. Activate Siren Alarm Audio & Vibration
        SoundManager.playSiren(context)

        // 2. Switch Location Tracking to High-Frequency SOS Mode (5-second updates)
        LocationTrackingService.enableSOSMode(context)

        // 3. Acquire Instant GPS Fix (or fallback to latest known location)
        val locationFix = getImmediateLocationFix(context, currentUserId)
        val lat = locationFix?.latitude
        val lng = locationFix?.longitude
        val accuracy = locationFix?.accuracy
        val address = locationFix?.address

        // 4. Create SOSEvent Document in Firestore
        val eventId = UUID.randomUUID().toString()
        val sosEvent = SOSEvent(
            id = eventId,
            userId = currentUserId,
            userName = userName,
            triggeredAt = System.currentTimeMillis(),
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            address = address,
            status = SOSStatus.ACTIVE,
            triggerMethod = triggerMethod
        )

        sosRepository.createSOSEvent(sosEvent)
        _activeEvent.value = sosEvent

        // 5. Send Emergency SMS to configured emergency contacts
        val emergencyContacts = userProfile?.emergencyContacts ?: emptyList()
        val smsCount = sendEmergencySMS(context, userName, emergencyContacts, lat, lng)

        // 6. Initiate Auto-Call to First Contact (if enabled)
        var callMade = false
        if (userProfile?.sosAutoCall == true && emergencyContacts.isNotEmpty()) {
            callMade = initiateEmergencyCall(context, emergencyContacts.first().phone)
        }

        // 7. Update Firestore SOSEvent with delivery stats
        sosRepository.updateDeliveryStats(
            eventId = eventId,
            notificationsSent = 0, // Cloud Function handles TC FCM counts
            smsSent = smsCount,
            callMade = callMade,
            fcmTokens = emptyList()
        )

        val updatedEvent = sosEvent.copy(smsSent = smsCount, callMade = callMade)
        _activeEvent.value = updatedEvent
        return updatedEvent
    }

    /**
     * Resolves an ongoing SOS incident (Mark as Safe or False Alarm).
     */
    suspend fun resolveSOS(context: Context, eventId: String, status: SOSStatus) {
        Log.i(TAG, "Resolving SOS event $eventId with status: $status")

        // 1. Stop Siren and Play Resolution Chime
        SoundManager.playResolved(context)

        // 2. Restore Location Tracking to Normal Frequency
        LocationTrackingService.disableSOSMode(context)

        // 3. Update Firestore Document
        val resolvedAt = System.currentTimeMillis()
        sosRepository.updateSOSEventStatus(eventId, status, resolvedAt)

        // 4. Send "I'm Safe" SMS to Emergency Contacts
        val currentUserId = authManager.currentUserId
        if (currentUserId != null) {
            val userProfile = userRepository.getUserProfile(currentUserId).getOrNull()
            val userName = userProfile?.name?.ifBlank { "User" } ?: "User"
            val emergencyContacts = userProfile?.emergencyContacts ?: emptyList()

            sendResolutionSMS(context, userName, emergencyContacts, status)
        }

        _activeEvent.value = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun getImmediateLocationFix(context: Context, userId: String): UserLocation? {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            Log.w(TAG, "No FINE_LOCATION permission. Falling back to repository latest.")
            return locationRepository.getLatestLocation(userId).getOrNull()
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val freshLocation = withTimeoutOrNull(GPS_TIMEOUT_MS) {
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }

            if (freshLocation != null) {
                UserLocation(
                    userId = userId,
                    latitude = freshLocation.latitude,
                    longitude = freshLocation.longitude,
                    accuracy = freshLocation.accuracy,
                    timestamp = System.currentTimeMillis(),
                    isSOSLocation = true
                )
            } else {
                locationRepository.getLatestLocation(userId).getOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring immediate GPS fix", e)
            locationRepository.getLatestLocation(userId).getOrNull()
        }
    }

    private fun sendEmergencySMS(
        context: Context,
        userName: String,
        contacts: List<com.womensafety.system.data.model.EmergencyContact>,
        lat: Double?,
        lng: Double?
    ): Int {
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            Log.w(TAG, "SEND_SMS permission not granted. Cannot dispatch cellular SMS.")
            return 0
        }

        if (contacts.isEmpty()) return 0

        val locationLink = if (lat != null && lng != null) {
            "https://maps.google.com/?q=$lat,$lng"
        } else {
            "Location unavailable"
        }

        val timeString = DateUtils.formatDateTime(System.currentTimeMillis())
        val messageText = "🚨 EMERGENCY ALERT 🚨\n" +
                "$userName triggered SOS and needs urgent help!\n\n" +
                "Location: $locationLink\n" +
                "Time: $timeString\n\n" +
                "Sent automatically from Women Safety System."

        var sentCount = 0
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val divided = smsManager.divideMessage(messageText)

            for (contact in contacts) {
                try {
                    smsManager.sendMultipartTextMessage(contact.phone, null, divided, null, null)
                    sentCount++
                    Log.d(TAG, "Emergency SMS dispatched to ${contact.name} (${contact.phone})")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send SMS to ${contact.phone}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SmsManager initialization failed", e)
        }

        return sentCount
    }

    private fun sendResolutionSMS(
        context: Context,
        userName: String,
        contacts: List<com.womensafety.system.data.model.EmergencyContact>,
        status: SOSStatus
    ) {
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission || contacts.isEmpty()) return

        val resolutionText = if (status == SOSStatus.FALSE_ALARM) {
            "ℹ️ FALSE ALARM: $userName has cancelled the SOS alert. All is well."
        } else {
            "✅ ALL CLEAR: $userName has marked the emergency as resolved and is safe now."
        }

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            for (contact in contacts) {
                try {
                    smsManager.sendTextMessage(contact.phone, null, resolutionText, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send resolution SMS to ${contact.phone}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resolution SMS failed", e)
        }
    }

    private fun initiateEmergencyCall(context: Context, phoneNumber: String): Boolean {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating emergency phone call", e)
            false
        }
    }
}
