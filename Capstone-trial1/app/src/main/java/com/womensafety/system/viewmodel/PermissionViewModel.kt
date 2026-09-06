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

    fun loadRelationships() {
        val userId = authRepository.currentUserId ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val result = relationshipRepository.getRelationshipsForUser(userId)
            if (result.isSuccess) {
                val relationships = result.getOrNull() ?: emptyList()
                Log.d("PermissionVM", "Loaded ${relationships.size} relationships for user $userId")
                relationships.forEach { rel ->
                    Log.d("PermissionVM", "  Relationship: ${rel.id}, status=${rel.status}, tcName=${rel.trustedContactName}")
                }
                _state.value = PermissionListState(
                    relationships = relationships,
                    isLoading = false
                )
            } else {
                Log.e("PermissionVM", "Failed to load relationships: ${result.exceptionOrNull()?.message}")
                _state.value = PermissionListState(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load contacts."
                )
            }
        }
    }

    fun approveRequest(relationshipId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, actionSuccess = null)
            val result = relationshipRepository.approveRelationship(relationshipId)
            if (result.isSuccess) {
                _state.value = _state.value.copy(actionSuccess = "Request approved.")
                loadRelationships()
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to approve request."
                )
            }
        }
    }

    fun denyRequest(relationshipId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, actionSuccess = null)
            val result = relationshipRepository.denyRelationship(relationshipId)
            if (result.isSuccess) {
                _state.value = _state.value.copy(actionSuccess = "Request denied.")
                loadRelationships()
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to deny request."
                )
            }
        }
    }

    fun revokeAccess(relationshipId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, actionSuccess = null)
            val result = relationshipRepository.revokeRelationship(relationshipId)
            if (result.isSuccess) {
                _state.value = _state.value.copy(actionSuccess = "Access revoked.")
                loadRelationships()
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to revoke access."
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
