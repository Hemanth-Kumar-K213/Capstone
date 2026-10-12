package com.womensafety.system.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.data.model.Relationship
import com.womensafety.system.data.model.TrustedContactProfile
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.data.repository.RelationshipRepository
import com.womensafety.system.data.repository.TrustedContactRepository
import com.womensafety.system.data.repository.UidRepository
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Trusted Contact Home screen.
 * Manages connected users list and add-user-by-UID flow.
 * Accesses Firebase ONLY through repositories.
 *
 * IMPORTANT: User names are read from the denormalized userName field in
 * Relationship documents — NOT from cross-collection User profile reads,
 * which Firestore security rules block.
 */
class TrustedContactHomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val tcRepository: TrustedContactRepository = TrustedContactRepository(),
    private val relationshipRepository: RelationshipRepository = RelationshipRepository(),
    private val uidRepository: UidRepository = UidRepository()
) : ViewModel() {

    data class TCHomeState(
        val profile: TrustedContactProfile? = null,
        val connectedUsers: List<ConnectedUser> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null
    )

    data class ConnectedUser(
        val relationship: Relationship
    )

    private val _homeState = MutableStateFlow(TCHomeState())
    val homeState: StateFlow<TCHomeState> = _homeState.asStateFlow()

    // ─── Add User State ──────────────────────────────────────────

    data class AddUserState(
        val uid: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false
    )

    private val _addUserState = MutableStateFlow(AddUserState())
    val addUserState: StateFlow<AddUserState> = _addUserState.asStateFlow()

    fun loadHomeData() {
        val tcId = authRepository.currentUserId ?: return

        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isLoading = true)

            // Load TC profile
            val profileResult = tcRepository.getTrustedContactProfile(tcId)

            // Load relationships
            val relResult = relationshipRepository.getRelationshipsForTrustedContact(tcId)
            val relationships = relResult.getOrNull() ?: emptyList()

            Log.d("TCHomeVM", "Loaded ${relationships.size} relationships for TC $tcId")

            // Use denormalized userName from the relationship document
            // instead of cross-collection User profile reads
            val connectedUsers = relationships
                .filter { it.permissionState == PermissionState.APPROVED || it.permissionState == PermissionState.PENDING }
                .map { rel ->
                    ConnectedUser(relationship = rel)
                }

            _homeState.value = TCHomeState(
                profile = profileResult.getOrNull(),
                connectedUsers = connectedUsers,
                isLoading = false,
                error = if (relResult.isFailure) relResult.exceptionOrNull()?.message else null
            )
        }
    }

    // ─── Add User by UID ─────────────────────────────────────────

    fun updateUidInput(uid: String) {
        if (uid.length <= 12) {
            _addUserState.value = _addUserState.value.copy(uid = uid, error = null)
        }
    }

    fun addUserByUid() {
        val state = _addUserState.value
        val tcId = authRepository.currentUserId ?: return

        if (!Validators.isValidUidFormat(state.uid)) {
            _addUserState.value = state.copy(error = "Please enter a valid 12-digit UID.")
            return
        }

        _addUserState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            // Look up the UID to find the User's stable Auth UID
            val uidResult = uidRepository.findUserByUid(state.uid)

            if (uidResult.isFailure) {
                _addUserState.value = _addUserState.value.copy(
                    isLoading = false,
                    error = "Failed to look up UID. Please try again."
                )
                return@launch
            }

            val weeklyUid = uidResult.getOrNull()
            if (weeklyUid == null) {
                _addUserState.value = _addUserState.value.copy(
                    isLoading = false,
                    error = "No user found with this UID, or the UID has expired."
                )
                return@launch
            }

            if (!weeklyUid.isCurrentlyActive) {
                _addUserState.value = _addUserState.value.copy(
                    isLoading = false,
                    error = "This UID has expired. Please ask the user for their current UID."
                )
                return@launch
            }

            // Use the stable Firebase Auth UID for the relationship
            val userId = weeklyUid.userId

            if (userId == tcId) {
                _addUserState.value = _addUserState.value.copy(
                    isLoading = false,
                    error = "You cannot add yourself."
                )
                return@launch
            }

            // Get TC's own name and phone from their profile (TC can read their own profile)
            val tcProfile = _homeState.value.profile
            val tcName = tcProfile?.name ?: ""
            val tcPhone = tcProfile?.phone ?: ""

            // Get User's name from the UID mapping (denormalized there during UID generation)
            val userName = weeklyUid.userName

            Log.d("TCHomeVM", "Creating relationship: userId=$userId, tcId=$tcId, userName=$userName, tcName=$tcName, tcPhone=$tcPhone")

            // Create relationship request with denormalized names and phone
            val relResult = relationshipRepository.createRelationshipRequest(
                userId = userId,
                trustedContactId = tcId,
                userName = userName,
                tcName = tcName,
                tcPhone = tcPhone
            )

            if (relResult.isSuccess) {
                _addUserState.value = _addUserState.value.copy(
                    isLoading = false,
                    success = true
                )
                // Reload home data
                loadHomeData()
            } else {
                _addUserState.value = _addUserState.value.copy(
                    isLoading = false,
                    error = relResult.exceptionOrNull()?.message ?: "Failed to send connection request."
                )
            }
        }
    }

    fun resetAddUserState() {
        _addUserState.value = AddUserState()
    }
}

