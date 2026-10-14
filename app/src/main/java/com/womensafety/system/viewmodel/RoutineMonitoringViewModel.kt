package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.model.*
import com.womensafety.system.data.repository.RoutineRepository
import com.womensafety.system.manager.RoutineAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

/**
 * ViewModel managing routine-based anomaly detection state, pattern inspection,
 * and user responses for REGULAR Safety Mode.
 */
class RoutineMonitoringViewModel(
    private val routineRepository: RoutineRepository = RoutineRepository(),
    private val routineAnalyzer: RoutineAnalyzer = RoutineAnalyzer
) : ViewModel() {

    private val _todayRoutine = MutableStateFlow<DailyRoutine?>(null)
    val todayRoutine: StateFlow<DailyRoutine?> = _todayRoutine.asStateFlow()

    private val _detectedAnomalies = MutableStateFlow<List<AnomalyDetected>>(emptyList())
    val detectedAnomalies: StateFlow<List<AnomalyDetected>> = _detectedAnomalies.asStateFlow()

    private val _nextExpectedLocation = MutableStateFlow<LocationPattern?>(null)
    val nextExpectedLocation: StateFlow<LocationPattern?> = _nextExpectedLocation.asStateFlow()

    val activeAlert: StateFlow<AnomalyDetected?> = routineAnalyzer.activeAnomaly

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            val today = LocalDate.now().dayOfWeek
            val routine = routineRepository.getRoutineForDay(today)
            _todayRoutine.value = routine

            // Calculate next expected location based on current hour/minute
            val cal = Calendar.getInstance()
            val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            val next = routine.typicalLocations
                .filter { (it.typicalTimeRange.startHour * 60 + it.typicalTimeRange.startMinute) > currentMinutes }
                .minByOrNull { it.typicalTimeRange.startHour * 60 + it.typicalTimeRange.startMinute }
                ?: routine.typicalLocations.firstOrNull()

            _nextExpectedLocation.value = next
            _detectedAnomalies.value = routineRepository.getRecentAnomalies(7)
            _isLoading.value = false
        }
    }

    fun respondToAnomaly(anomalyId: String, response: String) {
        viewModelScope.launch {
            routineAnalyzer.submitAnomalyResponse(anomalyId, response)
            // Reload anomalies
            _detectedAnomalies.value = routineRepository.getRecentAnomalies(7)
        }
    }

    fun dismissAlert() {
        routineAnalyzer.dismissActiveAnomaly()
    }

    fun simulateDemoAnomaly(severity: AnomalySeverity = AnomalySeverity.HIGH) {
        val simulated = routineAnalyzer.simulateDemoAnomaly(
            type = if (severity == AnomalySeverity.HIGH) AnomalyType.UNUSUAL_BOTH else AnomalyType.UNUSUAL_LOCATION,
            severity = severity
        )
        viewModelScope.launch {
            _detectedAnomalies.value = routineRepository.getRecentAnomalies(7)
        }
    }
}
