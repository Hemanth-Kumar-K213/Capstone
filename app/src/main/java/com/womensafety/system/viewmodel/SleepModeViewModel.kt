package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import com.womensafety.system.data.model.CriticalAlertData
import com.womensafety.system.data.model.FallDetection
import com.womensafety.system.data.model.SleepQuality
import com.womensafety.system.data.model.SleepSession
import com.womensafety.system.manager.EmergencyOverrideManager
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel managing sleep sessions, critical emergency overrides, and fall alerts for SLEEPING Safety Mode.
 */
class SleepModeViewModel(
    private val manager: EmergencyOverrideManager = EmergencyOverrideManager
) : ViewModel() {

    val isSleeping: StateFlow<Boolean> = manager.isSleeping
    val currentSession: StateFlow<SleepSession?> = manager.currentSession
    val lastSleepQuality: StateFlow<SleepQuality?> = manager.lastSleepQuality
    val fallAlert: StateFlow<FallDetection?> = manager.fallAlert
    val fallCountdown: StateFlow<Int?> = manager.fallCountdown
    val criticalAlert: StateFlow<CriticalAlertData?> = manager.criticalAlert

    fun startSleepMode() {
        manager.initiateSleepMode()
    }

    fun endSleepMode() {
        manager.exitSleepMode()
    }

    fun confirmFallSafe() {
        manager.confirmFallSafe()
    }

    fun dismissCriticalAlert() {
        manager.dismissCriticalAlert()
    }

    fun simulateConsecutiveCalls(callerName: String = "Mom (Primary Contact)") {
        manager.simulateConsecutiveCalls(callerName)
    }

    fun simulateFallEvent() {
        manager.simulateFallEvent()
    }
}
