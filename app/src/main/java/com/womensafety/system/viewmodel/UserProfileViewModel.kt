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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
        val userId = authRepository.currentUserId
        if (userId == null) {
            _profileState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            if (_profileState.value.userProfile == null) {
                _profileState.update { it.copy(isLoading = true) }
            }

            try {
                val profileResult = withTimeoutOrNull(15000L) {
                    userRepository.getUserProfile(userId)
                }
                val uidResult = withTimeoutOrNull(10000L) {
                    uidRepository.getCurrentUid(userId)
                }
                val relResult = withTimeoutOrNull(10000L) {
                    relationshipRepository.getRelationshipsForUser(userId)
                }

                val relationships = relResult?.getOrNull() ?: emptyList()
                val approvedContacts = relationships.filter {
                    it.permissionState == PermissionState.APPROVED
                }
                val pendingCount = relationships.count {
                    it.permissionState == PermissionState.PENDING
                }
                val profile = profileResult?.getOrNull()
                if (profile != null) {
                    com.womensafety.system.manager.SafetyMonitoringManager.updateProfileCache(profile)
                    com.womensafety.system.manager.SafetyMonitoringManager.setProactiveEnabled(profile.proactiveMonitoringEnabled)
                    com.womensafety.system.manager.SafetyMonitoringManager.setSafetyMode(profile.safetyMode)
                    com.womensafety.system.manager.TravelModeManager.routeDeviationToleranceMeters = profile.routeDeviationToleranceMeters
                    com.womensafety.system.manager.SOSManager.updateCachedProfile(profile)
                }

                _profileState.update {
                    it.copy(
                        userProfile = profile,
                        currentUid = uidResult?.getOrNull(),
                        trustedContacts = approvedContacts,
                        trustedContactCount = approvedContacts.size,
                        pendingRequestCount = pendingCount,
                        emergencyContactCount = profile?.emergencyContacts?.size ?: 0,
                        error = if (profileResult?.isFailure == true) profileResult.exceptionOrNull()?.localizedMessage else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _profileState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to load profile.")
                }
            } finally {
                _profileState.update { it.copy(isLoading = false) }
            }
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

    fun sendPasswordReset() {
        if (_profileState.value.isLoading) return
        val email = _profileState.value.userProfile?.primaryEmail ?: return

        viewModelScope.launch {
            _profileState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                val result = withTimeoutOrNull(15000L) {
                    authRepository.sendPasswordResetEmail(email)
                }
                if (result == null) {
                    _profileState.update {
                        it.copy(error = "Password reset request timed out. Please retry.")
                    }
                    return@launch
                }
                _profileState.update {
                    it.copy(
                        successMessage = if (result.isSuccess) "Password reset link sent to $email" else null,
                        error = if (result.isFailure) result.exceptionOrNull()?.localizedMessage ?: "Failed to send password reset email." else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _profileState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to send password reset email.")
                }
            } finally {
                _profileState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ─── Change Phone ────────────────────────────────────────────

    fun updateNewPhone(phone: String) {
        _changePhoneState.update { it.copy(newPhone = phone, error = null) }
    }

    fun sendChangePhoneOtp(activity: Activity) {
        if (_changePhoneState.value.isLoading) return
        val state = _changePhoneState.value
        val normalized = Validators.normalizePhone(state.newPhone)

        if (!Validators.isValidPhone(normalized)) {
            _changePhoneState.update { it.copy(error = "Please enter a valid phone number.") }
            return
        }

        _changePhoneState.update { it.copy(isLoading = true, error = null) }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                completePhoneChange(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _changePhoneState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Phone verification failed."
                    )
                }
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                _changePhoneState.update {
                    it.copy(
                        isLoading = false,
                        verificationId = verificationId,
                        resendToken = token,
                        otpSent = true,
                        resendCooldown = 60
                    )
                }
            }
        }

        try {
            authRepository.sendPhoneOtp(normalized, activity, callbacks)
        } catch (e: Exception) {
            _changePhoneState.update {
                it.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Failed to initiate phone verification."
                )
            }
        }
    }

    fun updateChangePhoneOtp(code: String) {
        if (code.length <= 6) {
            _changePhoneState.update { it.copy(otpCode = code, error = null) }
        }
    }

    fun verifyAndChangePhone() {
        if (_changePhoneState.value.isLoading) return
        val state = _changePhoneState.value
        if (!Validators.isValidOtp(state.otpCode)) {
            _changePhoneState.update { it.copy(error = "Please enter a valid 6-digit OTP.") }
            return
        }

        val verificationId = state.verificationId
        if (verificationId == null) {
            _changePhoneState.update { it.copy(error = "No active OTP request found. Please request an OTP first.") }
            return
        }
        val credential = authRepository.getPhoneCredential(verificationId, state.otpCode)
        completePhoneChange(credential)
    }

    private fun completePhoneChange(credential: PhoneAuthCredential) {
        if (_changePhoneState.value.isLoading) return
        val state = _changePhoneState.value
        _changePhoneState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val userId = authRepository.currentUserId
                if (userId == null) {
                    _changePhoneState.update {
                        it.copy(error = "User session expired. Please sign in again.")
                    }
                    return@launch
                }
                val normalized = Validators.normalizePhone(state.newPhone)

                // Update in Firestore
                val updateResult = withTimeoutOrNull(15000L) {
                    userRepository.updatePhone(userId, normalized)
                }

                if (updateResult == null) {
                    _changePhoneState.update {
                        it.copy(error = "Updating phone timed out. Please retry.")
                    }
                    return@launch
                }

                if (updateResult.isSuccess) {
                    _changePhoneState.update {
                        it.copy(
                            success = true,
                            error = null
                        )
                    }
                    // Reload profile
                    loadProfile()
                } else {
                    _changePhoneState.update {
                        it.copy(
                            error = updateResult.exceptionOrNull()?.localizedMessage ?: "Failed to update phone number."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _changePhoneState.update {
                    it.copy(error = e.localizedMessage ?: "Failed to update phone number.")
                }
            } finally {
                _changePhoneState.update { it.copy(isLoading = false) }
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
}
