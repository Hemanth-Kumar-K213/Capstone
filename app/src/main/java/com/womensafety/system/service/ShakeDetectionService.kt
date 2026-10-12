package com.womensafety.system.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.womensafety.system.MainActivity
import com.womensafety.system.R
import com.womensafety.system.data.model.SOSTriggerMethod
import com.womensafety.system.data.model.ShakeSensitivity
import com.womensafety.system.manager.SOSManager
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.manager.SafetyMonitoringManager
import com.womensafety.system.utils.NotificationChannelManager
import com.womensafety.system.utils.ShakeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that monitors the accelerometer sensor for emergency shake gestures.
 */
class ShakeDetectionService : Service() {

    companion object {
        private const val TAG = "ShakeDetectionService"
        private const val NOTIFICATION_ID = 2002

        const val ACTION_START_SHAKE_DETECTION = "com.womensafety.system.action.START_SHAKE"
        const val ACTION_STOP_SHAKE_DETECTION = "com.womensafety.system.action.STOP_SHAKE"
        const val ACTION_UPDATE_SENSITIVITY = "com.womensafety.system.action.UPDATE_SENSITIVITY"
        const val EXTRA_SENSITIVITY = "extra_sensitivity"

        var isRunning = false
            private set

        fun startService(context: Context, sensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM) {
            try {
                val intent = Intent(context, ShakeDetectionService::class.java).apply {
                    action = ACTION_START_SHAKE_DETECTION
                    putExtra(EXTRA_SENSITIVITY, sensitivity.name)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed startService in ShakeDetectionService", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, ShakeDetectionService::class.java).apply {
                    action = ACTION_STOP_SHAKE_DETECTION
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed stopService in ShakeDetectionService", e)
            }
        }

        fun updateSensitivity(context: Context, sensitivity: ShakeSensitivity) {
            try {
                val intent = Intent(context, ShakeDetectionService::class.java).apply {
                    action = ACTION_UPDATE_SENSITIVITY
                    putExtra(EXTRA_SENSITIVITY, sensitivity.name)
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed updateSensitivity in ShakeDetectionService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null
    private var currentSensitivity = ShakeSensitivity.MEDIUM
    private val authManager = FirebaseAuthManager()
    private val userRepository = UserRepository()

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        NotificationChannelManager.createNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SHAKE_DETECTION -> {
                val sensitivityName = intent.getStringExtra(EXTRA_SENSITIVITY) ?: ShakeSensitivity.MEDIUM.name
                currentSensitivity = ShakeSensitivity.fromString(sensitivityName)
                startShakeMonitoring()
            }
            ACTION_STOP_SHAKE_DETECTION -> {
                stopShakeMonitoring()
                stopSelf()
            }
            ACTION_UPDATE_SENSITIVITY -> {
                val sensitivityName = intent.getStringExtra(EXTRA_SENSITIVITY) ?: ShakeSensitivity.MEDIUM.name
                currentSensitivity = ShakeSensitivity.fromString(sensitivityName)
                shakeDetector?.updateSensitivity(currentSensitivity)
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun startShakeMonitoring() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            isRunning = true

            shakeDetector?.stop(sensorManager)
            shakeDetector = ShakeDetector(sensitivity = currentSensitivity) {
                onShakeTriggered()
            }
            shakeDetector?.start(sensorManager)
            Log.d(TAG, "Shake detection monitoring started with sensitivity: ${currentSensitivity.displayName}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed startShakeMonitoring in ShakeDetectionService", e)
            isRunning = false
            stopSelf()
        }
    }

    private fun stopShakeMonitoring() {
        shakeDetector?.stop(sensorManager)
        shakeDetector = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "Shake detection monitoring stopped")
    }

    private fun onShakeTriggered() {
        Log.w(TAG, "EMERGENCY SHAKE DETECTED!")

        // 1. Provide urgent haptic vibration (3 short bursts)
        vibratePhone()

        // 2. Route to proactive safety monitoring if enabled, else legacy SOS
        serviceScope.launch {
            try {
                val userId = authManager.currentUserId
                val proactiveEnabled = if (userId != null) {
                    userRepository.getUserProfile(userId).getOrNull()?.proactiveMonitoringEnabled ?: false
                } else {
                    false
                }

                val activeMode = SafetyMonitoringManager.activeSafetyMode.value
                val isSleeping = activeMode == com.womensafety.system.data.model.SafetyMode.SLEEPING

                if (proactiveEnabled && !isSleeping) {
                    SafetyMonitoringManager.reportShakeEvent()
                } else {
                    SOSManager.triggerSOS(
                        context = applicationContext,
                        triggerMethod = SOSTriggerMethod.SHAKE
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling shake trigger", e)
            }
        }
    }

    private fun vibratePhone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val pattern = longArrayOf(0, 300, 150, 300, 150, 300)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val pattern = longArrayOf(0, 300, 150, 300, 150, 300)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Haptic vibration failed", e)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ShakeDetectionService::class.java).apply {
            action = ACTION_STOP_SHAKE_DETECTION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationChannelManager.CHANNEL_SHAKE_DETECTION)
            .setContentTitle("Trinetra • Shake Detection Active")
            .setContentText("Shake phone vigorously to trigger SOS (${currentSensitivity.displayName})")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disable", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        if (isRunning) {
            val notification = buildNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopShakeMonitoring()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
