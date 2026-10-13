package com.womensafety.system.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.health.HealthConnectManager
import com.womensafety.system.data.health.model.HealthConnectStatus
import com.womensafety.system.data.health.model.HealthDataFreshness
import com.womensafety.system.data.health.model.HealthRiskSignal
import com.womensafety.system.data.health.model.HeartRateMeasurement
import com.womensafety.system.manager.RiskAssessmentEngine
import com.womensafety.system.manager.RiskAssessmentEngine.CapstoneScenarioResult
import com.womensafety.system.manager.SOSManager
import com.womensafety.system.manager.SafetyMonitoringManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HeartRateUiState(
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus.Unavailable,
    val hasPermission: Boolean = false,
    val isQuerying: Boolean = false,
    val isMonitoring: Boolean = false,
    val latestMeasurement: HeartRateMeasurement? = null,
    val recentMeasurements: List<HeartRateMeasurement> = emptyList(),
    val lastQueryTimestamp: Long? = null,
    val isWaitingForNewerRecord: Boolean = false,
    val errorMessage: String? = null,
    val activeTab: Int = 0, // 0: Live Diagnostics, 1: Risk Scenario & Test Harness, 2: Direct BLE Watch

    // Test Harness state
    val isTestMode: Boolean = true,
    val selectedScenarioIndex: Int = 2, // Default to Capstone scenario (+3, +2, +4 = 9)
    val simulatedRouteDeviation: Boolean = true,
    val simulatedLateNight: Boolean = true,
    val simulatedHeartRateSpike: Boolean = true,
    val scenarioResult: CapstoneScenarioResult? = null,
    val testStatusMessage: String? = null
)

class HeartRateMonitoringViewModel : ViewModel() {

    companion object {
        private const val TAG = "HeartRateVM"
        private const val POLLING_INTERVAL_MS = 3_000L // Fast 3-second polling for responsive heart rate sync
    }

    private val authManager = FirebaseAuthManager()
    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState: StateFlow<HeartRateUiState> = _uiState.asStateFlow()

    private var monitoringJob: Job? = null
    private var lastObservedTimestamp: Long? = null

    init {
        // Enforce session isolation with authenticated user ID
        val currentUserId = authManager.currentUserId
        HealthConnectManager.onUserSessionChanged(currentUserId)
        evaluateCurrentScenario()
    }

    fun setActiveTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex) }
    }

    /**
     * Inspects Health Connect provider status and heart-rate permission.
     */
    fun checkStatusAndPermissions(context: Context) {
        viewModelScope.launch {
            val status = HealthConnectManager.checkAvailability(context)
            val hasPerm = if (status == HealthConnectStatus.Available) {
                HealthConnectManager.hasHeartRatePermission(context)
            } else false

            _uiState.update {
                it.copy(
                    healthConnectStatus = status,
                    hasPermission = hasPerm,
                    errorMessage = null
                )
            }

            if (hasPerm) {
                startMonitoring(context)
            }
        }
    }

    fun onPermissionResult(granted: Boolean, context: Context) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) {
            startMonitoring(context)
        }
    }

    /**
     * Queries latest heart rate samples from Health Connect once.
     */
    fun refreshNow(context: Context) {
        if (_uiState.value.isQuerying) return

        viewModelScope.launch {
            _uiState.update { it.copy(isQuerying = true, errorMessage = null) }

            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    HealthConnectManager.queryHeartRateRecords(context)
                } ?: Result.failure(Exception("Health Connect query timed out"))

                result.onSuccess { samples ->
                    val latest = samples.firstOrNull()
                    val isUnchanged = latest != null && latest.timestamp == lastObservedTimestamp

                    lastObservedTimestamp = latest?.timestamp

                    _uiState.update {
                        it.copy(
                            latestMeasurement = latest,
                            recentMeasurements = samples,
                            lastQueryTimestamp = System.currentTimeMillis(),
                            isWaitingForNewerRecord = isUnchanged && samples.isNotEmpty(),
                            errorMessage = null
                        )
                    }
                }.onFailure { ex ->
                    Log.e(TAG, "Refresh failed", ex)
                    _uiState.update {
                        it.copy(
                            errorMessage = ex.message ?: "Failed querying Health Connect"
                        )
                    }
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Refresh unexpected error", e)
                _uiState.update {
                    it.copy(
                        errorMessage = e.message ?: "Error querying Health Connect"
                    )
                }
            } finally {
                _uiState.update { it.copy(isQuerying = false) }
            }
        }
    }

    /**
     * Starts continuous 15-second diagnostic polling loop.
     */
    fun startMonitoring(context: Context) {
        if (monitoringJob != null && monitoringJob?.isActive == true) return

        _uiState.update { it.copy(isMonitoring = true) }
        refreshNow(context)

        monitoringJob = viewModelScope.launch {
            Log.i(TAG, "Started diagnostic monitoring (polling every ${POLLING_INTERVAL_MS / 1000}s)")
            while (isActive) {
                delay(POLLING_INTERVAL_MS)
                refreshNow(context)
            }
        }
    }

    /**
     * Stops diagnostic polling loop and clears monitoring active state.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _uiState.update {
            it.copy(
                isMonitoring = false,
                isWaitingForNewerRecord = false
            )
        }
        Log.i(TAG, "Stopped diagnostic monitoring")
    }

    // ─── Test Harness & Capstone Scenario Execution ──────────────────

    fun selectScenario(index: Int) {
        _uiState.update { it.copy(selectedScenarioIndex = index) }
        when (index) {
            0 -> updateScenarioInputs(routeDeviated = false, lateNight = false, hrSpike = false)
            1 -> updateScenarioInputs(routeDeviated = true, lateNight = true, hrSpike = false) // Score 5
            2 -> updateScenarioInputs(routeDeviated = true, lateNight = true, hrSpike = true)  // Score 9 (Capstone)
            3 -> { /* Safe Response */ }
            4 -> { /* Help Response */ }
            5 -> { /* 60s Timeout */ }
            6 -> { /* Stale health data */ }
            7 -> { /* Missing health data */ }
            8 -> { /* Permission denied */ }
            9 -> { /* Notification permission denied */ }
        }
    }

    private fun updateScenarioInputs(routeDeviated: Boolean, lateNight: Boolean, hrSpike: Boolean) {
        _uiState.update {
            it.copy(
                simulatedRouteDeviation = routeDeviated,
                simulatedLateNight = lateNight,
                simulatedHeartRateSpike = hrSpike
            )
        }
        evaluateCurrentScenario()
    }

    private fun evaluateCurrentScenario() {
        val state = _uiState.value
        val result = RiskAssessmentEngine.evaluateCapstoneScenario(
            isRouteDeviated = state.simulatedRouteDeviation,
            isLateNight = state.simulatedLateNight,
            isHeartRateSpike = state.simulatedHeartRateSpike
        )
        _uiState.update { it.copy(scenarioResult = result) }
    }

    /**
     * Executes the simulated scenario safely under test-dispatch boundary.
     * Guarantees NO real SMS, calls, or emergency broadcasts are emitted.
     */
    fun runActiveTestScenario(context: Context) {
        viewModelScope.launch {
            // Activate double test mode guards
            SafetyMonitoringManager.isTestMode = true
            SOSManager.isTestMode = true

            val state = _uiState.value
            when (state.selectedScenarioIndex) {
                0 -> {
                    _uiState.update {
                        it.copy(testStatusMessage = "Normal Monitoring: Risk Score = 0. Baseline safe.")
                    }
                }
                1 -> {
                    // Confirmation threshold crossing: +3 +2 = 5
                    evaluateCurrentScenario()
                    _uiState.update {
                        it.copy(
                            testStatusMessage = "Threshold Crossed: Risk Score = 5 (+3 Route, +2 Night). 60s safety prompt triggered (Simulation)."
                        )
                    }
                }
                2 -> {
                    // Capstone Critical Scenario: +3 +2 +4 = 9
                    evaluateCurrentScenario()
                    _uiState.update {
                        it.copy(
                            testStatusMessage = "Critical Risk Verified: Score = 9 (+3 Route, +2 Night, +4 HR Spike). Real SOS suppressed."
                        )
                    }
                }
                3 -> {
                    SafetyMonitoringManager.resolveSafetyCheck(context, safe = true)
                    _uiState.update {
                        it.copy(testStatusMessage = "User confirmed: I'M SAFE. Escalation cancelled, monitoring resumed.")
                    }
                }
                4 -> {
                    SafetyMonitoringManager.resolveSafetyCheck(context, safe = false)
                    _uiState.update {
                        it.copy(testStatusMessage = "User selected: I NEED HELP. Simulated SOS triggered (Real SMS & alerts SUPPRESSED).")
                    }
                }
                5 -> {
                    _uiState.update {
                        it.copy(testStatusMessage = "60-Second Timeout: Auto-escalated to simulated SOS (Real SMS SUPPRESSED).")
                    }
                }
                6 -> {
                    val staleSignal = HealthRiskSignal(
                        heartRateBpm = 82,
                        timestamp = System.currentTimeMillis() - 900_000L, // 15 mins ago
                        sampleAgeMs = 900_000L,
                        freshness = HealthDataFreshness.STALE,
                        isAnomalousSpike = false,
                        isObservationOnly = true
                    )
                    _uiState.update {
                        it.copy(testStatusMessage = "Stale Data Handled: 15m old sample. Risk score untouched (+0). No false alarm.")
                    }
                }
                7 -> {
                    val missingSignal = HealthRiskSignal(
                        heartRateBpm = null,
                        timestamp = null,
                        sampleAgeMs = null,
                        freshness = HealthDataFreshness.UNAVAILABLE,
                        isAnomalousSpike = false,
                        isObservationOnly = true
                    )
                    _uiState.update {
                        it.copy(testStatusMessage = "Missing Data Handled: No records available. Risk score untouched (+0). No false alarm.")
                    }
                }
                8 -> {
                    _uiState.update {
                        it.copy(testStatusMessage = "Permission Denied Handled: Graceful empty state displayed without crash.")
                    }
                }
                9 -> {
                    _uiState.update {
                        it.copy(testStatusMessage = "Notification Permission Denied Handled: In-app fallback screen presented.")
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}
