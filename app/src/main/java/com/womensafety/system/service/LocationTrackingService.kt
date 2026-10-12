package com.womensafety.system.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.womensafety.system.MainActivity
import com.womensafety.system.R
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.LocationServiceState
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.repository.LocationRepository
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.data.repository.UidRepository
import com.womensafety.system.manager.SafetyMonitoringManager
import com.womensafety.system.manager.TravelModeManager
import com.womensafety.system.utils.NotificationChannelManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Foreground service that captures and streams high-accuracy GPS coordinates to Firestore.
 */
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_ID = 2001
        private const val WAKELOCK_TAG = "WomenSafety:LocationWakeLock"

        const val ACTION_START_TRACKING = "com.womensafety.system.action.START_LOCATION_TRACKING"
        const val ACTION_PAUSE_TRACKING = "com.womensafety.system.action.PAUSE_LOCATION_TRACKING"
        const val ACTION_RESUME_TRACKING = "com.womensafety.system.action.RESUME_LOCATION_TRACKING"
        const val ACTION_STOP_TRACKING = "com.womensafety.system.action.STOP_LOCATION_TRACKING"
        const val ACTION_SOS_MODE = "com.womensafety.system.action.LOCATION_SOS_MODE"
        const val ACTION_NORMAL_MODE = "com.womensafety.system.action.LOCATION_NORMAL_MODE"
        const val ACTION_UPDATE_INTERVAL = "com.womensafety.system.action.UPDATE_LOCATION_INTERVAL"

        const val EXTRA_INTERVAL_MS = "extra_interval_ms"

        const val DEFAULT_NORMAL_INTERVAL_MS = 10_000L // 10 seconds for real-time live tracking
        const val SOS_INTERVAL_MS = 5_000L              // 5 seconds

        private val _currentLocation = MutableStateFlow<UserLocation?>(null)
        val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()

        private val _serviceState = MutableStateFlow(LocationServiceState.IDLE)
        val serviceState: StateFlow<LocationServiceState> = _serviceState.asStateFlow()

        var isRunning = false
            private set

        var isSosMode = false
            private set

        var cachedWeeklyUid: String = ""
            private set

        var cachedUserName: String = "User"
            private set

        fun setTargetUid(uid: String, name: String = "") {
            if (uid.isNotBlank()) {
                cachedWeeklyUid = uid.trim()
            }
            if (name.isNotBlank()) {
                cachedUserName = name.trim()
            }
        }

        fun startTracking(context: Context, intervalMs: Long = DEFAULT_NORMAL_INTERVAL_MS) {
            try {
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasFine && !hasCoarse) {
                    Log.w(TAG, "Location permission not granted, skipping startTracking until granted")
                    return
                }

                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    action = ACTION_START_TRACKING
                    putExtra(EXTRA_INTERVAL_MS, intervalMs)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed startTracking", e)
            }
        }

        fun pauseTracking(context: Context) {
            try {
                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    action = ACTION_PAUSE_TRACKING
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed pauseTracking", e)
            }
        }

        fun resumeTracking(context: Context) {
            try {
                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    action = ACTION_RESUME_TRACKING
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed resumeTracking", e)
            }
        }

        fun stopTracking(context: Context) {
            try {
                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    action = ACTION_STOP_TRACKING
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed stopTracking", e)
            }
        }

        fun enableSOSMode(context: Context) {
            try {
                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    action = ACTION_SOS_MODE
                }
                if (isRunning) {
                    context.startService(intent)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed enableSOSMode", e)
            }
        }

        fun disableSOSMode(context: Context) {
            try {
                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    action = ACTION_NORMAL_MODE
                }
                if (isRunning) {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed disableSOSMode", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var wakeLock: PowerManager.WakeLock? = null

    private val locationRepository = LocationRepository()
    private val userRepository = UserRepository()
    private val uidRepository = UidRepository()
    private val authManager = FirebaseAuthManager()

    private var currentIntervalMs = DEFAULT_NORMAL_INTERVAL_MS

    override fun onCreate() {
        super.onCreate()
        NotificationChannelManager.createNotificationChannels(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)

        setupLocationCallback()
        loadCachedUser()
    }

    private fun loadCachedUser() {
        serviceScope.launch {
            val currentUserId = authManager.currentUserId ?: return@launch
            val userProfile = userRepository.getUserProfile(currentUserId).getOrNull()
            if (userProfile != null) {
                setTargetUid(userProfile.currentWeeklyUid, userProfile.name)
            }
            if (cachedWeeklyUid.isBlank()) {
                val activeUid = uidRepository.getCurrentUid(currentUserId).getOrNull()
                if (activeUid != null && activeUid.uid.isNotBlank()) {
                    setTargetUid(activeUid.uid, activeUid.userName)
                }
            }
            if (cachedWeeklyUid.isBlank()) {
                val devUid = uidRepository.generateDevUid(currentUserId, cachedUserName).getOrNull()
                if (devUid != null && devUid.uid.isNotBlank()) {
                    setTargetUid(devUid.uid, devUid.userName)
                }
            }
            Log.d(TAG, "Location tracking initialized for UID: $cachedWeeklyUid, User: $cachedUserName")
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                handleNewLocation(location)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                currentIntervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_NORMAL_INTERVAL_MS)
                isSosMode = false
                startTrackingService()
            }
            ACTION_PAUSE_TRACKING -> {
                pauseTrackingService()
            }
            ACTION_RESUME_TRACKING -> {
                resumeTrackingService()
            }
            ACTION_STOP_TRACKING -> {
                stopTrackingService()
                stopSelf()
            }
            ACTION_SOS_MODE -> {
                isSosMode = true
                currentIntervalMs = SOS_INTERVAL_MS
                requestLocationUpdates()
                updateNotification()
            }
            ACTION_NORMAL_MODE -> {
                isSosMode = false
                currentIntervalMs = DEFAULT_NORMAL_INTERVAL_MS
                requestLocationUpdates()
                updateNotification()
            }
            ACTION_UPDATE_INTERVAL -> {
                val newInterval = intent.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_NORMAL_INTERVAL_MS)
                if (!isSosMode) {
                    currentIntervalMs = newInterval
                    requestLocationUpdates()
                }
            }
        }
        return START_STICKY
    }

    private fun startTrackingService() {
        _serviceState.value = LocationServiceState.STARTING
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            isRunning = true
            acquireWakeLock()
            requestLocationUpdates()
            _serviceState.value = LocationServiceState.TRACKING
            Log.d(TAG, "Location tracking service started with interval: ${currentIntervalMs}ms")
        } catch (e: Throwable) {
            Log.e(TAG, "Cannot startForeground in LocationTrackingService: ${e.message}", e)
            isRunning = false
            _serviceState.value = LocationServiceState.ERROR
            stopSelf()
        }
    }

    private fun pauseTrackingService() {
        removeLocationUpdates()
        _serviceState.value = LocationServiceState.PAUSED
        updateNotification()
        Log.d(TAG, "Location tracking paused")
    }

    private fun resumeTrackingService() {
        requestLocationUpdates()
        _serviceState.value = LocationServiceState.TRACKING
        updateNotification()
        Log.d(TAG, "Location tracking resumed")
    }

    private fun stopTrackingService() {
        _serviceState.value = LocationServiceState.STOPPING
        removeLocationUpdates()
        releaseWakeLock()
        isRunning = false
        _serviceState.value = LocationServiceState.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "Location tracking stopped")
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        try {
            // 1. Immediately request cached/last known location to acquire instant fix!
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d(TAG, "Instant lastLocation fix acquired: ${loc.latitude}, ${loc.longitude}")
                    handleNewLocation(loc)
                }
            }

            // 2. Also request fresh one-shot current location immediately!
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d(TAG, "Instant getCurrentLocation fix acquired: ${loc.latitude}, ${loc.longitude}")
                    handleNewLocation(loc)
                }
            }

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                currentIntervalMs
            ).apply {
                setMinUpdateIntervalMillis(5_000L) // 5s fastest interval
                setMinUpdateDistanceMeters(0f)     // Emit every 10s even when stationary
                setWaitForAccurateLocation(isSosMode)
            }.build()

            fusedLocationClient.removeLocationUpdates(locationCallback)
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
            _serviceState.value = LocationServiceState.ERROR
        }
    }

    private fun removeLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun handleNewLocation(location: Location) {
        val currentUserId = authManager.currentUserId ?: return

        serviceScope.launch {
            // Ensure cachedWeeklyUid is populated
            if (cachedWeeklyUid.isBlank()) {
                val userProfile = userRepository.getUserProfile(currentUserId).getOrNull()
                if (userProfile != null && userProfile.currentWeeklyUid.isNotBlank()) {
                    setTargetUid(userProfile.currentWeeklyUid, userProfile.name)
                } else {
                    val activeUid = uidRepository.getCurrentUid(currentUserId).getOrNull()
                    if (activeUid != null && activeUid.uid.isNotBlank()) {
                        setTargetUid(activeUid.uid, activeUid.userName)
                    } else {
                        val devUid = uidRepository.generateDevUid(currentUserId, cachedUserName).getOrNull()
                        if (devUid != null && devUid.uid.isNotBlank()) {
                            setTargetUid(devUid.uid, devUid.userName)
                        }
                    }
                }
            }

            val address = reverseGeocode(location.latitude, location.longitude)
            val userLocation = UserLocation(
                userId = currentUserId,
                uid = cachedWeeklyUid,
                userName = cachedUserName,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                bearing = if (location.hasBearing()) location.bearing else null,
                altitude = if (location.hasAltitude()) location.altitude else null,
                timestamp = System.currentTimeMillis(),
                speed = if (location.hasSpeed()) location.speed else null,
                address = address,
                isSOSLocation = isSosMode
            )

            // 1. Update StateFlow for immediate in-app UI
            _currentLocation.value = userLocation

            // 2. Persist to standard Firestore subcollection (userLocations)
            locationRepository.saveLocation(currentUserId, userLocation)
            Log.d(TAG, "Location saved to Firestore: ${location.latitude}, ${location.longitude}")

            // 3. Persist every 10 seconds to dedicated child_locations collection by UID
            if (cachedWeeklyUid.isNotBlank()) {
                val uidResult = locationRepository.saveUidLocation(cachedWeeklyUid, userLocation)
                if (uidResult.isSuccess) {
                    Log.d(TAG, "Uploaded 10s location to child_locations for UID: $cachedWeeklyUid")
                } else {
                    Log.w(TAG, "Failed uploading location for UID: $cachedWeeklyUid", uidResult.exceptionOrNull())
                }
            } else {
                Log.w(TAG, "UID is still empty for user $currentUserId; skipping child_locations upload this cycle")
            }

            // 4. Notify SafetyMonitoringManager & TravelModeManager
            SafetyMonitoringManager.onLocationUpdated(userLocation)
            TravelModeManager.onLocationUpdated(userLocation)
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull()
            if (addr != null) {
                val street = addr.thoroughfare ?: addr.featureName ?: ""
                val locality = addr.locality ?: addr.subAdminArea ?: ""
                val admin = addr.adminArea ?: ""
                val subLocality = addr.subLocality ?: ""
                listOf(street, subLocality, locality, admin).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed: ${e.message}")
            null
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock acquire error", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock release error", e)
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

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = _serviceState.value == LocationServiceState.PAUSED
        val toggleActionIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = if (isPaused) ACTION_RESUME_TRACKING else ACTION_PAUSE_TRACKING
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            2,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSosMode) "🚨 EMERGENCY • High-Frequency Tracking" else "Trinetra • Location Active"
        val subtitle = when {
            isSosMode -> "Updating location every 5 seconds during active SOS"
            isPaused -> "Location tracking paused"
            else -> "Sharing real-time location with approved trusted contacts"
        }

        val toggleTitle = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(this, NotificationChannelManager.CHANNEL_LOCATION_TRACKING)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(!isPaused)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, toggleTitle, togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(if (isSosMode) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
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
        stopTrackingService()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
