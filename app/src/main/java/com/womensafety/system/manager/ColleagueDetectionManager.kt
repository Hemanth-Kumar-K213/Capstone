package com.womensafety.system.manager

import android.content.Context
import android.util.Log
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.data.model.ColleagueStatus
import com.womensafety.system.data.repository.SafetyBuddyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Requirement 2D: Colleague Detection Coordinator.
 *
 * Resolves colleague presence with tiered fallback hierarchy:
 * Tier 1 (BLE): distance < 150m, confidence 0.95
 * Tier 2 (Geofence): distance < 500m, confidence 0.70
 * Tier 3 (Firestore): location history < 5 min old, confidence 0.80
 * Tier 4 (Stale): discarded
 *
 * Selects highest confidence detection.
 * REMOVED ALL HARDCODED MOCKS (Priya, Ananya, Sneha, Vikram).
 */
class ColleagueDetectionManager(
    private val context: Context,
    private val firestoreManager: FirestoreManager = FirestoreManager(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val safetyBuddyRepository: SafetyBuddyRepository = SafetyBuddyRepository(firestoreManager, authManager),
    private val beaconManager: WorkplaceBeaconManager = WorkplaceBeaconManager(context),
    private val geofenceManager: WorkplaceGeofenceManager = WorkplaceGeofenceManager(context, firestoreManager, authManager)
) {

    companion object {
        private const val TAG = "ColleagueDetection"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _colleagues = MutableStateFlow<List<Colleague>>(emptyList())
    val colleagues: StateFlow<List<Colleague>> = _colleagues.asStateFlow()

    // Tier 1: Real-time BLE detections
    private val bleColleagues = ConcurrentHashMap<String, Colleague>()
    // Tier 2: Geofence fallback detections
    private val geofenceColleagues = ConcurrentHashMap<String, Colleague>()
    // Tier 3: Firestore remote location detections
    private val firestoreColleagues = ConcurrentHashMap<String, Colleague>()

    private val scannerManager = WorkplaceColleagueScannerManager(
        context = context,
        firestoreManager = firestoreManager,
        authManager = authManager,
        onColleagueDiscovered = { colleague ->
            bleColleagues[colleague.id] = colleague
            mergeColleagues()
        },
        onColleaguesUpdate = { updatedList ->
            updatedList.forEach { bleColleagues[it.id] = it }
            mergeColleagues()
        }
    )

    private var activeWorkplaceLat: Double = 0.0
    private var activeWorkplaceLng: Double = 0.0
    private var isMonitoring = false

    fun startWorkingModeMonitoring(
        workplaceLatitude: Double,
        workplaceLongitude: Double,
        onBluetoothDisabled: () -> Unit = {}
    ) {
        if (isMonitoring) return
        isMonitoring = true
        activeWorkplaceLat = workplaceLatitude
        activeWorkplaceLng = workplaceLongitude

        val currentUserId = authManager.currentUserId ?: "user"
        scope.launch {
            val userProfile = firestoreManager.getUserProfile(currentUserId).getOrNull()
            val userName = userProfile?.name?.ifBlank { "You" } ?: "You"

            // 1. Broadcast presence via BLE Beacon (mobile-only)
            beaconManager.startWorkplaceBeacon(
                userId = currentUserId,
                userName = userName,
                status = "WORKING",
                onBluetoothDisabled = onBluetoothDisabled
            )

            // 2. Start continuous scanning for nearby coworker beacons (Tier 1)
            scannerManager.startScanning()

            // 3. Query approved contacts in Firestore for Tier 3 remote location
            queryColleaguesFromFirestore(workplaceLatitude, workplaceLongitude)

            // 4. Start periodic 30s Geofence fallback check for colleagues whose BLE drops (Tier 2)
            geofenceManager.startPeriodicGeofenceFallback(
                workplaceLat = workplaceLatitude,
                workplaceLng = workplaceLongitude,
                getCandidates = { bleColleagues.values.toList() },
                onGeofenceVerified = { verified ->
                    geofenceColleagues[verified.id] = verified
                    mergeColleagues()
                }
            )

            mergeColleagues()
            Log.d(TAG, "Started Working Mode Colleague Monitoring at ($workplaceLatitude, $workplaceLongitude)")
        }
    }

    fun stopWorkingModeMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false
        beaconManager.stopWorkplaceBeacon()
        scannerManager.stopScanning()
        geofenceManager.stopPeriodicGeofenceFallback()
        bleColleagues.clear()
        geofenceColleagues.clear()
        firestoreColleagues.clear()
        _colleagues.value = emptyList()
        Log.d(TAG, "Stopped Working Mode Colleague Monitoring")
    }

    /**
     * Tier 3 fallback: Queries approved trusted contacts who have GPS updates within 5 minutes.
     */
    private suspend fun queryColleaguesFromFirestore(workplaceLat: Double, workplaceLng: Double) {
        val currentUserId = authManager.currentUserId ?: return
        try {
            val trustedContacts = firestoreManager.getApprovedTrustedContacts(currentUserId)
            val now = System.currentTimeMillis()
            val fiveMinutesMs = 5 * 60 * 1000L

            for (tc in trustedContacts) {
                val lastLoc = firestoreManager.getLastLocation(tc.authUserId)
                if (lastLoc != null) {
                    val age = now - lastLoc.timestamp
                    if (age <= fiveMinutesMs) {
                        val distance = geofenceManager.calculateDistance(
                            lastLoc.latitude,
                            lastLoc.longitude,
                            workplaceLat,
                            workplaceLng
                        )
                        if (distance <= WorkplaceGeofenceManager.DEFAULT_WORKPLACE_RADIUS_METERS) {
                            val colleague = Colleague(
                                id = tc.authUserId,
                                userId = tc.authUserId,
                                name = tc.name.ifBlank { "Colleague" },
                                workplaceName = "Designated Workplace",
                                lastSeen = lastLoc.timestamp,
                                latitude = lastLoc.latitude,
                                longitude = lastLoc.longitude,
                                isTrustedContact = true,
                                currentStatus = ColleagueStatus.AT_WORKPLACE,
                                phone = tc.phone,
                                distanceMeters = distance,
                                dataSource = "Firestore",
                                detectionMethod = "Location History (< 5m)",
                                confidence = 0.80f
                            )
                            firestoreColleagues[tc.authUserId] = colleague
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts from Firestore for Tier 3 fallback", e)
        }
    }

    /**
     * Merges all tiers, selecting the candidate with the HIGHEST confidence:
     * Tier 1 (BLE): confidence 0.95
     * Tier 3 (Firestore fresh < 5m): confidence 0.80
     * Tier 2 (Geofence fallback): confidence 0.70
     *
     * NO HARDCODED MOCKS. NO TEST DATA.
     */
    private fun mergeColleagues() {
        val allIds = (bleColleagues.keys + geofenceColleagues.keys + firestoreColleagues.keys).toSet()
        val resolvedList = mutableListOf<Colleague>()

        for (id in allIds) {
            val ble = bleColleagues[id]
            val firestore = firestoreColleagues[id]
            val geofence = geofenceColleagues[id]

            // Pick highest confidence match
            val candidates = listOfNotNull(ble, firestore, geofence)
            val best = candidates.maxByOrNull { it.confidence } ?: continue

            // Check if mutual safety buddy
            val isBuddy = safetyBuddyRepository.isPairedBuddy(id)
            resolvedList.add(best.copy(isSafetyBuddy = isBuddy))
        }

        // Sort: Active Safety Buddies first, then ascending by distance
        _colleagues.value = resolvedList.sortedWith(
            compareByDescending<Colleague> { it.isSafetyBuddy }
                .thenBy { it.distanceMeters ?: 9999f }
        )
    }
}
