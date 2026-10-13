package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.model.UserProfile
import com.womensafety.system.data.model.WeeklyUid
import com.womensafety.system.data.model.SafetyMode
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.data.repository.RelationshipRepository
import com.womensafety.system.data.repository.UidRepository
import com.womensafety.system.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the User Home screen.
 * Accesses Firebase ONLY through repositories.
 */
class UserHomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val uidRepository: UidRepository = UidRepository(),
    private val relationshipRepository: RelationshipRepository = RelationshipRepository()
) : ViewModel() {

    data class HomeState(
        val userProfile: UserProfile? = null,
        val currentUid: WeeklyUid? = null,
        val trustedContactCount: Int = 0,
        val pendingRequestCount: Int = 0,
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private var observeJob: kotlinx.coroutines.Job? = null

    fun loadHomeData() {
        val userId = authRepository.currentUserId
        if (userId == null) {
            _homeState.value = _homeState.value.copy(isLoading = false)
            return
        }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            relationshipRepository.observeRelationshipsForUser(userId).collect { rels ->
                val approved = rels.count { it.permissionState == com.womensafety.system.data.model.PermissionState.APPROVED }
                val pending = rels.count { it.permissionState == com.womensafety.system.data.model.PermissionState.PENDING }
                _homeState.value = _homeState.value.copy(
                    trustedContactCount = approved,
                    pendingRequestCount = pending
                )
            }
        }

        viewModelScope.launch {
            if (_homeState.value.userProfile == null) {
                _homeState.value = _homeState.value.copy(isLoading = true)
            }

            try {
                // Load user profile with timeout
                val profileResult = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    userRepository.getUserProfile(userId)
                } ?: Result.failure(Exception("Timed out loading profile"))

                val profile = profileResult.getOrNull()
                if (profile != null) {
                    com.womensafety.system.manager.SafetyMonitoringManager.updateProfileCache(profile)
                }

                // Load current UID guaranteed to be persisted in Firestore (so TCs can always resolve it)
                var uid: WeeklyUid? = null
                val uidResult = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    uidRepository.getCurrentUid(userId)
                }
                uid = uidResult?.getOrNull()

                if (uid == null) {
                    val userName = profile?.name?.ifBlank { "User" } ?: "User"
                    val devResult = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                        uidRepository.generateDevUid(userId, userName)
                    }
                    uid = devResult?.getOrNull()
                }

                if (uid == null && profile != null && profile.currentWeeklyUid.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    uid = WeeklyUid(
                        userId = userId,
                        uid = profile.currentWeeklyUid,
                        userName = profile.name,
                        generatedAt = now,
                        expiresAt = now + 7L * 24 * 60 * 60 * 1000,
                        active = true
                    )
                }

                // Keep LocationTrackingService synchronized with the exact active UID
                if (uid != null) {
                    com.womensafety.system.service.LocationTrackingService.setTargetUid(uid.uid, uid.userName)
                }

                // Load TC count
                val countResult = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    relationshipRepository.getApprovedContactCount(userId)
                }
                val count = countResult?.getOrNull() ?: 0

                // Load pending requests
                val relResult = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    relationshipRepository.getRelationshipsForUser(userId)
                }
                val pendingCount = relResult?.getOrNull()
                    ?.count { it.status == "PENDING" } ?: 0

                _homeState.value = _homeState.value.copy(
                    userProfile = profile ?: _homeState.value.userProfile,
                    currentUid = uid ?: _homeState.value.currentUid,
                    trustedContactCount = count,
                    pendingRequestCount = pendingCount,
                    isLoading = false,
                    error = if (profileResult.isFailure) profileResult.exceptionOrNull()?.message else null
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _homeState.value = _homeState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load home data"
                )
            } finally {
                _homeState.value = _homeState.value.copy(isLoading = false)
            }
        }
    }

    fun updateActiveSafetyMode(mode: SafetyMode) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            val currentProfile = _homeState.value.userProfile
            if (currentProfile != null) {
                val updated = currentProfile.copy(activeSafetyMode = mode.name)
                _homeState.value = _homeState.value.copy(userProfile = updated)
                com.womensafety.system.manager.SafetyMonitoringManager.updateProfileCache(updated)
            }
            userRepository.updateUserProfile(userId, mapOf("activeSafetyMode" to mode.name))
        }
    }

    fun saveWorkplaceLocation(latitude: Double, longitude: Double, address: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            val currentProfile = _homeState.value.userProfile
            if (currentProfile != null) {
                val updated = currentProfile.copy(
                    savedWorkplaceLatitude = latitude,
                    savedWorkplaceLongitude = longitude,
                    savedWorkplaceAddress = address
                )
                _homeState.value = _homeState.value.copy(userProfile = updated)
                com.womensafety.system.manager.SafetyMonitoringManager.updateProfileCache(updated)
            }
            userRepository.updateUserProfile(userId, mapOf(
                "savedWorkplaceLatitude" to latitude,
                "savedWorkplaceLongitude" to longitude,
                "savedWorkplaceAddress" to address
            ))
        }
    }

    fun saveHomeLocation(latitude: Double, longitude: Double, address: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            val currentProfile = _homeState.value.userProfile
            if (currentProfile != null) {
                val updated = currentProfile.copy(
                    savedHomeLatitude = latitude,
                    savedHomeLongitude = longitude,
                    savedHomeAddress = address
                )
                _homeState.value = _homeState.value.copy(userProfile = updated)
                com.womensafety.system.manager.SafetyMonitoringManager.updateProfileCache(updated)
            }
            userRepository.updateUserProfile(userId, mapOf(
                "savedHomeLatitude" to latitude,
                "savedHomeLongitude" to longitude,
                "savedHomeAddress" to address
            ))
        }
    }
}
