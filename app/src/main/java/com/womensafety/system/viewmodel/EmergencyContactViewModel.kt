package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.model.EmergencyContact
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val result = withTimeoutOrNull(15000L) {
                    userRepository.getEmergencyContacts()
                }
                if (result == null) {
                    _state.update {
                        it.copy(error = "Loading emergency contacts timed out.")
                    }
                    return@launch
                }
                if (result.isSuccess) {
                    val contacts = result.getOrNull() ?: emptyList()
                    com.womensafety.system.manager.SOSManager.updateCachedEmergencyContacts(contacts)
                    _state.update {
                        it.copy(
                            contacts = contacts,
                            error = null
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            error = result.exceptionOrNull()?.localizedMessage ?: "Failed to load emergency contacts."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.localizedMessage ?: "Failed to load emergency contacts.")
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
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
        if (_state.value.isLoading) return
        val trimmedName = name.trim()
        val normalizedPhone = Validators.normalizePhone(phone.trim())

        if (trimmedName.isBlank()) {
            _state.update { it.copy(error = "Please enter contact name.") }
            return
        }

        if (!Validators.isValidPhone(normalizedPhone)) {
            _state.update { it.copy(error = "Please enter a valid phone number.") }
            return
        }

        val contact = EmergencyContact(
            name = trimmedName,
            phone = normalizedPhone,
            isPrimary = isPrimary,
            enableEscalation = enableEscalation
        )

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, actionSuccess = null) }
            try {
                val result = withTimeoutOrNull(15000L) {
                    userRepository.addEmergencyContact(contact)
                }
                if (result == null) {
                    _state.update {
                        it.copy(error = "Adding contact timed out. Please retry.")
                    }
                    return@launch
                }
                if (result.isSuccess) {
                    _state.update {
                        it.copy(actionSuccess = "Contact added successfully.")
                    }
                    loadEmergencyContacts()
                    onSuccess()
                } else {
                    _state.update {
                        it.copy(
                            error = result.exceptionOrNull()?.localizedMessage ?: "Failed to add emergency contact."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.localizedMessage ?: "Failed to add emergency contact.")
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
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
        if (_state.value.isLoading) return
        val trimmedName = name.trim()
        val normalizedPhone = Validators.normalizePhone(phone.trim())

        if (trimmedName.isBlank()) {
            _state.update { it.copy(error = "Please enter contact name.") }
            return
        }

        if (!Validators.isValidPhone(normalizedPhone)) {
            _state.update { it.copy(error = "Please enter a valid phone number.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, actionSuccess = null) }
            try {
                val result = withTimeoutOrNull(15000L) {
                    userRepository.updateEmergencyContact(
                        contactId,
                        trimmedName,
                        normalizedPhone,
                        isPrimary,
                        enableEscalation
                    )
                }
                if (result == null) {
                    _state.update {
                        it.copy(error = "Updating contact timed out. Please retry.")
                    }
                    return@launch
                }
                if (result.isSuccess) {
                    _state.update {
                        it.copy(actionSuccess = "Contact updated successfully.")
                    }
                    loadEmergencyContacts()
                    onSuccess()
                } else {
                    _state.update {
                        it.copy(
                            error = result.exceptionOrNull()?.localizedMessage ?: "Failed to update emergency contact."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.localizedMessage ?: "Failed to update emergency contact.")
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteContact(contactId: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, actionSuccess = null) }
            try {
                val result = withTimeoutOrNull(15000L) {
                    userRepository.deleteEmergencyContact(contactId)
                }
                if (result == null) {
                    _state.update {
                        it.copy(error = "Deleting contact timed out. Please retry.")
                    }
                    return@launch
                }
                if (result.isSuccess) {
                    _state.update {
                        it.copy(actionSuccess = "Contact deleted.")
                    }
                    loadEmergencyContacts()
                    onSuccess()
                } else {
                    _state.update {
                        it.copy(
                            error = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete emergency contact."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.localizedMessage ?: "Failed to delete emergency contact.")
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
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
