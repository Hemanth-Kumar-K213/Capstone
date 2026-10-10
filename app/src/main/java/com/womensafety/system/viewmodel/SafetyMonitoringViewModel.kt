package com.womensafety.system.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.womensafety.system.manager.SafetyMonitoringManager
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the proactive safety check screen ("Are You Safe?").
 * Exposes state flows from [SafetyMonitoringManager] directly.
 */
class SafetyMonitoringViewModel : ViewModel() {

    val isSafetyCheckActive: StateFlow<Boolean> = SafetyMonitoringManager.isSafetyCheckActive
    val riskScore: StateFlow<Int> = SafetyMonitoringManager.riskScore
    val activeAnomalies: StateFlow<List<String>> = SafetyMonitoringManager.activeAnomalies
    val countdownSeconds: StateFlow<Int> = SafetyMonitoringManager.countdownSeconds

    fun markAsSafe(context: Context) {
        SafetyMonitoringManager.resolveSafetyCheck(context, safe = true)
    }

    fun triggerSOS(context: Context) {
        SafetyMonitoringManager.resolveSafetyCheck(context, safe = false)
    }
}
