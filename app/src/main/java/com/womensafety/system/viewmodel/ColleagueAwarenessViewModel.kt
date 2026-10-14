package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.data.model.TransportSuggestion
import com.womensafety.system.manager.ColleagueAwarenessManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing workplace presence, safety buddy partnerships,
 * and late-stay transport coordination for WORKING Safety Mode.
 */
class ColleagueAwarenessViewModel(
    private val manager: ColleagueAwarenessManager = ColleagueAwarenessManager
) : ViewModel() {

    val colleaguesAtWork: StateFlow<List<Colleague>> = manager.colleagues
    val safetyBuddy: StateFlow<Colleague?> = manager.safetyBuddy
    val isLateStay: StateFlow<Boolean> = manager.isLateStayActive
    val transportOptions: StateFlow<List<TransportSuggestion>> = manager.transportOptions
    val bookingConfirmation: StateFlow<String?> = manager.bookingConfirmation

    init {
        manager.refreshData()
    }

    fun setSafetyBuddy(colleague: Colleague) {
        manager.setSafetyBuddy(colleague)
    }

    fun bookTransport(option: TransportSuggestion) {
        manager.bookTransport(option)
    }

    fun simulateLateStay(isLate: Boolean) {
        manager.simulateLateStay(isLate)
    }

    fun clearConfirmation() {
        manager.clearConfirmation()
    }

    fun refresh() {
        manager.refreshData()
    }
}
