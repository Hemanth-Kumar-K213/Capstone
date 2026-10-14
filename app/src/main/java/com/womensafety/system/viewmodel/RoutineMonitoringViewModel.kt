package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.*
import com.womensafety.system.data.repository.RoutineRepository
import com.womensafety.system.manager.RoutineAnalyzer
import com.womensafety.system.manager.RoutineAutoLearner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Calendar

/**
 * ViewModel managing routine-based anomaly detection state, real Firestore routine locations,
 * auto-learning lifecycle, and manual location entries for REGULAR Safety Mode.
 */
class RoutineMonitoringViewModel(
    private val routineRepository: RoutineRepository = RoutineRepository(),
    private val routineAnalyzer: RoutineAnalyzer = RoutineAnalyzer,
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) : ViewModel() {

    private val _todayLocations = MutableStateFlow<List<RoutineLocation>>(emptyList())
    val todayLocations: StateFlow<List<RoutineLocation>> = _todayLocations.asStateFlow()

    private val _todayRoutine = MutableStateFlow<DailyRoutine?>(null)
    val todayRoutine: StateFlow<DailyRoutine?> = _todayRoutine.asStateFlow()

    private val _detectedAnomalies = MutableStateFlow<List<AnomalyDetected>>(emptyList())
    val detectedAnomalies: StateFlow<List<AnomalyDetected>> = _detectedAnomalies.asStateFlow()

    private val _nextExpectedLocation = MutableStateFlow<RoutineLocation?>(null)
    val nextExpectedLocation: StateFlow<RoutineLocation?> = _nextExpectedLocation.asStateFlow()

    val autoLearnStatus: StateFlow<AutoLearnStatus> = RoutineAutoLearner.status
    val activeAlert: StateFlow<AnomalyDetected?> = routineAnalyzer.activeAnomaly

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadData()
        authManager.currentUserId?.let { uid ->
            RoutineAutoLearner.checkAndTriggerOnLaunch(uid)
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            val today = LocalDate.now().dayOfWeek

            // Query real Firestore locations for today from routines/{userId}/{dayOfWeek}
            val locations = routineRepository.getRoutineLocationsForDay(today)
            _todayLocations.value = locations

            val routine = routineRepository.getRoutineForDay(today)
            _todayRoutine.value = routine

            // Compute next expected location based on current hour/minute
            val cal = Calendar.getInstance()
            val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            val next = locations.mapNotNull { loc ->
                val parts = loc.typicalArrivalTime.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val total = h * 60 + m
                Pair(loc, total)
            }.filter { it.second > currentMinutes }
                .minByOrNull { it.second }
                ?.first ?: locations.firstOrNull()

            _nextExpectedLocation.value = next
            _detectedAnomalies.value = routineRepository.getRecentAnomalies(7)
            _isLoading.value = false
        }
    }

    fun saveLocation(days: List<DayOfWeek>, location: RoutineLocation) {
        viewModelScope.launch {
            _isLoading.value = true
            routineRepository.saveRoutineLocationForDays(days, location)
            loadData()
        }
    }

    fun deleteLocation(dayOfWeek: DayOfWeek, locationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            routineRepository.deleteRoutineLocation(dayOfWeek, locationId)
            loadData()
        }
    }

    fun refreshAutoLearn(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        RoutineAutoLearner.triggerManualRefresh { success, message ->
            loadData()
            onResult(success, message)
        }
    }

    fun respondToAnomaly(anomalyId: String, response: String) {
        viewModelScope.launch {
            routineAnalyzer.submitAnomalyResponse(anomalyId, response)
            _detectedAnomalies.value = routineRepository.getRecentAnomalies(7)
        }
    }

    fun dismissAlert() {
        routineAnalyzer.dismissActiveAnomaly()
    }
}
