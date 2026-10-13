package com.womensafety.system.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.data.model.Relationship
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.data.repository.RelationshipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing permission requests and trusted contact relationships.
 * Accesses Firebase ONLY through repositories.
 *
 * IMPORTANT: Contact names are read from the denormalized fields in the Relationship
 * document (userName, trustedContactName) — NOT from cross-collection profile reads,
 * which Firestore security rules block.
 */
class PermissionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val relationshipRepository: RelationshipRepository = RelationshipRepository()
) : ViewModel() {

    data class PermissionListState(
        val relationships: List<Relationship> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val actionSuccess: String? = null
    )

    private val _state = MutableStateFlow(PermissionListState())
    val state: StateFlow<PermissionListState> = _state.asStateFlow()

    private var observeJob: kotlinx.coroutines.Job? = null
    private val deletedRelationshipIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        authRepository.currentUserId?.let { userId ->
            startObservingRelationships(userId)
        }
    }

    fun startObservingRelationships(userId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            relationshipRepository.observeRelationshipsForUser(userId).collect { relationships ->
                val activeList = relationships.filterNot { deletedRelationshipIds.contains(it.id) }
                Log.d("PermissionVM", "Real-time snapshot: ${activeList.size} relationships for user $userId")
                _state.value = _state.value.copy(
                    relationships = activeList,
                    isLoading = false
                )
            }
        }
    }

    fun loadRelationships() {
        val userId = authRepository.currentUserId
        if (userId == null) {
            _state.value = _state.value.copy(isLoading = false)
            return
        }
        startObservingRelationships(userId)

        viewModelScope.launch {
            if (_state.value.relationships.isEmpty()) {
                _state.value = _state.value.copy(isLoading = true)
            }

            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    relationshipRepository.getRelationshipsForUser(userId)
                } ?: Result.failure(Exception("Timed out loading relationships"))

                if (result.isSuccess) {
                    val relationships = result.getOrNull() ?: emptyList()
                    val filtered = relationships.filterNot { deletedRelationshipIds.contains(it.id) }
                    Log.d("PermissionVM", "Loaded ${filtered.size} relationships for user $userId")
                    _state.value = _state.value.copy(
                        relationships = filtered,
                        isLoading = false
                    )
                } else {
                    Log.e("PermissionVM", "Failed to load relationships: ${result.exceptionOrNull()?.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to load contacts."
                    )
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load contacts."
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun approveRequest(relationshipId: String) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, actionSuccess = null, error = null)
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    relationshipRepository.approveRelationship(relationshipId)
                } ?: Result.failure(Exception("Operation timed out"))

                if (result.isSuccess) {
                    _state.value = _state.value.copy(actionSuccess = "Request approved.")
                    loadRelationships()
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to approve request."
                    )
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to approve request."
                )
            }
        }
    }

    fun denyRequest(relationshipId: String) {
        deletedRelationshipIds.add(relationshipId)
        _state.value = _state.value.copy(
            relationships = _state.value.relationships.filterNot { it.id == relationshipId },
            actionSuccess = null,
            error = null
        )
        viewModelScope.launch {
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    relationshipRepository.denyRelationship(relationshipId)
                } ?: Result.failure(Exception("Operation timed out"))

                if (result.isSuccess) {
                    _state.value = _state.value.copy(actionSuccess = "Request denied.")
                    loadRelationships()
                } else {
                    Log.e("PermissionVM", "denyRequest error: ${result.exceptionOrNull()?.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to deny request."
                    )
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to deny request."
                )
            }
        }
    }

    fun revokeAccess(relationshipId: String) {
        deletedRelationshipIds.add(relationshipId)
        _state.value = _state.value.copy(
            relationships = _state.value.relationships.filterNot { it.id == relationshipId },
            actionSuccess = null,
            error = null
        )
        viewModelScope.launch {
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    relationshipRepository.revokeRelationship(relationshipId)
                } ?: Result.failure(Exception("Operation timed out"))

                if (result.isSuccess) {
                    _state.value = _state.value.copy(actionSuccess = "Access revoked.")
                    loadRelationships()
                } else {
                    Log.e("PermissionVM", "revokeAccess error: ${result.exceptionOrNull()?.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to revoke access."
                    )
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to revoke access."
                )
            }
        }
    }

    fun deleteRelationship(relationshipId: String) {
        deletedRelationshipIds.add(relationshipId)
        _state.value = _state.value.copy(
            relationships = _state.value.relationships.filterNot { it.id == relationshipId },
            actionSuccess = null,
            error = null
        )
        viewModelScope.launch {
            try {
                val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    relationshipRepository.deleteRelationship(relationshipId)
                } ?: Result.failure(Exception("Operation timed out"))

                if (result.isSuccess) {
                    _state.value = _state.value.copy(actionSuccess = "Contact removed.")
                    loadRelationships()
                } else {
                    Log.e("PermissionVM", "deleteRelationship error: ${result.exceptionOrNull()?.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to remove contact."
                    )
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to remove contact."
                )
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, actionSuccess = null)
    }

    /**
     * Returns the count of pending requests.
     */
    fun getPendingCount(): Int {
        return _state.value.relationships.count {
            it.permissionState == PermissionState.PENDING
        }
    }

    /**
     * Returns the count of approved contacts.
     */
    fun getApprovedCount(): Int {
        return _state.value.relationships.count {
            it.permissionState == PermissionState.APPROVED
        }
    }
}
