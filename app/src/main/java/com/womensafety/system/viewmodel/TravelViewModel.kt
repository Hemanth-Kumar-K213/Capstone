package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.osmdroid.util.GeoPoint
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.JourneyRecord
import com.womensafety.system.data.repository.JourneyRepository
import com.womensafety.system.manager.JourneyState
import com.womensafety.system.manager.TravelModeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel governing Travel Mode journeys and history list using OpenStreetMap GeoPoints.
 */
class TravelViewModel(
    private val journeyRepository: JourneyRepository = JourneyRepository()
) : ViewModel() {

    private val authManager = FirebaseAuthManager()

    val journeyState: StateFlow<JourneyState> = TravelModeManager.journeyState
    val expectedRoute: StateFlow<List<GeoPoint>> = TravelModeManager.expectedRoute
    val currentLocation: StateFlow<GeoPoint?> = TravelModeManager.currentLocation
    val destinationLocation: StateFlow<GeoPoint?> = TravelModeManager.destinationLocation
    val currentDeviationDistance: StateFlow<Double> = TravelModeManager.currentDeviationDistance
    val speedKmh: StateFlow<Float> = TravelModeManager.speedKmh
    val stopCount: StateFlow<Int> = TravelModeManager.stopCount
    val durationSeconds: StateFlow<Long> = TravelModeManager.durationSeconds
    val isDeviationPromptActive: StateFlow<Boolean> = TravelModeManager.isDeviationPromptActive
    val deviationCountdownSeconds: StateFlow<Int> = TravelModeManager.deviationCountdownSeconds

    private val _pastJourneys = MutableStateFlow<List<JourneyRecord>>(emptyList())
    val pastJourneys: StateFlow<List<JourneyRecord>> = _pastJourneys.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Source location details
    private val _sourceLatLng = MutableStateFlow<GeoPoint?>(null)
    val sourceLatLng: StateFlow<GeoPoint?> = _sourceLatLng.asStateFlow()

    private val _sourceAddressName = MutableStateFlow("")
    val sourceAddressName: StateFlow<String> = _sourceAddressName.asStateFlow()

    // Destination location details
    private val _destinationLatLng = MutableStateFlow<GeoPoint?>(null)
    val destinationLatLng: StateFlow<GeoPoint?> = _destinationLatLng.asStateFlow()

    private val _destinationAddressName = MutableStateFlow("")
    val destinationAddressName: StateFlow<String> = _destinationAddressName.asStateFlow()

    private val _geocodeError = MutableStateFlow<String?>(null)
    val geocodeError: StateFlow<String?> = _geocodeError.asStateFlow()

    private val _isGeocoding = MutableStateFlow(false)
    val isGeocoding: StateFlow<Boolean> = _isGeocoding.asStateFlow()

    fun updateSourceLatLng(latLng: GeoPoint, address: String) {
        _sourceLatLng.value = latLng
        _sourceAddressName.value = address
    }

    fun updateDestinationLatLng(latLng: GeoPoint, address: String) {
        _destinationLatLng.value = latLng
        _destinationAddressName.value = address
    }

    fun geocodeAddressString(context: android.content.Context, query: String, isSource: Boolean) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isGeocoding.value = true
            _geocodeError.value = null
            try {
                val resolved = com.womensafety.system.manager.TrinetraLocationManager.getLocationFromAddress(context, query)
                if (resolved != null) {
                    if (isSource) {
                        _sourceLatLng.value = resolved
                        _sourceAddressName.value = query
                    } else {
                        _destinationLatLng.value = resolved
                        _destinationAddressName.value = query
                    }
                } else {
                    _geocodeError.value = "Could not resolve address \"$query\""
                }
            } catch (e: java.lang.Exception) {
                _geocodeError.value = "Error: ${e.localizedMessage}"
            } finally {
                _isGeocoding.value = false
            }
        }
    }

    init {
        loadHistory()
    }

    fun startJourney(start: GeoPoint, dest: GeoPoint) {
        TravelModeManager.startJourney(start, dest)
    }

    fun pauseJourney() {
        TravelModeManager.pauseJourney()
    }

    fun resumeJourney() {
        TravelModeManager.resumeJourney()
    }

    fun completeJourney() {
        TravelModeManager.completeJourney()
        loadHistory()
    }

    fun cancelJourney() {
        TravelModeManager.cancelJourney()
    }

    fun confirmSafety() {
        TravelModeManager.confirmSafetyAndRecalculate()
    }

    fun ignoreDeviationPrompt() {
        TravelModeManager.ignoreDeviationPrompt()
    }

    fun loadHistory() {
        val userId = authManager.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = journeyRepository.getUserJourneys(userId)
            if (result.isSuccess) {
                _pastJourneys.value = result.getOrDefault(emptyList())
            }
            _isLoading.value = false
        }
    }
}
