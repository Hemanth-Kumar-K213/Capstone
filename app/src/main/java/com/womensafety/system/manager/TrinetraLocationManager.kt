package com.womensafety.system.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.util.GeoPoint
import com.womensafety.system.data.model.LocationServiceState
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.repository.LocationRepository
import com.womensafety.system.service.LocationTrackingService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Reusable coordinator for Location, Permissions, GPS availability, and OpenStreetMap utilities.
 * Centralizes duplicate location checking code, security exceptions, and geocoding operations.
 */
object TrinetraLocationManager {

    private const val TAG = "TrinetraLocationManager"
    private const val GPS_TIMEOUT_MS = 8000L
    private val locationRepository = LocationRepository()

    /**
     * Expose location and service status state flows from the foreground service.
     */
    val currentLocation: StateFlow<UserLocation?> = LocationTrackingService.currentLocation
    val serviceState: StateFlow<LocationServiceState> = LocationTrackingService.serviceState

    /**
     * Checks if fine/coarse location permissions are granted.
     */
    fun isLocationPermissionGranted(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    /**
     * Checks if background location permission is granted (Android Q+).
     */
    fun isBackgroundLocationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if GPS (hardware provider) is enabled.
     */
    fun isGpsEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check GPS provider status", e)
            false
        }
    }

    fun isPlayServicesAvailable(context: Context): Boolean {
        return true
    }

    /**
     * OpenStreetMap (OSMDroid) is 100% free and open-source, requiring zero API keys or billing.
     */
    fun isOsmAvailable(context: Context): Boolean {
        return true
    }

    /**
     * Compatibility helper preserved for existing callers.
     */
    fun isMapsApiKeyConfigured(context: Context): Boolean {
        return true
    }

    /**
     * Performs a reverse-geocoding lookup for given coordinates.
     */
    fun reverseGeocode(context: Context, lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.let { addr ->
                val street = addr.thoroughfare ?: addr.featureName ?: ""
                val locality = addr.locality ?: addr.subAdminArea ?: ""
                val admin = addr.adminArea ?: ""
                listOf(street, locality, admin).filter { it.isNotBlank() }.joinToString(", ")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed: ${e.message}")
            null
        }
    }

    /**
     * Resolves an address string to an OSMDroid GeoPoint location.
     */
    suspend fun getLocationFromAddress(context: Context, addressStr: String): GeoPoint? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (addressStr.isBlank()) return@withContext null
        return@withContext try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val completableDeferred = kotlinx.coroutines.CompletableDeferred<GeoPoint?>()
                geocoder.getFromLocationName(addressStr, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: List<Address>) {
                        val addr = addresses.firstOrNull()
                        if (addr != null) {
                            completableDeferred.complete(GeoPoint(addr.latitude, addr.longitude))
                        } else {
                            completableDeferred.complete(null)
                        }
                    }
                    override fun onError(errorMessage: String?) {
                        completableDeferred.complete(null)
                    }
                })
                completableDeferred.await()
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(addressStr, 1)
                val addr = addresses?.firstOrNull()
                if (addr != null) GeoPoint(addr.latitude, addr.longitude) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed for address: $addressStr", e)
            null
        }
    }

    /**
     * Acquires a high-accuracy current location fix immediately, safely wrapping permission
     * checks, security exceptions, play services availability, and timeouts.
     */
    @SuppressLint("MissingPermission")
    suspend fun getImmediateLocationFix(context: Context, userId: String): UserLocation? {
        if (!isLocationPermissionGranted(context)) {
            Log.w(TAG, "getImmediateLocationFix: Permission missing. Using last known from DB.")
            return locationRepository.getLatestLocation(userId).getOrNull()
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val freshLocation = withTimeoutOrNull(GPS_TIMEOUT_MS) {
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }

            if (freshLocation != null) {
                val addressStr = reverseGeocode(context, freshLocation.latitude, freshLocation.longitude)
                UserLocation(
                    userId = userId,
                    latitude = freshLocation.latitude,
                    longitude = freshLocation.longitude,
                    accuracy = freshLocation.accuracy,
                    timestamp = System.currentTimeMillis(),
                    speed = if (freshLocation.hasSpeed()) freshLocation.speed else null,
                    address = addressStr,
                    isSOSLocation = true
                )
            } else {
                Log.w(TAG, "fusedClient.getCurrentLocation returned null or timed out. Falling back.")
                locationRepository.getLatestLocation(userId).getOrNull()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Permission revoked during location query", e)
            locationRepository.getLatestLocation(userId).getOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error retrieving current location", e)
            locationRepository.getLatestLocation(userId).getOrNull()
        }
    }
}
