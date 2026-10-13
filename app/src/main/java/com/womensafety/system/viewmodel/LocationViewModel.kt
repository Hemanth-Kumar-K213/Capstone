package com.womensafety.system.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.LocationServiceState
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.repository.LocationRepository
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.service.LocationTrackingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing location tracking services, settings, and historical coordinates.
 */
class LocationViewModel(
    private val locationRepository: LocationRepository = LocationRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) : ViewModel() {

    val currentLocation: StateFlow<UserLocation?> = LocationTrackingService.currentLocation
    val serviceState: StateFlow<LocationServiceState> = LocationTrackingService.serviceState

    private val _locationHistory = MutableStateFlow<List<UserLocation>>(emptyList())
    val locationHistory: StateFlow<List<UserLocation>> = _locationHistory.asStateFlow()

    private val _updateIntervalMs = MutableStateFlow(LocationTrackingService.DEFAULT_NORMAL_INTERVAL_MS)
    val updateIntervalMs: StateFlow<Long> = _updateIntervalMs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val currentUserId: String?
        get() = authManager.currentUserId

    fun startLocationTracking(context: Context) {
        LocationTrackingService.startTracking(context, _updateIntervalMs.value)
        updateUserProfileLocationPreference(true)
    }

    fun pauseLocationTracking(context: Context) {
        LocationTrackingService.pauseTracking(context)
    }

    fun resumeLocationTracking(context: Context) {
        LocationTrackingService.resumeTracking(context)
    }

    fun stopLocationTracking(context: Context) {
        LocationTrackingService.stopTracking(context)
        updateUserProfileLocationPreference(false)
    }

    fun updateInterval(context: Context, intervalMs: Long) {
        _updateIntervalMs.value = intervalMs
        if (LocationTrackingService.isRunning) {
            val intent = android.content.Intent(context, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_UPDATE_INTERVAL
                putExtra(LocationTrackingService.EXTRA_INTERVAL_MS, intervalMs)
            }
            context.startService(intent)
        }
    }

    fun loadLocationHistory(userId: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    locationRepository.getLocationHistory(userId, limit = 50)
                } ?: Result.failure(Exception("Timed out loading location history"))

                result.onSuccess {
                    _locationHistory.value = it
                }.onFailure {
                    _errorMessage.value = it.message ?: "Failed to load location history"
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load location history"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun observeUserLocation(userId: String): Flow<UserLocation?> {
        return locationRepository.observeUserLocation(userId)
    }

    fun observeUidLocation(uid: String): Flow<UserLocation?> {
        return locationRepository.observeUidLocation(uid)
    }

    fun observeLocationByUserIdOrUid(identifier: String): Flow<UserLocation?> {
        return locationRepository.observeLocationByUserIdOrUid(identifier)
    }

    suspend fun getLatestLocation(identifier: String): UserLocation? {
        val uidLoc = locationRepository.getLatestUidLocation(identifier).getOrNull()
        if (uidLoc != null) return uidLoc
        return locationRepository.getLatestLocation(identifier).getOrNull()
    }

    fun clearHistory(userId: String, beforeTimestamp: Long) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    locationRepository.deleteOldLocations(userId, beforeTimestamp)
                } ?: Result.failure(Exception("Timed out deleting location history"))

                result.onSuccess {
                    loadLocationHistory(userId)
                }.onFailure {
                    _errorMessage.value = it.message ?: "Failed to delete location history"
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete location history"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateUserProfileLocationPreference(enabled: Boolean) {
        val uid = authManager.currentUserId ?: return
        viewModelScope.launch {
            userRepository.updateUserProfile(uid, mapOf("locationTrackingEnabled" to enabled))
        }
    }
}
