package com.womensafety.system.viewmodel

import android.content.Context
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.ShakeSensitivity
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.service.ShakeDetectionService
import com.womensafety.system.utils.ShakeDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing shake detection service, sensitivity levels, and test mode.
 */
class ShakeDetectionViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) : ViewModel() {

    private val _isShakeActive = MutableStateFlow(ShakeDetectionService.isRunning)
    val isShakeActive: StateFlow<Boolean> = _isShakeActive.asStateFlow()

    private val _currentSensitivity = MutableStateFlow(ShakeSensitivity.MEDIUM)
    val currentSensitivity: StateFlow<ShakeSensitivity> = _currentSensitivity.asStateFlow()

    private val _isTestModeActive = MutableStateFlow(false)
    val isTestModeActive: StateFlow<Boolean> = _isTestModeActive.asStateFlow()

    private var testShakeDetector: ShakeDetector? = null

    init {
        loadUserPreferences()
    }

    fun loadUserPreferences() {
        val uid = authManager.currentUserId ?: return
        viewModelScope.launch {
            val profile = userRepository.getUserProfile(uid).getOrNull()
            if (profile != null) {
                _currentSensitivity.value = ShakeSensitivity.fromString(profile.shakeSensitivity)
                _isShakeActive.value = ShakeDetectionService.isRunning || profile.shakeDetectionEnabled
            }
        }
    }

    fun startShakeDetection(context: Context) {
        ShakeDetectionService.startService(context, _currentSensitivity.value)
        _isShakeActive.value = true
        persistShakePreferences(enabled = true, sensitivity = _currentSensitivity.value)
    }

    fun stopShakeDetection(context: Context) {
        ShakeDetectionService.stopService(context)
        _isShakeActive.value = false
        persistShakePreferences(enabled = false, sensitivity = _currentSensitivity.value)
    }

    fun updateSensitivity(context: Context, sensitivity: ShakeSensitivity) {
        _currentSensitivity.value = sensitivity
        if (ShakeDetectionService.isRunning) {
            ShakeDetectionService.updateSensitivity(context, sensitivity)
        }
        persistShakePreferences(enabled = _isShakeActive.value, sensitivity = sensitivity)
    }

    fun startTestMode(context: Context, onShakeDetected: () -> Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        testShakeDetector?.stop(sensorManager)

        testShakeDetector = ShakeDetector(sensitivity = _currentSensitivity.value) {
            onShakeDetected()
        }
        testShakeDetector?.start(sensorManager)
        _isTestModeActive.value = true
    }

    fun stopTestMode(context: Context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        testShakeDetector?.stop(sensorManager)
        testShakeDetector = null
        _isTestModeActive.value = false
    }

    private fun persistShakePreferences(enabled: Boolean, sensitivity: ShakeSensitivity) {
        val uid = authManager.currentUserId ?: return
        viewModelScope.launch {
            userRepository.updateUserProfile(
                uid,
                mapOf(
                    "shakeDetectionEnabled" to enabled,
                    "shakeSensitivity" to sensitivity.name
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        testShakeDetector = null
    }
}
