package com.womensafety.system.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.data.model.SOSTriggerMethod
import com.womensafety.system.data.repository.SOSRepository
import com.womensafety.system.manager.SOSManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing active SOS state, 5-minute auto-resolve countdown, and incident history.
 */
class SOSViewModel(
    private val sosRepository: SOSRepository = SOSRepository(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) : ViewModel() {

    val activeSOSEvent: StateFlow<SOSEvent?> = SOSManager.activeEvent

    private val _sosCountdownSeconds = MutableStateFlow(300) // 5 minutes
    val sosCountdownSeconds: StateFlow<Int> = _sosCountdownSeconds.asStateFlow()

    private val _sosHistory = MutableStateFlow<List<SOSEvent>>(emptyList())
    val sosHistory: StateFlow<List<SOSEvent>> = _sosHistory.asStateFlow()

    private val _selectedDetail = MutableStateFlow<SOSEvent?>(null)
    val selectedDetail: StateFlow<SOSEvent?> = _selectedDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var countdownJob: Job? = null

    init {
        checkActiveSOS()
    }

    fun checkActiveSOS() {
        val uid = authManager.currentUserId ?: return
        viewModelScope.launch {
            val active = sosRepository.getActiveSOSEvent(uid).getOrNull()
            if (active != null) {
                startCountdown()
            }
        }
    }

    fun triggerSOS(context: Context, method: SOSTriggerMethod, onTriggered: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val event = SOSManager.triggerSOS(context, method)
                if (event != null) {
                    startCountdown()
                    onTriggered?.invoke(event.id)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to trigger SOS alert"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun triggerDemoSOS(context: Context, onComplete: ((callMade: Boolean, smsSent: Int) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = SOSManager.triggerDemoSOS(context)
                onComplete?.invoke(res.first, res.second)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed demo execution"
                onComplete?.invoke(false, 0)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retrySmsDispatch(context: Context) {
        val event = activeSOSEvent.value ?: return
        viewModelScope.launch {
            try {
                SOSManager.redispatchSMS(context, event)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to redispatch SMS"
            }
        }
    }

    fun markAsSafe(context: Context, eventId: String, onResolved: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                stopCountdown()
                SOSManager.resolveSOS(context, eventId, SOSStatus.RESOLVED)
                onResolved?.invoke()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to resolve SOS"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAsFalseAlarm(context: Context, eventId: String, onCancelled: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                stopCountdown()
                SOSManager.resolveSOS(context, eventId, SOSStatus.FALSE_ALARM)
                onCancelled?.invoke()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to cancel false alarm"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        _sosCountdownSeconds.value = 300
        countdownJob = viewModelScope.launch {
            while (_sosCountdownSeconds.value > 0) {
                delay(1000)
                _sosCountdownSeconds.value -= 1
            }
            // Auto-resolve when countdown reaches 0
            activeSOSEvent.value?.let { event ->
                sosRepository.updateSOSEventStatus(event.id, SOSStatus.AUTO_RESOLVED)
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _sosCountdownSeconds.value = 300
    }

    fun loadSOSHistory() {
        val uid = authManager.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = sosRepository.getSOSHistory(uid, limit = 50)
            result.onSuccess {
                _sosHistory.value = it
            }.onFailure {
                _errorMessage.value = it.message ?: "Failed to load SOS history"
            }
            _isLoading.value = false
        }
    }

    fun loadSOSDetail(eventId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val event = _sosHistory.value.find { it.id == eventId }
            _selectedDetail.value = event
            _isLoading.value = false
        }
    }

    fun observeActiveSOS(userId: String): Flow<SOSEvent?> {
        return sosRepository.observeActiveSOSEvent(userId)
    }

    fun formatCountdown(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
