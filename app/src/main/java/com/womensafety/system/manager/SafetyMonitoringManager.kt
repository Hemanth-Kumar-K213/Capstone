package com.womensafety.system.manager

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.RiskEvent
import com.womensafety.system.data.model.SOSTriggerMethod
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.model.SafetyMode
import com.womensafety.system.data.model.UserProfile
import com.womensafety.system.data.repository.RiskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

import com.womensafety.system.service.LocationTrackingService

/**
 * Main coordinator for proactive safety monitoring, checking risk levels,
 * vibrating on alert thresholds, and escalating to the existing SOSManager.
 */
object SafetyMonitoringManager {

    private const val TAG = "SafetyMonitoringManager"
    private const val DEFAULT_THRESHOLD = 50

    private val authManager by lazy { FirebaseAuthManager() }
    private val riskRepository by lazy { RiskRepository() }

    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    private var countdownJob: Job? = null
    private var appContext: Context? = null

    // State flows for UI mapping
    private val _isProactiveEnabled = MutableStateFlow(true)
    val isProactiveEnabled: StateFlow<Boolean> = _isProactiveEnabled.asStateFlow()

    private val _safetyMode = MutableStateFlow("NORMAL")
    val safetyMode: StateFlow<String> = _safetyMode.asStateFlow()

    private val _isTripActive = MutableStateFlow(false)
    val isTripActive: StateFlow<Boolean> = _isTripActive.asStateFlow()

    private val _tripStartLocation = MutableStateFlow<UserLocation?>(null)
    val tripStartLocation: StateFlow<UserLocation?> = _tripStartLocation.asStateFlow()

    private val _destinationLocation = MutableStateFlow<UserLocation?>(null)
    val destinationLocation: StateFlow<UserLocation?> = _destinationLocation.asStateFlow()

    private val _isSafetyCheckActive = MutableStateFlow(false)
    val isSafetyCheckActive: StateFlow<Boolean> = _isSafetyCheckActive.asStateFlow()

    private val _riskScore = MutableStateFlow(0)
    val riskScore: StateFlow<Int> = _riskScore.asStateFlow()

    private val _activeAnomalies = MutableStateFlow<List<String>>(emptyList())
    val activeAnomalies: StateFlow<List<String>> = _activeAnomalies.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(30)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _activeSafetyMode = MutableStateFlow(SafetyMode.REGULAR)
    val activeSafetyMode: StateFlow<SafetyMode> = _activeSafetyMode.asStateFlow()

    private val _modeSuggestion = MutableStateFlow<SafetyMode?>(null)
    val modeSuggestion: StateFlow<SafetyMode?> = _modeSuggestion.asStateFlow()

    var cachedProfile: UserProfile? = null
        private set

    // Tracking variables
    private var lastKnownLocation: UserLocation? = null
    private var lastMovementTime = 0L
    private var recentShakeDetected = false
    private var currentRiskEvent: RiskEvent? = null

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        Log.i(TAG, "SafetyMonitoringManager initialized")
    }

    fun setProactiveEnabled(enabled: Boolean) {
        _isProactiveEnabled.value = enabled
        Log.d(TAG, "Proactive monitoring status set to: $enabled")
        if (!enabled && _isTripActive.value) {
            endTrip()
        }
    }

    fun setSafetyMode(mode: String) {
        _safetyMode.value = mode
        Log.d(TAG, "Safety mode set to: $mode")
    }

    fun updateProfileCache(profile: UserProfile) {
        this.cachedProfile = profile
        val mode = SafetyMode.fromString(profile.activeSafetyMode)
        _activeSafetyMode.value = mode
        Log.d(TAG, "Profile cache updated: activeSafetyMode=${profile.activeSafetyMode}")

        // Clear suggestions for the newly activated mode
        if (_modeSuggestion.value == mode) {
            _modeSuggestion.value = null
        }

        // Dynamically adjust location tracking interval based on safety mode
        appContext?.let { context ->
            val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasLocationPermission) {
                val isJourneyActive = TravelModeManager.journeyState.value == JourneyState.ACTIVE ||
                        TravelModeManager.journeyState.value == JourneyState.DEVIATION_DETECTED ||
                        TravelModeManager.journeyState.value == JourneyState.NO_RESPONSE
                
                when (mode) {
                    SafetyMode.TRAVELLING -> {
                        if (isJourneyActive) {
                            LocationTrackingService.startTracking(context, 5000L) // 5s high frequency
                        } else {
                            LocationTrackingService.startTracking(context, 10000L) // 10s setup/standby
                        }
                    }
                    SafetyMode.WORKING -> {
                        LocationTrackingService.startTracking(context, 10000L) // 10s updates
                    }
                    SafetyMode.SLEEPING -> {
                        LocationTrackingService.startTracking(context, 60000L) // 60s lower frequency
                    }
                    SafetyMode.REGULAR -> {
                        if (profile.locationTrackingEnabled) {
                            LocationTrackingService.startTracking(context, 10000L) // 10s normal updates
                        } else {
                            LocationTrackingService.stopTracking(context)
                        }
                    }
                }
            }
        }
    }

    fun dismissSuggestion() {
        _modeSuggestion.value = null
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    fun startTrip(start: UserLocation?, dest: UserLocation?) {
        _tripStartLocation.value = start
        _destinationLocation.value = dest
        _isTripActive.value = true
        lastMovementTime = System.currentTimeMillis()
        recentShakeDetected = false
        _riskScore.value = 0
        _activeAnomalies.value = emptyList()
        _isSafetyCheckActive.value = false
        _countdownSeconds.value = 30
        currentRiskEvent = null
        lastKnownLocation = start
        Log.i(TAG, "Safe Trip started from ${start?.latitude},${start?.longitude} towards ${dest?.latitude},${dest?.longitude}")
    }

    fun endTrip() {
        Log.i(TAG, "Safe Trip stopped")
        _isTripActive.value = false
        _tripStartLocation.value = null
        _destinationLocation.value = null
        _isSafetyCheckActive.value = false
        countdownJob?.cancel()
        countdownJob = null
    }

    fun reportShakeEvent() {
        val activeMode = _activeSafetyMode.value
        if (!_isProactiveEnabled.value) {
            Log.d(TAG, "Shake ignored: proactive monitoring inactive")
            return
        }
        if (activeMode == SafetyMode.SLEEPING) {
            Log.d(TAG, "Shake ignored: disabled in Sleeping Mode")
            return
        }
        Log.w(TAG, "Shake event reported to Proactive Safety Module (Mode: ${activeMode.name})")
        recentShakeDetected = true
        assessCurrentRisk(lastKnownLocation)
    }

    fun onLocationUpdated(location: UserLocation) {
        lastKnownLocation = location
        if (!_isProactiveEnabled.value) return

        val activeMode = _activeSafetyMode.value
        if (activeMode == SafetyMode.SLEEPING) return

        // Update movement timer if user is actively moving (speed > 2.0 km/h)
        val speedKmh = (location.speed ?: 0f) * 3.6f
        if (speedKmh > 2.0f) {
            lastMovementTime = System.currentTimeMillis()
        }

        // Suggestions Engine Check
        val profile = cachedProfile
        if (profile != null && profile.autoModeSuggestionsEnabled) {
            // 1. Suggest Travelling Mode
            if (activeMode != SafetyMode.TRAVELLING && speedKmh > 15.0f) {
                _modeSuggestion.value = SafetyMode.TRAVELLING
                Log.i(TAG, "Speed is $speedKmh km/h. Suggesting Travelling Mode.")
            }
            // 2. Suggest Working Mode
            if (activeMode != SafetyMode.WORKING) {
                val workLat = profile.savedWorkplaceLatitude
                val workLng = profile.savedWorkplaceLongitude
                if (workLat != null && workLng != null) {
                    val distance = haversineDistance(location.latitude, location.longitude, workLat, workLng)
                    if (distance <= 150.0) {
                        _modeSuggestion.value = SafetyMode.WORKING
                        Log.i(TAG, "Distance to workplace is $distance m. Suggesting Working Mode.")
                    }
                }
            }
        }

        assessCurrentRisk(location)
    }

    private fun assessCurrentRisk(location: UserLocation?) {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val isLateNight = hour >= 22 || hour < 5 // 10 PM to 5 AM

        val isTravelDeviated = TravelModeManager.journeyState.value == JourneyState.DEVIATION_DETECTED
        val isTravelNoResponse = TravelModeManager.journeyState.value == JourneyState.NO_RESPONSE

        val result = RiskAssessmentEngine.assessRisk(
            currentLocation = location,
            tripStart = _tripStartLocation.value,
            destination = _destinationLocation.value,
            recentShakeDetected = recentShakeDetected,
            safetyMode = _safetyMode.value,
            isLateNight = isLateNight,
            lastMovementTime = lastMovementTime,
            currentTime = now,
            isRouteDeviated = isTravelDeviated,
            isNoResponseToDeviation = isTravelNoResponse,
            activeOperatingMode = _activeSafetyMode.value
        )

        _riskScore.value = result.riskScore
        _activeAnomalies.value = result.activeAnomalies

        Log.d(TAG, "Assessed Risk score: ${result.riskScore}, anomalies: ${result.activeAnomalies}")

        if (result.riskScore >= DEFAULT_THRESHOLD && !_isSafetyCheckActive.value) {
            triggerSafetyCheck(location)
        }
    }

    var isTestMode: Boolean = false

    private fun triggerSafetyCheck(location: UserLocation?) {
        Log.w(TAG, "Risk score exceeded threshold! Triggering safety check...")
        _isSafetyCheckActive.value = true
        _countdownSeconds.value = 60 // 60-second response window per capstone specification

        val eventId = UUID.randomUUID().toString()
        val userId = authManager.currentUserId ?: ""
        val event = RiskEvent(
            id = eventId,
            userId = userId,
            timestamp = System.currentTimeMillis(),
            riskScore = _riskScore.value,
            activeAnomalies = _activeAnomalies.value,
            safetyMode = _safetyMode.value,
            status = "PENDING",
            latitude = location?.latitude,
            longitude = location?.longitude
        )
        currentRiskEvent = event

        // Persist to repository (Firestore log) unless in isolated test mode
        if (!isTestMode) {
            serviceScope.launch {
                riskRepository.createRiskEvent(event)
            }
        }

        // Play vibration alert and display system notification
        appContext?.let { ctx ->
            vibratePhone(ctx)
            com.womensafety.system.utils.SafetyNotificationManager.showSafetyCheckNotification(
                context = ctx,
                eventId = eventId,
                riskScore = _riskScore.value,
                countdownSeconds = _countdownSeconds.value
            )
        }

        // Start countdown timer
        startCountdown()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            while (_countdownSeconds.value > 0) {
                delay(1000)
                _countdownSeconds.value -= 1
            }
            // Escalation stage on timeout (no response after 60 seconds)
            escalateToSOS("EXPIRED")
        }
    }

    fun resolveSafetyCheck(context: Context, safe: Boolean) {
        countdownJob?.cancel()
        countdownJob = null

        // Dismiss the system notification immediately
        com.womensafety.system.utils.SafetyNotificationManager.cancelSafetyCheckNotification(context)

        val event = currentRiskEvent
        val status = if (safe) "SAFE" else "ESCALATED"

        if (event != null && !isTestMode) {
            currentRiskEvent = event.copy(status = status)
            serviceScope.launch {
                riskRepository.updateRiskEventStatus(event.id, status)
            }
        }

        _isSafetyCheckActive.value = false
        recentShakeDetected = false
        _riskScore.value = 0
        _activeAnomalies.value = emptyList()
        lastMovementTime = System.currentTimeMillis() // Reset movement reference

        if (!safe) {
            if (isTestMode) {
                Log.w(TAG, "TEST MODE: User clicked I NEED HELP. Real SOS/SMS dispatch suppressed.")
            } else {
                // Trigger emergency escalation to existing SOSManager
                serviceScope.launch {
                    SOSManager.triggerSOS(context, SOSTriggerMethod.AUTO)
                }
            }
            endTrip() // End trip after escalating
        } else {
            if (TravelModeManager.journeyState.value == JourneyState.DEVIATION_DETECTED) {
                TravelModeManager.confirmSafetyAndRecalculate()
            }
            Log.i(TAG, "Safety check resolved: User clicked I'M SAFE. Resuming monitoring.")
        }
    }

    fun reportDeviationAnomaly(location: UserLocation) {
        if (_isSafetyCheckActive.value) return
        Log.w(TAG, "Route deviation anomaly reported. Triggering safety check...")
        _activeAnomalies.value = listOf("Significant Route Deviation")
        _riskScore.value = 75
        triggerSafetyCheck(location)
    }

    private fun escalateToSOS(status: String) {
        val event = currentRiskEvent ?: return
        val updatedEvent = event.copy(status = status)
        currentRiskEvent = updatedEvent

        // Dismiss system notification on timeout
        appContext?.let { ctx ->
            com.womensafety.system.utils.SafetyNotificationManager.cancelSafetyCheckNotification(ctx)
        }

        if (isTestMode) {
            Log.w(TAG, "TEST MODE: Safety check timed out. Real SOS/SMS dispatch suppressed.")
        } else {
            serviceScope.launch {
                riskRepository.updateRiskEventStatus(event.id, status)
                appContext?.let { context ->
                    SOSManager.triggerSOS(context, SOSTriggerMethod.AUTO)
                }
            }
        }

        _isSafetyCheckActive.value = false
        recentShakeDetected = false
        _riskScore.value = 0
        _activeAnomalies.value = emptyList()
        _isTripActive.value = false // Auto-stop trip on escalation
        Log.w(TAG, "Safety check timed out without response (status: $status).")
    }

    private fun vibratePhone(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }
}
