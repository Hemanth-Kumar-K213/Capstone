package com.womensafety.system.manager

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.data.model.ColleagueStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * Requirement 2C: Geofence Fallback Manager.
 *
 * When BLE signal is lost for a colleague (> 15s):
 * 1. Reads workplace location from user profile / settings
 * 2. Fetches colleague's last known location from Firestore
 * 3. Calculates distance to workplace bounds (< 500m)
 * 4. Updates colleague status to "At Workplace (BLE lost)" with confidence 0.70
 * 5. Runs periodic checks every 30 seconds
 */
class WorkplaceGeofenceManager(
    private val context: Context,
    private val firestoreManager: FirestoreManager,
    private val authManager: FirebaseAuthManager
) {

    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var periodicJob: Job? = null

    companion object {
        private const val TAG = "WorkplaceGeofence"
        const val DEFAULT_WORKPLACE_RADIUS_METERS = 500f
        const val CHECK_INTERVAL_MS = 30_000L // 30 seconds
    }

    /**
     * Resolves workplace coordinates from userProfile/{userId}/workplaceLocation or userProfile/{userId}.
     */
    suspend fun getWorkplaceLocation(userId: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            // Check userProfile/{userId}/workplaceLocation
            val doc = db.collection("userProfile")
                .document(userId)
                .collection("workplaceLocation")
                .document("current")
                .get()
                .await()

            if (doc.exists()) {
                val lat = doc.getDouble("lat") ?: doc.getDouble("latitude")
                val lon = doc.getDouble("lon") ?: doc.getDouble("longitude")
                if (lat != null && lon != null) {
                    return@withContext Pair(lat, lon)
                }
            }

            // Fallback: Check userProfile/{userId} direct document
            val profileDoc = db.collection("users")
                .document(userId)
                .get()
                .await()

            val workplaceMap = profileDoc.get("workplaceLocation") as? Map<*, *>
            val lat = (workplaceMap?.get("lat") as? Number)?.toDouble() ?: (profileDoc.getDouble("workplaceLat"))
            val lon = (workplaceMap?.get("lon") as? Number)?.toDouble() ?: (profileDoc.getDouble("workplaceLon"))

            if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
                return@withContext Pair(lat, lon)
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching workplace location from Firestore: ${e.message}")
            null
        }
    }

    /**
     * Verifies if a specific colleague is inside the 500m workplace geofence using Firestore location.
     */
    suspend fun checkColleagueInGeofence(
        colleagueId: String,
        colleagueName: String,
        workplaceLat: Double,
        workplaceLng: Double
    ): Colleague? = withContext(Dispatchers.IO) {
        try {
            val lastLocation = firestoreManager.getLastLocation(colleagueId) ?: return@withContext null
            val now = System.currentTimeMillis()
            val ageMs = now - lastLocation.timestamp

            // If location is fresher than 30 minutes
            if (ageMs > 30 * 60 * 1000L) {
                return@withContext null
            }

            val distanceToWorkplace = calculateDistance(
                lastLocation.latitude,
                lastLocation.longitude,
                workplaceLat,
                workplaceLng
            )

            if (distanceToWorkplace <= DEFAULT_WORKPLACE_RADIUS_METERS) {
                Colleague(
                    id = colleagueId,
                    userId = colleagueId,
                    name = colleagueName,
                    currentStatus = ColleagueStatus.AT_WORKPLACE,
                    distanceMeters = distanceToWorkplace,
                    dataSource = "Geofence",
                    detectionMethod = "Geofence",
                    confidence = 0.70f,
                    lastSeen = lastLocation.timestamp,
                    latitude = lastLocation.latitude,
                    longitude = lastLocation.longitude,
                    isTrustedContact = true
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check geofence for colleague $colleagueId", e)
            null
        }
    }

    /**
     * Starts background fallback timer checking every 30 seconds for lost BLE contacts.
     */
    fun startPeriodicGeofenceFallback(
        workplaceLat: Double,
        workplaceLng: Double,
        getCandidates: () -> List<Colleague>,
        onGeofenceVerified: (Colleague) -> Unit
    ) {
        stopPeriodicGeofenceFallback()
        periodicJob = scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                try {
                    val candidates = getCandidates()
                    val now = System.currentTimeMillis()

                    for (candidate in candidates) {
                        // Check if BLE was lost (no BLE update in last 15 seconds)
                        val isBleLost = (now - candidate.lastSeen) > 15_000L
                        if (isBleLost || candidate.detectionMethod != "BLE") {
                            val verified = checkColleagueInGeofence(
                                candidate.id,
                                candidate.name,
                                workplaceLat,
                                workplaceLng
                            )
                            if (verified != null) {
                                onGeofenceVerified(verified)
                                Log.d(TAG, "Geofence fallback confirmed colleague: ${candidate.name} (${verified.distanceMeters?.toInt()}m)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error in periodic geofence loop", e)
                }
            }
        }
    }

    fun stopPeriodicGeofenceFallback() {
        periodicJob?.cancel()
        periodicJob = null
    }

    suspend fun isUserInsideWorkplace(
        workplaceLatitude: Double,
        workplaceLongitude: Double,
        radiusMeters: Float = DEFAULT_WORKPLACE_RADIUS_METERS
    ): Boolean {
        return try {
            val location = locationClient.lastLocation.await()
            if (location != null) {
                val distance = calculateDistance(
                    location.latitude,
                    location.longitude,
                    workplaceLatitude,
                    workplaceLongitude
                )
                distance <= radiusMeters
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking user workplace geofence", e)
            false
        }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
