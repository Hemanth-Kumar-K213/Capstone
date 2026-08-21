package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.model.EmergencyContact
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the 5-screen Initial Setup Wizard.
 * Accesses Firebase ONLY through [UserRepository].
 */
class SetupWizardViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    data class SetupWizardState(
        val emergencyContactsList: List<EmergencyContact> = emptyList(),
        val locationTrackingEnabled: Boolean = true,
        val shakeDetectionEnabled: Boolean = false,
        val shakeSensitivity: String = "MEDIUM", // LOW, MEDIUM, HIGH
        val sosAutoCall: Boolean = false,
        val grantedPermissions: Set<String> = emptySet(),
        val totalRequiredPermissionsCount: Int = 8,
        val isLoading: Boolean = false,
        val error: String? = null,
        val isCompleted: Boolean = false
    )

    private val _state = MutableStateFlow(SetupWizardState())
    val state: StateFlow<SetupWizardState> = _state.asStateFlow()

    fun updatePermissionStatus(permission: String, isGranted: Boolean) {
        val current = _state.value.grantedPermissions.toMutableSet()
        if (isGranted) {
            current.add(permission)
        } else {
            current.remove(permission)
        }
        _state.value = _state.value.copy(grantedPermissions = current)
    }

    fun addEmergencyContact(name: String, phone: String): Boolean {
        val trimmedName = name.trim()
        val normalizedPhone = Validators.normalizePhone(phone.trim())

        if (trimmedName.isBlank()) {
            _state.value = _state.value.copy(error = "Please enter contact name.")
            return false
        }

        if (!Validators.isValidPhone(normalizedPhone)) {
            _state.value = _state.value.copy(error = "Please enter a valid phone number.")
            return false
        }

        val newContact = EmergencyContact(
            name = trimmedName,
            phone = normalizedPhone
        )

        val updatedList = _state.value.emergencyContactsList + newContact
        _state.value = _state.value.copy(
            emergencyContactsList = updatedList,
            error = null
        )
        return true
    }

    fun removeEmergencyContact(id: String) {
        val updatedList = _state.value.emergencyContactsList.filterNot { it.id == id }
        _state.value = _state.value.copy(
            emergencyContactsList = updatedList,
            error = null
        )
    }

    fun updateLocationTracking(enabled: Boolean) {
        _state.value = _state.value.copy(locationTrackingEnabled = enabled)
    }

    fun updateShakeDetection(enabled: Boolean) {
        _state.value = _state.value.copy(shakeDetectionEnabled = enabled)
    }

    fun updateShakeSensitivity(sensitivity: String) {
        _state.value = _state.value.copy(shakeSensitivity = sensitivity)
    }

    fun updateSosAutoCall(enabled: Boolean) {
        _state.value = _state.value.copy(sosAutoCall = enabled)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun skipSetup(onComplete: () -> Unit) {
        onComplete()
    }

    fun completeSetup(onSuccess: () -> Unit) {
        val currentState = _state.value
        _state.value = currentState.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val preferences = mapOf(
                "locationTrackingEnabled" to currentState.locationTrackingEnabled,
                "shakeDetectionEnabled" to currentState.shakeDetectionEnabled,
                "shakeSensitivity" to currentState.shakeSensitivity,
                "sosAutoCall" to currentState.sosAutoCall,
                "emergencyContacts" to currentState.emergencyContactsList.map { it.toMap() }
            )

            val result = userRepository.updateSetupStatus(
                setupCompleted = true,
                preferences = preferences
            )

            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isCompleted = true
                )
                onSuccess()
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to save setup configuration."
                )
            }
        }
    }
}
