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

    fun loadHomeData() {
        val userId = authRepository.currentUserId
        if (userId == null) {
            _homeState.value = _homeState.value.copy(isLoading = false)
            return
        }

        viewModelScope.launch {
            if (_homeState.value.userProfile == null) {
                _homeState.value = _homeState.value.copy(isLoading = true)
            }

            // Load user profile
            val profileResult = userRepository.getUserProfile(userId)
            val profile = profileResult.getOrNull()
            if (profile != null) {
                com.womensafety.system.manager.SafetyMonitoringManager.updateProfileCache(profile)
            }

            // Load current UID with instant fallback so it is never stuck on "Generating..."
            var uid: WeeklyUid? = null
            if (profile != null && profile.currentWeeklyUid.isNotBlank()) {
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

            if (uid == null) {
                val uidResult = uidRepository.getCurrentUid(userId)
                uid = uidResult.getOrNull()
            }

            if (uid == null && profile != null) {
                val devResult = uidRepository.generateDevUid(userId, profile.name)
                uid = devResult.getOrNull()
            }

            if (uid == null) {
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                val y = cal.get(java.util.Calendar.YEAR).toString()
                val m = (cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
                val d = cal.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                val r = (1000..9999).random().toString()
                val fallbackUidStr = "$y$m$d$r"
                val now = System.currentTimeMillis()
                uid = WeeklyUid(
                    userId = userId,
                    uid = fallbackUidStr,
                    userName = profile?.name ?: "User",
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
            val countResult = relationshipRepository.getApprovedContactCount(userId)
            val count = countResult.getOrNull() ?: 0

            // Load pending requests
            val relResult = relationshipRepository.getRelationshipsForUser(userId)
            val pendingCount = relResult.getOrNull()
                ?.count { it.status == "PENDING" } ?: 0

            _homeState.value = HomeState(
                userProfile = profile,
                currentUid = uid,
                trustedContactCount = count,
                pendingRequestCount = pendingCount,
                isLoading = false,
                error = if (profileResult.isFailure) profileResult.exceptionOrNull()?.message else null
            )
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
