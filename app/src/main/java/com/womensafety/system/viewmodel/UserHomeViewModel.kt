package com.womensafety.system.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womensafety.system.data.model.UserProfile
import com.womensafety.system.data.model.WeeklyUid
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
        val userId = authRepository.currentUserId ?: return

        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isLoading = true)

            // Load user profile
            val profileResult = userRepository.getUserProfile(userId)
            val profile = profileResult.getOrNull()

            // Load current UID (read-only from backend, or dev fallback)
            var uidResult = uidRepository.getCurrentUid(userId)
            var uid = uidResult.getOrNull()

            // If no UID exists, generate one (Spark plan dev/demo fallback)
            if (uid == null && profile != null) {
                val devResult = uidRepository.generateDevUid(userId, profile.name)
                if (devResult.isSuccess) {
                    uid = devResult.getOrNull()
                }
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
}
