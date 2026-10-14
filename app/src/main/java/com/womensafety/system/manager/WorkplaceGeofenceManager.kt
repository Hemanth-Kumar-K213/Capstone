package com.womensafety.system.manager

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import kotlinx.coroutines.tasks.await

/**
 * Manages workplace geofencing presence verification as a fallback when BLE is out of range.
 */
class WorkplaceGeofenceManager(
    private val context: Context,
    private val firestoreManager: FirestoreManager,
    private val authManager: FirebaseAuthManager
) {

    private val locationClient = LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "WorkplaceGeofence"
        const val DEFAULT_WORKPLACE_RADIUS_METERS = 500f
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
                val inside = distance <= radiusMeters
                Log.d(TAG, "User distance to workplace: ${distance}m (Inside: $inside)")
                inside
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
