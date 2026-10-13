package com.womensafety.system.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.data.model.Relationship
import com.womensafety.system.data.model.TrustedContactProfile
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.data.repository.LocationRepository
import com.womensafety.system.data.repository.RelationshipRepository
import com.womensafety.system.data.repository.TrustedContactRepository
import com.womensafety.system.data.repository.UidRepository
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private val uidRepository: UidRepository = UidRepository(),
    private val locationRepository: LocationRepository = LocationRepository()
) : ViewModel() {

    data class TCHomeState(
        val profile: TrustedContactProfile? = null,
        val connectedUsers: List<ConnectedUser> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null
    )

    data class ConnectedUser(
        val relationship: Relationship,
        val userUid: String = "",
        val latestLocation: UserLocation? = null
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

    private val deletedRelationshipIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val deletedUserIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private var observeJob: kotlinx.coroutines.Job? = null

    private fun enrichConnectedUsers(users: List<ConnectedUser>) {
        viewModelScope.launch {
            val updated = users.map { cu ->
                var uid = cu.userUid
                var loc = cu.latestLocation

                if (uid.isBlank()) {
                    val weeklyUidResult = uidRepository.getCurrentUid(cu.relationship.userId).getOrNull()
                    if (weeklyUidResult != null && weeklyUidResult.uid.isNotBlank()) {
                        uid = weeklyUidResult.uid
                    }
                }

                if (loc == null || uid.isBlank()) {
                    val latest = locationRepository.getLatestUidLocation(cu.relationship.userId).getOrNull()
                        ?: locationRepository.getLatestLocation(cu.relationship.userId).getOrNull()
                    if (latest != null) {
                        loc = latest
                        if (uid.isBlank() && latest.uid.isNotBlank()) {
                            uid = latest.uid
                        }
                    }
                }

                cu.copy(userUid = uid, latestLocation = loc)
            }

            _homeState.update { current ->
                current.copy(connectedUsers = updated)
            }
        }
    }

    fun loadHomeData() {
        val tcId = authRepository.currentUserId
        if (tcId == null) {
            _homeState.value = _homeState.value.copy(isLoading = false)
            return
        }

        // Real-time observation of TC relationships
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            relationshipRepository.observeRelationshipsForTrustedContact(tcId).collect { relationships ->
                val activeList = relationships
                    .filter { rel ->
                        !deletedRelationshipIds.contains(rel.id) &&
                        !deletedUserIds.contains(rel.userId) &&
                        (rel.permissionState == PermissionState.APPROVED || rel.permissionState == PermissionState.PENDING)
                    }
                    .map { rel ->
                        val existing = _homeState.value.connectedUsers.find { it.relationship.id == rel.id }
                        ConnectedUser(
                            relationship = rel,
                            userUid = existing?.userUid ?: "",
                            latestLocation = existing?.latestLocation
                        )
                    }

                _homeState.value = _homeState.value.copy(
                    connectedUsers = activeList,
                    isLoading = false
                )
                enrichConnectedUsers(activeList)
            }
        }

        viewModelScope.launch {
            if (_homeState.value.profile == null) {
                _homeState.update { it.copy(isLoading = true) }
            }

            try {
                // Load TC profile
                val profileResult = withTimeoutOrNull(15000L) {
                    tcRepository.getTrustedContactProfile(tcId)
                }
                val profile = profileResult?.getOrNull()

                // Load relationships
                val relResult = withTimeoutOrNull(15000L) {
                    relationshipRepository.getRelationshipsForTrustedContact(tcId)
                }
                val relationships = relResult?.getOrNull() ?: emptyList()

                Log.d("TCHomeVM", "Loaded ${relationships.size} relationships for TC $tcId")

                // Use denormalized userName from the relationship document
                // Filter only genuine ACTIVE or PENDING relationships, excluding deleted users
                val connectedUsers = relationships
                    .filter { rel ->
                        !deletedRelationshipIds.contains(rel.id) &&
                        !deletedUserIds.contains(rel.userId) &&
                        (rel.permissionState == PermissionState.APPROVED || rel.permissionState == PermissionState.PENDING)
                    }
                    .map { rel ->
                        val existing = _homeState.value.connectedUsers.find { it.relationship.id == rel.id }
                        ConnectedUser(
                            relationship = rel,
                            userUid = existing?.userUid ?: "",
                            latestLocation = existing?.latestLocation
                        )
                    }

                _homeState.update {
                    it.copy(
                        profile = profile,
                        connectedUsers = connectedUsers,
                        error = if (relResult?.isFailure == true) relResult.exceptionOrNull()?.localizedMessage else null
                    )
                }
                enrichConnectedUsers(connectedUsers)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _homeState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to load dashboard data.")
                }
            } finally {
                _homeState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Removes a user from the Guardian's monitored list.
     * Works for both APPROVED users (unpairing/disconnecting) and PENDING requests (canceling).
     */
    fun removeUser(relationshipId: String, targetUserId: String? = null) {
        val tcId = authRepository.currentUserId ?: return
        val currentList = _homeState.value.connectedUsers
        val matchingUser = currentList.find { it.relationship.id == relationshipId }
        val resolvedUserId = targetUserId ?: matchingUser?.relationship?.userId ?: ""

        // Instantly add to deletion blacklist so this user never flickers or re-appears
        deletedRelationshipIds.add(relationshipId)
        if (resolvedUserId.isNotBlank()) {
            deletedUserIds.add(resolvedUserId)
        }

        // Optimistic UI update: instantly remove from screen
        _homeState.update { state ->
            state.copy(
                connectedUsers = state.connectedUsers.filterNot {
                    it.relationship.id == relationshipId || (resolvedUserId.isNotBlank() && it.relationship.userId == resolvedUserId)
                }
            )
        }

        viewModelScope.launch {
            val result = withTimeoutOrNull(15000L) {
                relationshipRepository.removeRelationshipByTc(tcId, relationshipId, resolvedUserId)
            } ?: Result.failure(Exception("Operation timed out"))

            if (result.isFailure) {
                Log.e("TCHomeVM", "Failed to remove user $relationshipId: ${result.exceptionOrNull()?.message}")
            } else {
                Log.d("TCHomeVM", "Successfully removed user $relationshipId")
            }
            // Always reload to sync authoritative state
            loadHomeData()
        }
    }

    // ─── Add User by UID ─────────────────────────────────────────

    fun updateUidInput(uid: String) {
        if (uid.length <= 12) {
            _addUserState.value = _addUserState.value.copy(uid = uid, error = null)
        }
    }

    fun addUserByUid() {
        if (_addUserState.value.isLoading) return
        val state = _addUserState.value
        val tcId = authRepository.currentUserId
        if (tcId == null) {
            _addUserState.update { it.copy(error = "Authentication required. Please re-login.") }
            return
        }

        if (!Validators.isValidUidFormat(state.uid)) {
            _addUserState.update { it.copy(error = "Please enter a valid 12-digit UID.") }
            return
        }

        _addUserState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // Look up the UID to find the User's stable Auth UID
                val uidResult = withTimeoutOrNull(15000L) {
                    uidRepository.findUserByUid(state.uid)
                }

                if (uidResult == null) {
                    _addUserState.update {
                        it.copy(error = "UID lookup timed out. Please check your connection and retry.")
                    }
                    return@launch
                }

                if (uidResult.isFailure) {
                    val err = uidResult.exceptionOrNull()?.localizedMessage
                    _addUserState.update {
                        it.copy(error = err ?: "Failed to look up UID. Please try again.")
                    }
                    return@launch
                }

                val weeklyUid = uidResult.getOrNull()
                if (weeklyUid == null) {
                    _addUserState.update {
                        it.copy(
                            error = "No user found with UID '${state.uid}'. Please ensure the user has generated and shared their active Safety UID."
                        )
                    }
                    return@launch
                }

                // Use the stable Firebase Auth UID for the relationship
                val userId = weeklyUid.userId

                if (userId.isBlank()) {
                    _addUserState.update {
                        it.copy(error = "Unable to resolve User identity for this UID.")
                    }
                    return@launch
                }

                if (userId == tcId) {
                    _addUserState.update {
                        it.copy(error = "You cannot add yourself as a monitored user.")
                    }
                    return@launch
                }

                // Get TC's own name and phone from profile (load from repository if not cached)
                val tcProfile = _homeState.value.profile ?: tcRepository.getTrustedContactProfile(tcId).getOrNull()
                val tcName = tcProfile?.name ?: "Guardian"
                val tcPhone = tcProfile?.phone ?: ""

                // Get User's name from the UID mapping
                val userName = weeklyUid.userName.ifBlank { "Protected User" }

                Log.d("TCHomeVM", "Creating relationship: userId=$userId, tcId=$tcId, userName=$userName, tcName=$tcName, tcPhone=$tcPhone")

                // Create relationship request with denormalized names & phone
                val relResult = withTimeoutOrNull(15000L) {
                    relationshipRepository.createRelationshipRequest(
                        userId = userId,
                        trustedContactId = tcId,
                        userName = userName,
                        tcName = tcName,
                        tcPhone = tcPhone
                    )
                }

                if (relResult == null) {
                    _addUserState.update {
                        it.copy(error = "Connection request timed out. Please retry.")
                    }
                    return@launch
                }

                if (relResult.isSuccess) {
                    deletedUserIds.remove(userId)
                    deletedRelationshipIds.remove(FirestoreManager.relationshipDocId(userId, tcId))
                    _addUserState.update {
                        it.copy(
                            success = true,
                            error = null
                        )
                    }
                    // Reload home data
                    loadHomeData()
                } else {
                    val errMsg = relResult.exceptionOrNull()?.localizedMessage ?: "Failed to send connection request."
                    _addUserState.update { it.copy(error = errMsg) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("TCHomeVM", "Error in addUserByUid", e)
                _addUserState.update {
                    it.copy(error = e.localizedMessage ?: "An unexpected error occurred. Please try again.")
                }
            } finally {
                _addUserState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun resetAddUserState() {
        _addUserState.value = AddUserState()
    }
}

