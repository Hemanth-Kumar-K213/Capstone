package com.womensafety.system.manager

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.*
import com.womensafety.system.data.repository.SleepModeRepository
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.data.sms.TwoFactorClient
import com.womensafety.system.utils.SoundManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager orchestrating Smart Emergency Override and Nocturnal Fall Protection in SLEEPING Safety Mode.
 * Ensures sleep is uninterrupted while strictly prioritizing critical multi-call overrides and falls.
 */
object EmergencyOverrideManager {

    private const val TAG = "EmergencyOverrideMgr"

    private val repository = SleepModeRepository()
    private val userRepository = UserRepository()
    private val authManager = FirebaseAuthManager()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val callTimestamps = mutableListOf<Long>()

    private val _isSleeping = MutableStateFlow(false)
    val isSleeping: StateFlow<Boolean> = _isSleeping.asStateFlow()

    private val _currentSession = MutableStateFlow<SleepSession?>(null)
    val currentSession: StateFlow<SleepSession?> = _currentSession.asStateFlow()

    private val _lastSleepQuality = MutableStateFlow<SleepQuality?>(null)
    val lastSleepQuality: StateFlow<SleepQuality?> = _lastSleepQuality.asStateFlow()

    private val _fallAlert = MutableStateFlow<FallDetection?>(null)
    val fallAlert: StateFlow<FallDetection?> = _fallAlert.asStateFlow()

    private val _fallCountdown = MutableStateFlow<Int?>(null)
    val fallCountdown: StateFlow<Int?> = _fallCountdown.asStateFlow()

    private val _criticalAlert = MutableStateFlow<CriticalAlertData?>(null)
    val criticalAlert: StateFlow<CriticalAlertData?> = _criticalAlert.asStateFlow()

    private var appContext: Context? = null
    private var fallCountdownJob: Job? = null

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        repository.initialize(context)
        _lastSleepQuality.value = repository.getLastSleepQuality()
    }

    /**
     * Initiates Sleep Mode: pauses passive location broadcasts, disables standard shake,
     * and arms emergency call and fall detection watchdog.
     */
    fun initiateSleepMode() {
        _isSleeping.value = true
        scope.launch {
            val session = repository.startSleepSession()
            _currentSession.value = session
            callTimestamps.clear()
            Log.d(TAG, "Sleep mode activated. Emergency override armed.")
        }
    }

    /**
     * Exits Sleep Mode: ends session, assesses sleep quality, and resumes normal sensors.
     */
    fun exitSleepMode() {
        _isSleeping.value = false
        fallCountdownJob?.cancel()
        _fallAlert.value = null
        _fallCountdown.value = null
        dismissCriticalAlert()

        scope.launch {
            val session = _currentSession.value
            if (session != null) {
                val ended = repository.endSleepSession(session)
                _lastSleepQuality.value = ended.quality
                _currentSession.value = null
                Log.d(TAG, "Sleep mode ended. Sleep quality evaluated: ${ended.quality}")
            }
        }
    }

    /**
     * Tracks incoming phone calls. If callThreshold (3 calls within 60s) is exceeded,
     * immediately triggers critical emergency wakeup!
     */
    fun onIncomingCall(callerNumber: String, callerName: String = "Emergency Contact") {
        if (!_isSleeping.value) return

        val now = System.currentTimeMillis()
        callTimestamps.add(now)

        // Purge timestamps older than 60 seconds
        callTimestamps.removeAll { now - it > 60_000L }

        Log.d(TAG, "Incoming call recorded from $callerName (${callTimestamps.size}/3 in last 60s)")

        if (callTimestamps.size >= 3) {
            triggerEmergencyWakeup(
                title = "MULTIPLE EMERGENCY CALLS",
                message = "CRITICAL: $callerName called 3 times in quick succession! Immediate attention required.",
                reason = "MULTIPLE_EMERGENCY_CALLS",
                callerName = callerName
            )
            callTimestamps.clear()
        }
    }

    /**
     * Triggers critical emergency wakeup: plays siren at max volume, intense vibration,
     * and displays full-screen override alert.
     */
    fun triggerEmergencyWakeup(
        title: String,
        message: String,
        reason: String,
        callerName: String? = null
    ) {
        val alert = CriticalAlertData(
            title = title,
            message = message,
            reason = reason,
            callerName = callerName
        )
        _criticalAlert.value = alert

        scope.launch {
            _currentSession.value?.let {
                repository.recordEmergencyWakeup(it.id, callerName ?: "Contact", reason)
            }
        }

        val context = appContext
        if (context != null) {
            // Maximum volume siren on USAGE_ALARM
            SoundManager.playSiren(context)

            // Heavy pulsing vibration
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null) {
                val pattern = longArrayOf(0, 600, 300, 600, 300, 600)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
            }
        }
    }

    /**
     * Detects fall during sleep and triggers a 10-second countdown prompt.
     * If user does not respond within 10 seconds, automatically escalates to emergency contacts!
     */
    fun detectFall(accelerationMagnitude: Float) {
        if (!_isSleeping.value) return

        val fall = FallDetection(
            timestamp = System.currentTimeMillis(),
            deviceMovement = accelerationMagnitude,
            confidence = 0.88f
        )
        _fallAlert.value = fall

        // Start 10-second confirmation countdown
        fallCountdownJob?.cancel()
        fallCountdownJob = scope.launch {
            for (i in 10 downTo 0) {
                _fallCountdown.value = i
                if (i == 0) {
                    // Countdown expired without confirmation -> Auto-dispatch alert!
                    onFallTimeoutEscalate(fall)
                    break
                }
                delay(1000L)
            }
        }

        scope.launch {
            repository.recordFallDetection(fall)
        }
    }

    /**
     * User confirmed safe from fall prompt.
     */
    fun confirmFallSafe() {
        fallCountdownJob?.cancel()
        _fallAlert.value = null
        _fallCountdown.value = null
        scope.launch {
            repository.recordFallDetection(FallDetection(userConfirmed = true))
        }
    }

    private suspend fun onFallTimeoutEscalate(fall: FallDetection) {
        _fallAlert.value = null
        _fallCountdown.value = null

        // Trigger critical alarm locally
        triggerEmergencyWakeup(
            title = "FALL DETECTED — UNRESPONSIVE",
            message = "A severe fall was detected during sleep mode and was not cancelled within 10 seconds. Notifying emergency contacts.",
            reason = "UNRESPONSIVE_FALL"
        )

        // Dispatch SMS alert to emergency contacts
        val uid = authManager.currentUserId
        if (!uid.isNullOrBlank()) {
            val profile = userRepository.getUserProfile(uid).getOrNull()
            val userName = profile?.name?.ifBlank { "User" } ?: "User"
            val phone = profile?.emergencyContacts?.firstOrNull { it.isPrimary }?.phone
                ?: profile?.emergencyContacts?.firstOrNull()?.phone

            if (!phone.isNullOrBlank()) {
                val smsBody = "🚨 CRITICAL FALL ALERT 🚨\n" +
                        "A potential fall was detected for $userName during sleep mode with no response after 10 seconds. " +
                        "Please check on them immediately!\n" +
                        "Sent automatically from Trinetra Sleep Protection."

                TwoFactorClient.sendTransactionalSms(phone, smsBody)
            }
        }
    }

    fun dismissCriticalAlert() {
        _criticalAlert.value = null
        appContext?.let {
            SoundManager.stopSiren()
            val vibrator = it.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.cancel()
        }
    }

    // ─── Capstone Demo Simulators ────────────────────────────────────────

    fun simulateConsecutiveCalls(callerName: String = "Mom (Primary Contact)") {
        initiateSleepMode()
        scope.launch {
            for (i in 1..3) {
                delay(300L)
                onIncomingCall(callerNumber = "+919876543210", callerName = callerName)
            }
        }
    }

    fun simulateFallEvent() {
        initiateSleepMode()
        detectFall(accelerationMagnitude = 28.5f)
    }
}
