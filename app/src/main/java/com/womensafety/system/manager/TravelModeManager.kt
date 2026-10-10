package com.womensafety.system.manager

import android.content.Context
import android.util.Log
import org.osmdroid.util.GeoPoint
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.JourneyRecord
import com.womensafety.system.data.model.SOSTriggerMethod
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.model.SafetyMode
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.data.repository.JourneyRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class JourneyState {
    NOT_STARTED,
    ACTIVE,
    DEVIATION_DETECTED,
    USER_CONFIRMED,
    NO_RESPONSE,
    ESCALATED,
    COMPLETED
}

/**
 * Main coordinator for Travel Mode & route-deviation monitoring.
 * Tracks the journey state machine, deviation math, and escalation logic using OpenStreetMap GeoPoints.
 */
object TravelModeManager {

    private const val TAG = "TravelModeManager"
    private val authManager = FirebaseAuthManager()
    private val journeyRepository = JourneyRepository()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var countdownJob: Job? = null
    private var durationJob: Job? = null
    private var appContext: Context? = null

    // State flows for UI mapping
    private val _journeyState = MutableStateFlow(JourneyState.NOT_STARTED)
    val journeyState: StateFlow<JourneyState> = _journeyState.asStateFlow()

    private val _expectedRoute = MutableStateFlow<List<GeoPoint>>(emptyList())
    val expectedRoute: StateFlow<List<GeoPoint>> = _expectedRoute.asStateFlow()

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private val _destinationLocation = MutableStateFlow<GeoPoint?>(null)
    val destinationLocation: StateFlow<GeoPoint?> = _destinationLocation.asStateFlow()

    private val _currentDeviationDistance = MutableStateFlow(0.0)
    val currentDeviationDistance: StateFlow<Double> = _currentDeviationDistance.asStateFlow()

    private val _speedKmh = MutableStateFlow(0f)
    val speedKmh: StateFlow<Float> = _speedKmh.asStateFlow()

    private val _stopCount = MutableStateFlow(0)
    val stopCount: StateFlow<Int> = _stopCount.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()

    private val _isDeviationPromptActive = MutableStateFlow(false)
    val isDeviationPromptActive: StateFlow<Boolean> = _isDeviationPromptActive.asStateFlow()

    private val _deviationCountdownSeconds = MutableStateFlow(30)
    val deviationCountdownSeconds: StateFlow<Int> = _deviationCountdownSeconds.asStateFlow()

    // Configurable thresholds
    var routeDeviationToleranceMeters = 300
    private var startLocation: GeoPoint? = null
    private val traveledLocations = mutableListOf<UserLocation>()

    // Persistence monitoring
    private var deviatedUpdatesCount = 0
    private var lastMovementTime = 0L
    private var isStopActive = false
    private var stopStartTime = 0L

    // Escalation trigger after no response
    private var continuedDeviationsCount = 0

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        Log.i(TAG, "TravelModeManager initialized with OSMDroid support")
    }

    fun startJourney(start: GeoPoint, dest: GeoPoint) {
        Log.i(TAG, "Starting journey from $start to $dest")
        this.startLocation = start
        _currentLocation.value = start
        _destinationLocation.value = dest
        val context = appContext
        if (context != null) {
            serviceScope.launch {
                val route = DirectionsApiHelper.getRoute(context, start, dest)
                _expectedRoute.value = route
            }
        } else {
            _expectedRoute.value = generateMockRoute(start, dest)
        }
        _journeyState.value = JourneyState.ACTIVE
        _currentDeviationDistance.value = 0.0
        _speedKmh.value = 0f
        _stopCount.value = 0
        _durationSeconds.value = 0L
        _isDeviationPromptActive.value = false
        _deviationCountdownSeconds.value = 30
        traveledLocations.clear()
        traveledLocations.add(UserLocation(latitude = start.latitude, longitude = start.longitude, timestamp = System.currentTimeMillis()))

        deviatedUpdatesCount = 0
        continuedDeviationsCount = 0
        lastMovementTime = System.currentTimeMillis()
        isStopActive = false

        // Also notify safety monitoring manager
        val startUserLoc = UserLocation(latitude = start.latitude, longitude = start.longitude, timestamp = System.currentTimeMillis())
        val destUserLoc = UserLocation(latitude = dest.latitude, longitude = dest.longitude, timestamp = System.currentTimeMillis())
        SafetyMonitoringManager.startTrip(startUserLoc, destUserLoc)

        appContext?.let { ctx ->
            LocationTrackingService.startTracking(ctx, 5000L)
        }

        startDurationTimer()
    }

    fun pauseJourney() {
        Log.d(TAG, "Journey paused")
        durationJob?.cancel()
    }

    fun resumeJourney() {
        Log.d(TAG, "Journey resumed")
        startDurationTimer()
    }

    fun completeJourney() {
        Log.i(TAG, "Completing journey")
        _journeyState.value = JourneyState.COMPLETED
        durationJob?.cancel()
        countdownJob?.cancel()

        val userId = authManager.currentUserId ?: ""
        val startLoc = startLocation
        val destLoc = _destinationLocation.value

        if (startLoc != null && destLoc != null) {
            val record = JourneyRecord(
                id = UUID.randomUUID().toString(),
                userId = userId,
                startTime = traveledLocations.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                startLatitude = startLoc.latitude,
                startLongitude = startLoc.longitude,
                destLatitude = destLoc.latitude,
                destLongitude = destLoc.longitude,
                status = "COMPLETED",
                routePoints = traveledLocations.toList()
            )
            serviceScope.launch {
                journeyRepository.createJourneyRecord(record)
            }
        }

        appContext?.let { ctx ->
            val profile = SafetyMonitoringManager.cachedProfile
            if (profile != null) {
                val mode = SafetyMode.fromString(profile.activeSafetyMode)
                if (mode == SafetyMode.REGULAR && !profile.locationTrackingEnabled) {
                    LocationTrackingService.stopTracking(ctx)
                } else {
                    val interval = when (mode) {
                        SafetyMode.WORKING -> 15000L
                        SafetyMode.SLEEPING -> 60000L
                        else -> 30000L
                    }
                    LocationTrackingService.startTracking(ctx, interval)
                }
            } else {
                LocationTrackingService.startTracking(ctx, 30000L)
            }
        }

        SafetyMonitoringManager.endTrip()
        resetState()
    }

    fun cancelJourney() {
        Log.i(TAG, "Journey cancelled")
        durationJob?.cancel()
        countdownJob?.cancel()

        appContext?.let { ctx ->
            val profile = SafetyMonitoringManager.cachedProfile
            if (profile != null) {
                val mode = SafetyMode.fromString(profile.activeSafetyMode)
                if (mode == SafetyMode.REGULAR && !profile.locationTrackingEnabled) {
                    LocationTrackingService.stopTracking(ctx)
                } else {
                    val interval = when (mode) {
                        SafetyMode.WORKING -> 15000L
                        SafetyMode.SLEEPING -> 60000L
                        else -> 30000L
                    }
                    LocationTrackingService.startTracking(ctx, interval)
                }
            } else {
                LocationTrackingService.startTracking(ctx, 30000L)
            }
        }

        SafetyMonitoringManager.endTrip()
        resetState()
    }

    fun onLocationUpdated(location: UserLocation) {
        if (_journeyState.value == JourneyState.NOT_STARTED || _journeyState.value == JourneyState.COMPLETED) return

        val geoPoint = GeoPoint(location.latitude, location.longitude)
        _currentLocation.value = geoPoint
        traveledLocations.add(location)

        // Track speed and stops
        val currentSpeedKmh = (location.speed ?: 0f) * 3.6f
        _speedKmh.value = currentSpeedKmh

        if (currentSpeedKmh < 1.5f) {
            if (!isStopActive) {
                isStopActive = true
                stopStartTime = System.currentTimeMillis()
            } else {
                val stopDuration = System.currentTimeMillis() - stopStartTime
                if (stopDuration > 60_000 && stopDuration < 62_000) {
                    _stopCount.value += 1
                }
            }
        } else {
            isStopActive = false
            lastMovementTime = System.currentTimeMillis()
        }

        // Check if user has reached destination
        val dest = _destinationLocation.value
        if (dest != null) {
            val distanceToDest = haversineDistance(location.latitude, location.longitude, dest.latitude, dest.longitude)
            if (distanceToDest <= 50.0) {
                Log.i(TAG, "Destination reached! Proximity: $distanceToDest m")
                appContext?.let { ctx ->
                    android.widget.Toast.makeText(ctx, "✅ Destination Reached!", android.widget.Toast.LENGTH_LONG).show()
                }
                completeJourney()
                return
            }
        }

        // Calculate perpendicular deviation to expected route
        val route = _expectedRoute.value
        if (route.size >= 2) {
            val deviation = distanceToRoute(geoPoint, route)
            _currentDeviationDistance.value = deviation
            Log.d(TAG, "Distance to route: $deviation meters")

            if (deviation > routeDeviationToleranceMeters) {
                handleDeviationDetected(location)
            } else {
                deviatedUpdatesCount = 0
                if (_journeyState.value == JourneyState.DEVIATION_DETECTED) {
                    Log.i(TAG, "User returned to route. Clearing warning.")
                    _journeyState.value = JourneyState.ACTIVE
                    _isDeviationPromptActive.value = false
                    countdownJob?.cancel()
                }
            }
        }
    }

    private fun handleDeviationDetected(location: UserLocation) {
        if (_journeyState.value == JourneyState.ACTIVE) {
            deviatedUpdatesCount++
            Log.w(TAG, "Deviation detected! Sequence count: $deviatedUpdatesCount / 3")
            if (deviatedUpdatesCount >= 3) {
                _journeyState.value = JourneyState.DEVIATION_DETECTED
                _isDeviationPromptActive.value = true
                _deviationCountdownSeconds.value = 30
                startCountdown()
                SafetyMonitoringManager.reportDeviationAnomaly(location)
            }
        }
    }

    fun confirmSafetyAndRecalculate() {
        Log.i(TAG, "User confirmed safety. Recalculating route...")
        countdownJob?.cancel()
        countdownJob = null

        val current = _currentLocation.value
        val dest = _destinationLocation.value
        val context = appContext

        if (current != null && dest != null) {
            if (context != null) {
                serviceScope.launch {
                    val route = DirectionsApiHelper.getRoute(context, current, dest)
                    _expectedRoute.value = route
                }
            } else {
                _expectedRoute.value = generateMockRoute(current, dest)
            }
        }

        _journeyState.value = JourneyState.ACTIVE
        _isDeviationPromptActive.value = false
        deviatedUpdatesCount = 0
        continuedDeviationsCount = 0
    }

    fun ignoreDeviationPrompt() {
        countdownJob?.cancel()
        handleNoResponse()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            while (_deviationCountdownSeconds.value > 0) {
                delay(1000)
                _deviationCountdownSeconds.value -= 1
            }
            handleNoResponse()
        }
    }

    private fun handleNoResponse() {
        Log.w(TAG, "No response from user. Transitioning to NO_RESPONSE and escalating to SOS.")
        _journeyState.value = JourneyState.NO_RESPONSE
        _isDeviationPromptActive.value = false
        escalateToSOS()
    }

    private fun escalateToSOS() {
        Log.e(TAG, "Escalating deviation to SOS!")
        _journeyState.value = JourneyState.ESCALATED
        durationJob?.cancel()

        appContext?.let { context ->
            serviceScope.launch {
                SOSManager.triggerSOS(context, SOSTriggerMethod.AUTO)
            }
        }

        val userId = authManager.currentUserId ?: ""
        val startLoc = startLocation
        val destLoc = _destinationLocation.value
        if (startLoc != null && destLoc != null) {
            val record = JourneyRecord(
                id = UUID.randomUUID().toString(),
                userId = userId,
                startTime = traveledLocations.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                startLatitude = startLoc.latitude,
                startLongitude = startLoc.longitude,
                destLatitude = destLoc.latitude,
                destLongitude = destLoc.longitude,
                status = "ESCALATED",
                routePoints = traveledLocations.toList()
            )
            serviceScope.launch {
                journeyRepository.createJourneyRecord(record)
            }
        }

        resetState()
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = serviceScope.launch {
            while (true) {
                delay(1000)
                _durationSeconds.value += 1
            }
        }
    }

    private fun resetState() {
        _journeyState.value = JourneyState.NOT_STARTED
        _expectedRoute.value = emptyList()
        _currentLocation.value = null
        _destinationLocation.value = null
        _currentDeviationDistance.value = 0.0
        _speedKmh.value = 0f
        _stopCount.value = 0
        _durationSeconds.value = 0L
        _isDeviationPromptActive.value = false
        _deviationCountdownSeconds.value = 30
        traveledLocations.clear()
        startLocation = null
        deviatedUpdatesCount = 0
        continuedDeviationsCount = 0
    }

    private fun generateMockRoute(start: GeoPoint, dest: GeoPoint): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        points.add(start)

        val latDiff = dest.latitude - start.latitude
        val lngDiff = dest.longitude - start.longitude

        val turn1 = GeoPoint(start.latitude + latDiff * 0.4, start.longitude + lngDiff * 0.1)
        points.add(turn1)

        val turn2 = GeoPoint(start.latitude + latDiff * 0.7, start.longitude + lngDiff * 0.8)
        points.add(turn2)

        points.add(dest)
        return points
    }

    private fun distanceToRoute(p: GeoPoint, route: List<GeoPoint>): Double {
        if (route.size < 2) return 0.0
        var minDistance = Double.MAX_VALUE
        for (i in 0 until route.size - 1) {
            val dist = distanceToSegment(p, route[i], route[i + 1])
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    private fun distanceToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val xA = a.longitude
        val yA = a.latitude
        val xB = b.longitude
        val yB = b.latitude
        val xP = p.longitude
        val yP = p.latitude

        val dx = xB - xA
        val dy = yB - yA

        if (dx == 0.0 && dy == 0.0) {
            return haversineDistance(p.latitude, p.longitude, a.latitude, a.longitude)
        }

        var t = ((xP - xA) * dx + (yP - yA) * dy) / (dx * dx + dy * dy)
        t = t.coerceIn(0.0, 1.0)

        val closestX = xA + t * dx
        val closestY = yA + t * dy

        return haversineDistance(p.latitude, p.longitude, closestY, closestX)
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
