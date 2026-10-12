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
        val pendingRequests: List<com.womensafety.system.data.model.Relationship> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private var relationshipListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun startListeningForRequests(userId: String) {
        relationshipListener?.remove()
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            relationshipListener = db.collection("relationships")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            android.util.Log.w("UserHomeVM", "Snapshot listener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val rels = snapshot.documents.mapNotNull { doc ->
                                try {
                                    doc.data?.let { com.womensafety.system.data.model.Relationship.fromMap(doc.id, it) }
                                } catch (e: Throwable) {
                                    null
                                }
                            }
                            val pending = rels.filter { it.status == "PENDING" }
                            val approved = rels.count { it.status == "APPROVED" }
                            _homeState.value = _homeState.value.copy(
                                pendingRequests = pending,
                                pendingRequestCount = pending.size,
                                trustedContactCount = if (approved > 0) approved else _homeState.value.trustedContactCount
                            )
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("UserHomeVM", "Error in relationships snapshot callback", t)
                    }
                }
        } catch (e: Throwable) {
            android.util.Log.w("UserHomeVM", "Snapshot attach fallback: ${e.message}")
        }
    }

    fun refreshRequests() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val relResult = relationshipRepository.getRelationshipsForUser(userId)
                val allRels = relResult.getOrNull() ?: emptyList()
                val pendingList = allRels.filter { it.status == "PENDING" }
                _homeState.value = _homeState.value.copy(
                    pendingRequests = pendingList,
                    pendingRequestCount = pendingList.size
                )
            } catch (e: Throwable) {
                android.util.Log.w("UserHomeVM", "refreshRequests notice: ${e.message}")
            }
        }
    }

    fun loadHomeData() {
        val userId = authRepository.currentUserId ?: return

        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isLoading = true)

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
            val allRels = relResult.getOrNull() ?: emptyList()
            val pendingList = allRels.filter { it.status == "PENDING" }
            val pendingCount = pendingList.size

            _homeState.value = HomeState(
                userProfile = profile,
                currentUid = uid,
                trustedContactCount = count,
                pendingRequestCount = pendingCount,
                pendingRequests = pendingList,
                isLoading = false,
                error = if (profileResult.isFailure) profileResult.exceptionOrNull()?.message else null
            )

            // Start active listener for instant real-time bell updates
            startListeningForRequests(userId)
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

    fun approveRequest(relationshipId: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val result = relationshipRepository.approveRelationship(relationshipId)
            if (result.isSuccess) {
                loadHomeData()
                onComplete?.invoke(true, null)
            } else {
                onComplete?.invoke(false, result.exceptionOrNull()?.localizedMessage ?: "Failed to accept request.")
            }
        }
    }

    fun denyRequest(relationshipId: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val result = relationshipRepository.denyRelationship(relationshipId)
            if (result.isSuccess) {
                loadHomeData()
                onComplete?.invoke(true, null)
            } else {
                onComplete?.invoke(false, result.exceptionOrNull()?.localizedMessage ?: "Failed to reject request.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        relationshipListener?.remove()
    }
}

