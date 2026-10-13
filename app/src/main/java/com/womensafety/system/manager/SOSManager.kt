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

    private const val PREFS_NAME = "sos_prefs"
    private const val KEY_CACHED_PRIMARY_PHONE = "cached_primary_phone"
    private const val KEY_CACHED_USER_NAME = "cached_user_name"

    private val authManager by lazy { FirebaseAuthManager() }
    private val userRepository by lazy { UserRepository() }
    private val locationRepository by lazy { LocationRepository() }
    private val sosRepository by lazy { SOSRepository() }
    private val relationshipRepository by lazy { com.womensafety.system.data.repository.RelationshipRepository() }

    private val _activeEvent = MutableStateFlow<SOSEvent?>(null)
    val activeEvent: StateFlow<SOSEvent?> = _activeEvent.asStateFlow()

    var isTestMode: Boolean = false

    private var appContext: Context? = null
    private var cachedPrimaryPhone: String? = null
    private var cachedUserName: String? = null
    private var cachedEmergencyContacts: List<com.womensafety.system.data.model.EmergencyContact> = emptyList()

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cachedPrimaryPhone = prefs.getString(KEY_CACHED_PRIMARY_PHONE, null)
            cachedUserName = prefs.getString(KEY_CACHED_USER_NAME, "User") ?: "User"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached SOS preferences: ${e.message}")
        }
    }

    fun saveCachedPrimaryPhone(context: Context, phone: String) {
        if (phone.isNotBlank()) {
            cachedPrimaryPhone = phone
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_CACHED_PRIMARY_PHONE, phone).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save cached primary phone: ${e.message}")
            }
        }
    }

    fun saveCachedUserName(context: Context, name: String) {
        if (name.isNotBlank()) {
            cachedUserName = name
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_CACHED_USER_NAME, name).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save cached user name: ${e.message}")
            }
        }
    }

    fun updateCachedEmergencyContacts(contacts: List<com.womensafety.system.data.model.EmergencyContact>) {
        cachedEmergencyContacts = contacts
        val primary = contacts.find { it.isPrimary && it.phone.isNotBlank() }?.phone
            ?: contacts.firstOrNull { it.phone.isNotBlank() }?.phone
        if (!primary.isNullOrBlank()) {
            cachedPrimaryPhone = primary
            appContext?.let { saveCachedPrimaryPhone(it, primary) }
        }
    }

    fun updateCachedProfile(profile: com.womensafety.system.data.model.UserProfile) {
        cachedUserName = profile.name.ifBlank { "User" }
        appContext?.let { saveCachedUserName(it, cachedUserName ?: "User") }
        if (profile.emergencyContacts.isNotEmpty()) {
            updateCachedEmergencyContacts(profile.emergencyContacts)
        }
    }

    fun getFastPrimaryPhone(context: Context? = null): String {
        cachedPrimaryPhone?.takeIf { it.isNotBlank() }?.let { return it }

        val profileContacts = SafetyMonitoringManager.cachedProfile?.emergencyContacts?.filter { it.phone.isNotBlank() }
        val primaryFromProfile = profileContacts?.find { it.isPrimary }?.phone
            ?: profileContacts?.firstOrNull()?.phone
        if (!primaryFromProfile.isNullOrBlank()) {
            cachedPrimaryPhone = primaryFromProfile
            val ctx = context ?: appContext
            ctx?.let { saveCachedPrimaryPhone(it, primaryFromProfile) }
            return primaryFromProfile
        }

        val ctx = context ?: appContext
        if (ctx != null) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val fromPrefs = prefs.getString(KEY_CACHED_PRIMARY_PHONE, null)
                if (!fromPrefs.isNullOrBlank()) {
                    cachedPrimaryPhone = fromPrefs
                    return fromPrefs
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read cached primary phone: ${e.message}")
            }
        }

        return "112"
    }

    fun getFastUserName(context: Context? = null): String {
        cachedUserName?.takeIf { it.isNotBlank() && it != "User" }?.let { return it }

        val profileName = SafetyMonitoringManager.cachedProfile?.name
        if (!profileName.isNullOrBlank()) {
            cachedUserName = profileName
            val ctx = context ?: appContext
            ctx?.let { saveCachedUserName(it, profileName) }
            return profileName
        }

        val ctx = context ?: appContext
        if (ctx != null) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val fromPrefs = prefs.getString(KEY_CACHED_USER_NAME, "User")
                if (!fromPrefs.isNullOrBlank()) {
                    cachedUserName = fromPrefs
                    return fromPrefs
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read cached user name: ${e.message}")
            }
        }

        return "User"
    }

    fun publishInitialActiveEvent(
        context: Context,
        eventId: String = UUID.randomUUID().toString(),
        triggerMethod: SOSTriggerMethod = SOSTriggerMethod.BUTTON
    ): SOSEvent {
        val currentUserId = authManager.currentUserId ?: "unknown"
        val userName = getFastUserName(context)
        val loc = LocationTrackingService.currentLocation.value

        val initialEvent = SOSEvent(
            id = eventId,
            userId = currentUserId,
            userName = userName,
            triggeredAt = System.currentTimeMillis(),
            latitude = loc?.latitude ?: 0.0,
            longitude = loc?.longitude ?: 0.0,
            accuracy = loc?.accuracy,
            address = loc?.address,
            status = SOSStatus.ACTIVE,
            triggerMethod = triggerMethod,
            callMade = true,
            smsSent = 0
        )
        _activeEvent.value = initialEvent
        return initialEvent
    }

    /**
     * Executes the complete SOS emergency alert sequence.
     */
    suspend fun triggerSOS(
        context: Context,
        triggerMethod: SOSTriggerMethod,
        customEventId: String? = null
    ): SOSEvent? {
        val currentUserId = authManager.currentUserId ?: return null
        val eventId = customEventId ?: UUID.randomUUID().toString()

        val cachedProfile = SafetyMonitoringManager.cachedProfile
        val userName = cachedProfile?.name?.ifBlank { "User" } ?: getFastUserName(context)
        val primaryPhone = getFastPrimaryPhone(context)

        if (isTestMode) {
            Log.w(TAG, "TEST MODE ACTIVE: SOS emergency triggered ($triggerMethod). Real SMS, siren, and external alerts strictly SUPPRESSED.")
            val testEvent = SOSEvent(
                id = eventId,
                userId = currentUserId,
                userName = userName,
                triggeredAt = System.currentTimeMillis(),
                latitude = 0.0,
                longitude = 0.0,
                status = SOSStatus.ACTIVE,
                triggerMethod = triggerMethod
            )
            _activeEvent.value = testEvent
            return testEvent
        }

        Log.w(TAG, "Triggering SOS alert for user $userName via $triggerMethod")

        // Fast publication of initial active event if not already present
        if (_activeEvent.value == null || _activeEvent.value?.id != eventId) {
            val loc = LocationTrackingService.currentLocation.value
            _activeEvent.value = SOSEvent(
                id = eventId,
                userId = currentUserId,
                userName = userName,
                triggeredAt = System.currentTimeMillis(),
                latitude = loc?.latitude ?: 0.0,
                longitude = loc?.longitude ?: 0.0,
                accuracy = loc?.accuracy,
                address = loc?.address,
                status = SOSStatus.ACTIVE,
                triggerMethod = triggerMethod,
                callMade = true
            )
        }

        // 1 & 2: SIMULTANEOUSLY ACTIVATE SIREN ALARM AUDIO & INITIATE EMERGENCY CALL
        Log.i(TAG, "SIMULTANEOUSLY initiating siren alarm and emergency phone call to $primaryPhone")
        SoundManager.playSiren(context)
        val callMade = initiateEmergencyCall(context, primaryPhone)

        // 3. Switch Location Tracking to High-Frequency SOS Mode (5-second updates)
        LocationTrackingService.enableSOSMode(context)

        // 4. Acquire Instant GPS Fix (or fallback to latest known location)
        val locationFix = getImmediateLocationFix(context, currentUserId)
        val lat = locationFix?.latitude
            ?: LocationTrackingService.currentLocation.value?.latitude
            ?: 0.0
        val lng = locationFix?.longitude
            ?: LocationTrackingService.currentLocation.value?.longitude
            ?: 0.0
        val accuracy = locationFix?.accuracy ?: LocationTrackingService.currentLocation.value?.accuracy
        val address = locationFix?.address ?: LocationTrackingService.currentLocation.value?.address

        // 5. Create SOSEvent Document in Firestore
        // (Automatically triggers onSOSEventCreated Firebase Cloud Function to dispatch Twilio SMS alerts)
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
            triggerMethod = triggerMethod,
            callMade = callMade
        )

        sosRepository.createSOSEvent(sosEvent)
        _activeEvent.value = sosEvent

        // 6. Collect all available emergency contacts from profile, repo, and paired guardians
        val userProfile = cachedProfile ?: userRepository.getUserProfile(currentUserId).getOrNull()
        var emergencyContacts = userProfile?.emergencyContacts?.filter { it.phone.isNotBlank() } ?: emptyList()
        if (emergencyContacts.isEmpty()) {
            val repoContacts = userRepository.getEmergencyContacts().getOrNull() ?: emptyList()
            emergencyContacts = repoContacts.filter { it.phone.isNotBlank() }
        }
        if (emergencyContacts.isEmpty()) {
            val pairedGuardians = relationshipRepository.getRelationshipsForUser(currentUserId).getOrNull() ?: emptyList()
            emergencyContacts = pairedGuardians.filter { it.trustedContactPhone.isNotBlank() }.map { rel ->
                com.womensafety.system.data.model.EmergencyContact(
                    name = rel.trustedContactName.ifBlank { "Guardian" },
                    phone = rel.trustedContactPhone,
                    isPrimary = true
                )
            }
        }

        if (emergencyContacts.isNotEmpty()) {
            updateCachedEmergencyContacts(emergencyContacts)
        }

        // 7. Dispatch Direct Cellular SIM SMS + 2Factor Cloud SMS to contacts
        val smsCount = dispatchEmergencyAlerts(
            context = context,
            contacts = emergencyContacts,
            userName = userName,
            latitude = lat,
            longitude = lng,
            timestamp = sosEvent.triggeredAt
        )

        val updatedEvent = sosEvent.copy(callMade = callMade, smsSent = smsCount)
        _activeEvent.value = updatedEvent
        sosRepository.updateDeliveryStats(
            eventId = eventId,
            notificationsSent = 0,
            smsSent = smsCount,
            callMade = callMade,
            fcmTokens = emptyList()
        )
        return updatedEvent
    }

    /**
     * Resolves an ongoing SOS incident (Mark as Safe or False Alarm).
     */
    suspend fun resolveSOS(context: Context, eventId: String, status: SOSStatus) {
        Log.i(TAG, "Resolving SOS event $eventId with status: $status")

        // 1. Clear active event in memory immediately so UI/NavGraph unblocks
        _activeEvent.value = null

        // 2. Stop Siren and Play Resolution Chime
        SoundManager.playResolved(context)

        // 3. Restore Location Tracking to Normal Frequency
        LocationTrackingService.disableSOSMode(context)

        // 4. Send Resolution SMS ("I'm Safe" / "False Alarm") to Emergency Contacts
        val currentUserId = authManager.currentUserId
        if (currentUserId != null) {
            val userProfile = userRepository.getUserProfile(currentUserId).getOrNull()
            val userName = userProfile?.name?.ifBlank { "User" } ?: "User"
            var allContacts = userProfile?.emergencyContacts?.filter { it.phone.isNotBlank() } ?: emptyList()
            if (allContacts.isEmpty()) {
                allContacts = userRepository.getEmergencyContacts().getOrNull()?.filter { it.phone.isNotBlank() } ?: emptyList()
            }
            sendResolutionSMS(context, userName, allContacts, status)
        }

        // 5. Update Firestore Document with timeout safeguard
        val resolvedAt = System.currentTimeMillis()
        try {
            withTimeoutOrNull(3000L) {
                sosRepository.updateSOSEventStatus(eventId, status, resolvedAt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "updateSOSEventStatus caught error: ${e.message}")
        }
    }

    private suspend fun getImmediateLocationFix(context: Context, userId: String): UserLocation? {
        return TrinetraLocationManager.getImmediateLocationFix(context, userId)
    }

    fun initiateEmergencyCall(context: Context, phoneNumber: String): Boolean {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            // ACTION_CALL directly dials without user pressing Call button
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
            Log.e(TAG, "Error initiating emergency phone call with ACTION_CALL, falling back to ACTION_DIAL", e)
            try {
                val fallbackIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed emergency phone call dispatch", e2)
                false
            }
        }
    }

    private fun sendResolutionSMS(
        context: Context,
        userName: String,
        contacts: List<com.womensafety.system.data.model.EmergencyContact>,
        status: SOSStatus
    ) {
        if (contacts.isEmpty()) return

        val resolutionText = if (status == SOSStatus.FALSE_ALARM) {
            "ℹ️ FALSE ALARM: $userName has cancelled the SOS alert. All is well."
        } else {
            "✅ ALL CLEAR: $userName has marked the emergency as resolved and is safe now."
        }

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        contacts.filter { it.phone.isNotBlank() }.forEach { contact ->
            val phone = contact.phone.trim()
            if (hasSmsPermission) {
                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(android.telephony.SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        android.telephony.SmsManager.getDefault()
                    }
                    smsManager.sendTextMessage(phone, null, resolutionText, null, null)
                    Log.i(TAG, "Resolution SMS sent via cellular SIM to $phone")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed sending resolution SMS to $phone", e)
                }
            }
        }
    }

    fun dispatchEmergencyAlerts(
        context: Context,
        contacts: List<com.womensafety.system.data.model.EmergencyContact>,
        userName: String,
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ): Int {
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

        val validContacts = contacts.filter { it.phone.isNotBlank() }
        Log.i(TAG, "Dispatching SOS alerts to ${validContacts.size} contacts. SEND_SMS granted: $hasSmsPermission")

        var simSentCount = 0

        validContacts.forEach { contact ->
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
                    simSentCount++
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

        return if (hasSmsPermission && simSentCount > 0) simSentCount else validContacts.size
    }

    suspend fun redispatchSMS(context: Context, event: SOSEvent): Int {
        val currentUserId = authManager.currentUserId ?: return 0
        val userProfile = userRepository.getUserProfile(currentUserId).getOrNull()
        val userName = userProfile?.name?.ifBlank { "User" } ?: event.userName.ifBlank { "User" }
        val contacts = userProfile?.emergencyContacts ?: emptyList()
        val count = dispatchEmergencyAlerts(
            context = context,
            contacts = contacts,
            userName = userName,
            latitude = event.latitude ?: 0.0,
            longitude = event.longitude ?: 0.0,
            timestamp = event.triggeredAt
        )
        val updated = event.copy(smsSent = count)
        _activeEvent.value = updated
        sosRepository.updateDeliveryStats(
            eventId = event.id,
            notificationsSent = event.notificationsSent,
            smsSent = count,
            callMade = event.callMade,
            fcmTokens = event.fcmTokensSent
        )
        return count
    }
}
