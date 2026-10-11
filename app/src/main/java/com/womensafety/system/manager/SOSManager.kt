package com.womensafety.system.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import com.womensafety.system.data.sms.TwoFactorClient
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
            ?: LocationTrackingService.currentLocation.value?.latitude
            ?: 0.0
        val lng = locationFix?.longitude
            ?: LocationTrackingService.currentLocation.value?.longitude
            ?: 0.0
        val accuracy = locationFix?.accuracy ?: LocationTrackingService.currentLocation.value?.accuracy
        val address = locationFix?.address ?: LocationTrackingService.currentLocation.value?.address

        // 4. Create SOSEvent Document in Firestore
        // (Automatically triggers onSOSEventCreated Firebase Cloud Function to dispatch Twilio SMS alerts)
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

        val emergencyContacts = userProfile?.emergencyContacts ?: emptyList()

        // 5. Dispatch Direct Cellular SIM SMS + 2Factor Cloud SMS to contacts
        dispatchEmergencyAlerts(
            context = context,
            contacts = emergencyContacts,
            userName = userName,
            latitude = lat,
            longitude = lng,
            timestamp = sosEvent.triggeredAt
        )

        // 6. Initiate Auto-Call to Primary Contact (if enabled)
        var callMade = false
        if (userProfile?.sosAutoCall == true && emergencyContacts.isNotEmpty()) {
            val primaryContact = emergencyContacts.find { it.isPrimary } ?: emergencyContacts.first()
            callMade = initiateEmergencyCall(context, primaryContact.phone)
        }

        val updatedEvent = sosEvent.copy(callMade = callMade)
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

        _activeEvent.value = null
    }

    private suspend fun getImmediateLocationFix(context: Context, userId: String): UserLocation? {
        return TrinetraLocationManager.getImmediateLocationFix(context, userId)
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

    private fun dispatchEmergencyAlerts(
        context: Context,
        contacts: List<com.womensafety.system.data.model.EmergencyContact>,
        userName: String,
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ) {
        val timeStr = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.US).format(java.util.Date(timestamp))
        val mapLink = if (latitude != 0.0 && longitude != 0.0) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            "https://maps.google.com/?q=12.9716,77.5946"
        }

        val messageText = "🚨 EMERGENCY ALERT 🚨\n" +
                "$userName triggered SOS and needs urgent help!\n\n" +
                "Location:\n" +
                "$mapLink\n" +
                "Time: $timeStr\n\n" +
                "Sent automatically from Trinetra."

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        Log.i(TAG, "Dispatching SOS alerts to ${contacts.size} contacts. SEND_SMS granted: $hasSmsPermission")

        contacts.filter { it.phone.isNotBlank() }.forEach { contact ->
            val phone = contact.phone.trim()

            // 1. Direct device cellular SIM dispatch via SmsManager (highest priority, direct carrier)
            if (hasSmsPermission) {
                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(android.telephony.SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        android.telephony.SmsManager.getDefault()
                    }

                    val parts = smsManager.divideMessage(messageText)
                    if (parts.size > 1) {
                        smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                    } else {
                        smsManager.sendTextMessage(phone, null, messageText, null, null)
                    }
                    Log.i(TAG, "Direct cellular SIM SMS alert successfully dispatched to $phone")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed sending direct cellular SIM SMS to $phone", e)
                }
            } else {
                Log.w(TAG, "SEND_SMS permission not granted; relying on 2Factor Cloud SMS Gateway")
            }

            // 2. Parallel 2Factor Cloud SMS Gateway
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val res = TwoFactorClient.sendSOSAlert(
                        phoneNumber = phone,
                        userName = userName,
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = timestamp
                    )
                    if (res.isSuccess) {
                        Log.i(TAG, "2Factor SMS successfully dispatched to $phone")
                    } else {
                        Log.w(TAG, "2Factor SMS notice for $phone: ${res.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "2Factor SMS dispatch exception for $phone", e)
                }
            }
        }
    }
}
