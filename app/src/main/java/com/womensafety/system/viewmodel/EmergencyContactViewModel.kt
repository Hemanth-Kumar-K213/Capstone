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
 * ViewModel for managing Emergency Contacts after initial setup.
 * Accesses Firebase ONLY through [UserRepository].
 */
class EmergencyContactViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    data class EmergencyContactsState(
        val contacts: List<EmergencyContact> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val actionSuccess: String? = null
    )

    private val _state = MutableStateFlow(EmergencyContactsState())
    val state: StateFlow<EmergencyContactsState> = _state.asStateFlow()

    init {
        loadEmergencyContacts()
    }

    fun loadEmergencyContacts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = userRepository.getEmergencyContacts()
            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    contacts = result.getOrNull() ?: emptyList(),
                    isLoading = false
                )
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load emergency contacts."
                )
            }
        }
    }

    fun addContact(
        name: String,
        phone: String,
        isPrimary: Boolean,
        enableEscalation: Boolean,
        onSuccess: () -> Unit
    ) {
        val trimmedName = name.trim()
        val normalizedPhone = Validators.normalizePhone(phone.trim())

        if (trimmedName.isBlank()) {
            _state.value = _state.value.copy(error = "Please enter contact name.")
            return
        }

        if (!Validators.isValidPhone(normalizedPhone)) {
            _state.value = _state.value.copy(error = "Please enter a valid phone number.")
            return
        }

        val contact = EmergencyContact(
            name = trimmedName,
            phone = normalizedPhone,
            isPrimary = isPrimary,
            enableEscalation = enableEscalation
        )

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, actionSuccess = null)
            val result = userRepository.addEmergencyContact(contact)
            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    actionSuccess = "Contact added successfully."
                )
                loadEmergencyContacts()
                onSuccess()
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to add emergency contact."
                )
            }
        }
    }

    fun updateContact(
        contactId: String,
        name: String,
        phone: String,
        isPrimary: Boolean,
        enableEscalation: Boolean,
        onSuccess: () -> Unit
    ) {
        val trimmedName = name.trim()
        val normalizedPhone = Validators.normalizePhone(phone.trim())

        if (trimmedName.isBlank()) {
            _state.value = _state.value.copy(error = "Please enter contact name.")
            return
        }

        if (!Validators.isValidPhone(normalizedPhone)) {
            _state.value = _state.value.copy(error = "Please enter a valid phone number.")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, actionSuccess = null)
            val result = userRepository.updateEmergencyContact(
                contactId,
                trimmedName,
                normalizedPhone,
                isPrimary,
                enableEscalation
            )
            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    actionSuccess = "Contact updated successfully."
                )
                loadEmergencyContacts()
                onSuccess()
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to update emergency contact."
                )
            }
        }
    }

    fun deleteContact(contactId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, actionSuccess = null)
            val result = userRepository.deleteEmergencyContact(contactId)
            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    actionSuccess = "Contact deleted."
                )
                loadEmergencyContacts()
                onSuccess()
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to delete emergency contact."
                )
            }
        }
    }

    fun getContactById(contactId: String): EmergencyContact? {
        return _state.value.contacts.find { it.id == contactId }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, actionSuccess = null)
    }
}
