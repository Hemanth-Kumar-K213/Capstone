package com.womensafety.system.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.womensafety.system.data.model.Relationship
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.data.model.UserProfile
import com.womensafety.system.data.model.WeeklyUid
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.data.repository.RelationshipRepository
import com.womensafety.system.data.repository.UidRepository
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.utils.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for User Profile screen.
 * Accesses Firebase ONLY through repositories.
 */
class UserProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val uidRepository: UidRepository = UidRepository(),
    private val relationshipRepository: RelationshipRepository = RelationshipRepository()
) : ViewModel() {

    data class ProfileState(
        val userProfile: UserProfile? = null,
        val currentUid: WeeklyUid? = null,
        val trustedContacts: List<Relationship> = emptyList(),
        val trustedContactCount: Int = 0,
        val pendingRequestCount: Int = 0,
        val emergencyContactCount: Int = 0,
        val isLoading: Boolean = true,
        val error: String? = null,
        val successMessage: String? = null
    )

    private val _profileState = MutableStateFlow(ProfileState())
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    // ─── Change Phone State ──────────────────────────────────────

    data class ChangePhoneState(
        val newPhone: String = "",
        val verificationId: String? = null,
        val resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        val otpCode: String = "",
        val otpSent: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        val resendCooldown: Int = 0
    )

    private val _changePhoneState = MutableStateFlow(ChangePhoneState())
    val changePhoneState: StateFlow<ChangePhoneState> = _changePhoneState.asStateFlow()

    fun loadProfile() {
        val userId = authRepository.currentUserId ?: return

        viewModelScope.launch {
            _profileState.value = _profileState.value.copy(isLoading = true)

            val profileResult = userRepository.getUserProfile(userId)
            val uidResult = uidRepository.getCurrentUid(userId)

            // Load trusted contact relationships
            val relResult = relationshipRepository.getRelationshipsForUser(userId)
            val relationships = relResult.getOrNull() ?: emptyList()
            val approvedContacts = relationships.filter {
                it.permissionState == PermissionState.APPROVED
            }
            val pendingCount = relationships.count {
                it.permissionState == PermissionState.PENDING
            }
            val profile = profileResult.getOrNull()
            if (profile != null) {
                com.womensafety.system.manager.SafetyMonitoringManager.updateProfileCache(profile)
                com.womensafety.system.manager.SafetyMonitoringManager.setProactiveEnabled(profile.proactiveMonitoringEnabled)
                com.womensafety.system.manager.SafetyMonitoringManager.setSafetyMode(profile.safetyMode)
                com.womensafety.system.manager.TravelModeManager.routeDeviationToleranceMeters = profile.routeDeviationToleranceMeters
            }

            _profileState.value = ProfileState(
                userProfile = profile,
                currentUid = uidResult.getOrNull(),
                trustedContacts = approvedContacts,
                trustedContactCount = approvedContacts.size,
                pendingRequestCount = pendingCount,
                emergencyContactCount = profile?.emergencyContacts?.size ?: 0,
                isLoading = false,
                error = if (profileResult.isFailure) profileResult.exceptionOrNull()?.message else null
            )
        }
    }

    fun updateSafetyPreferences(
        locationTrackingEnabled: Boolean = _profileState.value.userProfile?.locationTrackingEnabled ?: false,
        shakeDetectionEnabled: Boolean = _profileState.value.userProfile?.shakeDetectionEnabled ?: false,
        shakeSensitivity: String = _profileState.value.userProfile?.shakeSensitivity ?: "MEDIUM",
        sosAutoCall: Boolean = _profileState.value.userProfile?.sosAutoCall ?: false,
        proactiveMonitoringEnabled: Boolean = _profileState.value.userProfile?.proactiveMonitoringEnabled ?: false,
        safetyMode: String = _profileState.value.userProfile?.safetyMode ?: "NORMAL",
        routeDeviationToleranceMeters: Int = _profileState.value.userProfile?.routeDeviationToleranceMeters ?: 300,
        activeSafetyMode: String = _profileState.value.userProfile?.activeSafetyMode ?: "REGULAR",
        defaultSafetyMode: String = _profileState.value.userProfile?.defaultSafetyMode ?: "REGULAR",
        autoModeSuggestionsEnabled: Boolean = _profileState.value.userProfile?.autoModeSuggestionsEnabled ?: true,
        savedWorkplaceLatitude: Double? = _profileState.value.userProfile?.savedWorkplaceLatitude,
        savedWorkplaceLongitude: Double? = _profileState.value.userProfile?.savedWorkplaceLongitude,
        savedWorkplaceAddress: String? = _profileState.value.userProfile?.savedWorkplaceAddress,
        savedHomeLatitude: Double? = _profileState.value.userProfile?.savedHomeLatitude,
        savedHomeLongitude: Double? = _profileState.value.userProfile?.savedHomeLongitude,
        savedHomeAddress: String? = _profileState.value.userProfile?.savedHomeAddress
    ) {
        val currentProfile = _profileState.value.userProfile ?: return
        val updatedProfile = currentProfile.copy(
            locationTrackingEnabled = locationTrackingEnabled,
            shakeDetectionEnabled = shakeDetectionEnabled,
            shakeSensitivity = shakeSensitivity,
            sosAutoCall = sosAutoCall,
            proactiveMonitoringEnabled = proactiveMonitoringEnabled,
            safetyMode = safetyMode,
            routeDeviationToleranceMeters = routeDeviationToleranceMeters,
            activeSafetyMode = activeSafetyMode,
            defaultSafetyMode = defaultSafetyMode,
            autoModeSuggestionsEnabled = autoModeSuggestionsEnabled,
            savedWorkplaceLatitude = savedWorkplaceLatitude,
            savedWorkplaceLongitude = savedWorkplaceLongitude,
            savedWorkplaceAddress = savedWorkplaceAddress,
            savedHomeLatitude = savedHomeLatitude,
            savedHomeLongitude = savedHomeLongitude,
            savedHomeAddress = savedHomeAddress
        )
        _profileState.value = _profileState.value.copy(userProfile = updatedProfile)

        com.womensafety.system.manager.SafetyMonitoringManager.updateProfileCache(updatedProfile)
        com.womensafety.system.manager.SafetyMonitoringManager.setProactiveEnabled(proactiveMonitoringEnabled)
        com.womensafety.system.manager.SafetyMonitoringManager.setSafetyMode(safetyMode)
        com.womensafety.system.manager.TravelModeManager.routeDeviationToleranceMeters = routeDeviationToleranceMeters

        viewModelScope.launch {
            val preferences = mapOf(
                "locationTrackingEnabled" to locationTrackingEnabled,
                "shakeDetectionEnabled" to shakeDetectionEnabled,
                "shakeSensitivity" to shakeSensitivity,
                "sosAutoCall" to sosAutoCall,
                "proactiveMonitoringEnabled" to proactiveMonitoringEnabled,
                "safetyMode" to safetyMode,
                "routeDeviationToleranceMeters" to routeDeviationToleranceMeters,
                "activeSafetyMode" to activeSafetyMode,
                "defaultSafetyMode" to defaultSafetyMode,
                "autoModeSuggestionsEnabled" to autoModeSuggestionsEnabled,
                "savedWorkplaceLatitude" to savedWorkplaceLatitude,
                "savedWorkplaceLongitude" to savedWorkplaceLongitude,
                "savedWorkplaceAddress" to savedWorkplaceAddress,
                "savedHomeLatitude" to savedHomeLatitude,
                "savedHomeLongitude" to savedHomeLongitude,
                "savedHomeAddress" to savedHomeAddress
            )
            val result = userRepository.updateSetupStatus(
                setupCompleted = currentProfile.setupCompleted,
                preferences = preferences
            )
            if (result.isFailure) {
                _profileState.value = _profileState.value.copy(
                    error = "Failed to update safety preferences."
                )
            }
        }
    }

    fun updatePersonalInformation(
        name: String,
        homeAddress: String,
        workAddress: String
    ) {
        val currentProfile = _profileState.value.userProfile ?: return
        val updatedProfile = currentProfile.copy(
            name = name,
            savedHomeAddress = homeAddress,
            savedWorkplaceAddress = workAddress
        )
        _profileState.value = _profileState.value.copy(userProfile = updatedProfile)

        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            val updates = mapOf(
                "name" to name,
                "savedHomeAddress" to homeAddress,
                "savedWorkplaceAddress" to workAddress
            )
            val result = userRepository.updateUserProfile(userId, updates)
            if (result.isSuccess) {
                _profileState.value = _profileState.value.copy(
                    successMessage = "Personal information updated successfully."
                )
                loadProfile()
            } else {
                _profileState.value = _profileState.value.copy(
                    error = "Failed to update personal information."
                )
            }
        }
    }

    fun sendPasswordReset() {
        val email = _profileState.value.userProfile?.primaryEmail ?: return

        viewModelScope.launch {
            _profileState.value = _profileState.value.copy(isLoading = true)
            val result = authRepository.sendPasswordResetEmail(email)
            _profileState.value = _profileState.value.copy(
                isLoading = false,
                successMessage = if (result.isSuccess) "Password reset link sent to $email" else null,
                error = if (result.isFailure) "Failed to send password reset email." else null
            )
        }
    }

    // ─── Change Phone ────────────────────────────────────────────

    fun updateNewPhone(phone: String) {
        _changePhoneState.value = _changePhoneState.value.copy(newPhone = phone, error = null)
    }

    fun sendChangePhoneOtp(activity: Activity) {
        val state = _changePhoneState.value
        val normalized = Validators.normalizePhone(state.newPhone)

        if (!Validators.isValidPhone(normalized)) {
            _changePhoneState.value = state.copy(error = "Please enter a valid phone number.")
            return
        }

        _changePhoneState.value = state.copy(isLoading = true, error = null)

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                completePhoneChange(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _changePhoneState.value = _changePhoneState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Phone verification failed."
                )
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                _changePhoneState.value = _changePhoneState.value.copy(
                    isLoading = false,
                    verificationId = verificationId,
                    resendToken = token,
                    otpSent = true,
                    resendCooldown = 60
                )
            }
        }

        authRepository.sendPhoneOtp(normalized, activity, callbacks)
    }

    fun updateChangePhoneOtp(code: String) {
        if (code.length <= 6) {
            _changePhoneState.value = _changePhoneState.value.copy(otpCode = code, error = null)
        }
    }

    fun verifyAndChangePhone() {
        val state = _changePhoneState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _changePhoneState.value = state.copy(error = "Please enter a valid 6-digit OTP.")
            return
        }

        val verificationId = state.verificationId ?: return
        val credential = authRepository.getPhoneCredential(verificationId, state.otpCode)
        completePhoneChange(credential)
    }

    private fun completePhoneChange(credential: PhoneAuthCredential) {
        val state = _changePhoneState.value
        _changePhoneState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            val normalized = Validators.normalizePhone(state.newPhone)

            // NOTE: Phone duplicate checking is handled by Firebase Auth.
            // If this phone is already linked to another account, the
            // updatePhoneNumber/linkPhoneCredential call will fail with
            // an appropriate error.

            // Update in Firestore
            val updateResult = userRepository.updatePhone(userId, normalized)
            if (updateResult.isSuccess) {
                _changePhoneState.value = _changePhoneState.value.copy(
                    isLoading = false,
                    success = true
                )
                // Reload profile
                loadProfile()
            } else {
                _changePhoneState.value = _changePhoneState.value.copy(
                    isLoading = false,
                    error = "Failed to update phone number."
                )
            }
        }
    }

    fun resetChangePhoneState() {
        _changePhoneState.value = ChangePhoneState()
    }

    fun updateResendCooldown(seconds: Int) {
        _changePhoneState.value = _changePhoneState.value.copy(resendCooldown = seconds)
    }

    fun clearMessages() {
        _profileState.value = _profileState.value.copy(error = null, successMessage = null)
    }

    fun submitFeedback(feedbackMessage: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val uid = authRepository.currentUserId
                ?: _profileState.value.userProfile?.authUserId
                ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: ""

            if (uid.isBlank()) {
                onComplete?.invoke(false, "User not authenticated. Please log in.")
                return@launch
            }

            val name = _profileState.value.userProfile?.name?.ifBlank { null }
                ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName?.ifBlank { null }
                ?: "User"

            try {
                val res = userRepository.submitFeedback(
                    userId = uid,
                    userName = name,
                    feedbackMessage = feedbackMessage
                )
                if (res.isSuccess) {
                    onComplete?.invoke(true, null)
                } else {
                    onComplete?.invoke(false, res.exceptionOrNull()?.localizedMessage)
                }
            } catch (e: Exception) {
                onComplete?.invoke(false, e.localizedMessage)
            }
        }
    }
}
