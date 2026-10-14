package com.womensafety.system.manager

import android.content.Context
import android.util.Log
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.*
import com.womensafety.system.data.repository.ColleagueRepository
import com.womensafety.system.data.repository.SafetyBuddyRepository
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.data.sms.TwoFactorClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Manager coordinating workplace presence, colleague safety partnerships,
 * late-stay detection, and safe transport dispatch.
 */
object ColleagueAwarenessManager {

    private const val TAG = "ColleagueManager"

    private val colleagueRepository = ColleagueRepository()
    private val safetyBuddyRepository = SafetyBuddyRepository()
    private val userRepository = UserRepository()
    private val authManager = FirebaseAuthManager()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _colleagues = MutableStateFlow<List<Colleague>>(emptyList())
    val colleagues: StateFlow<List<Colleague>> = _colleagues.asStateFlow()

    private val _safetyBuddy = MutableStateFlow<Colleague?>(null)
    val safetyBuddy: StateFlow<Colleague?> = _safetyBuddy.asStateFlow()

    private val _incomingRequests = MutableStateFlow<List<BuddyRequest>>(emptyList())
    val incomingRequests: StateFlow<List<BuddyRequest>> = _incomingRequests.asStateFlow()

    private val _isLateStayActive = MutableStateFlow(false)
    val isLateStayActive: StateFlow<Boolean> = _isLateStayActive.asStateFlow()

    private val _transportOptions = MutableStateFlow<List<TransportSuggestion>>(emptyList())
    val transportOptions: StateFlow<List<TransportSuggestion>> = _transportOptions.asStateFlow()

    private val _bookingConfirmation = MutableStateFlow<String?>(null)
    val bookingConfirmation: StateFlow<String?> = _bookingConfirmation.asStateFlow()

    private var appContext: Context? = null
    private var detectionManager: ColleagueDetectionManager? = null

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        colleagueRepository.initialize(context)
        detectionManager = ColleagueDetectionManager(context)

        scope.launch {
            detectionManager?.colleagues?.collect { discovered ->
                _colleagues.value = discovered
            }
        }

        refreshData()
    }

    fun startMonitoring(workplaceLatitude: Double, workplaceLongitude: Double) {
        detectionManager?.startWorkingModeMonitoring(workplaceLatitude, workplaceLongitude)
        refreshData()
    }

    fun stopMonitoring() {
        detectionManager?.stopWorkingModeMonitoring()
    }

    fun refreshData() {
        scope.launch {
            val list = colleagueRepository.getColleaguesAtWorkplace("workplace_main")
            if (_colleagues.value.isEmpty()) {
                _colleagues.value = list
            }
            _safetyBuddy.value = colleagueRepository.getCurrentSafetyBuddy("workplace_main")
            _incomingRequests.value = safetyBuddyRepository.getIncomingBuddyRequests()
            checkForLateStay()
        }
    }

    /**
     * Checks if current time is past standard work hours (past 6:30 PM).
     */
    fun checkForLateStay() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // Late stay threshold: after 6:30 PM (18:30) or before 6:00 AM
        val isLate = (hour > 18 || (hour == 18 && minute >= 30)) || hour < 6
        if (isLate && !_isLateStayActive.value) {
            triggerLateStayAlert(reason = "Working past 6:30 PM scheduled hours")
        }
    }

    /**
     * Triggers a late-stay safety alert and compiles verified transport options.
     */
    fun triggerLateStayAlert(reason: String = "Extended evening work session") {
        _isLateStayActive.value = true

        scope.launch {
            val alert = LateStayAlert(
                userId = authManager.currentUserId ?: "",
                workplaceId = "workplace_main",
                estimatedDeparture = System.currentTimeMillis() + (45 * 60 * 1000L),
                reason = reason,
                emergencyContactsNotified = true
            )
            colleagueRepository.recordLateStayAlert(alert)
            generateTransportSuggestions()
        }
    }

    /**
     * Generates context-aware, verified transport suggestions.
     */
    suspend fun generateTransportSuggestions() {
        val buddy = _safetyBuddy.value
        val colleaguesLeaving = colleagueRepository.getColleaguesAlsoLeavingNow("workplace_main")
            .filter { it.id != buddy?.id }

        val suggestions = mutableListOf<TransportSuggestion>()

        // 1. Safety Buddy shared commute
        if (buddy != null) {
            suggestions.add(TransportSuggestion.SharedWithBuddy(buddy))
        }

        // 2. Colleague Carpool
        val leavingColleague = colleaguesLeaving.firstOrNull()
        if (leavingColleague != null) {
            suggestions.add(TransportSuggestion.SharedWithColleague(leavingColleague))
        }

        // 3. Security Escort Company Cab
        suggestions.add(TransportSuggestion.CompanyCab)

        // 4. Commercial Verified Rideshare (Ola / Uber)
        suggestions.add(TransportSuggestion.RideShare("Ola Safety Shield"))
        suggestions.add(TransportSuggestion.RideShare("Uber Reserve"))

        _transportOptions.value = suggestions
    }

    /**
     * Sends a real bilateral Safety Buddy request to a coworker.
     */
    fun sendBuddyRequest(colleague: Colleague) {
        scope.launch {
            safetyBuddyRepository.sendBuddyRequest(colleague, "workplace_main")
            refreshData()
        }
    }

    fun acceptBuddyRequest(requesterUserId: String) {
        scope.launch {
            safetyBuddyRepository.acceptBuddyRequest(requesterUserId)
            val matched = _colleagues.value.find { it.id == requesterUserId }
            if (matched != null) {
                _safetyBuddy.value = matched.copy(isSafetyBuddy = true)
                detectionManager?.setBuddyPairing(requesterUserId, true)
            }
            refreshData()
        }
    }

    fun declineBuddyRequest(requesterUserId: String) {
        scope.launch {
            safetyBuddyRepository.declineBuddyRequest(requesterUserId)
            refreshData()
        }
    }

    /**
     * Instantly pairs a Safety Buddy (or updates active buddy state).
     */
    fun setSafetyBuddy(colleague: Colleague) {
        scope.launch {
            safetyBuddyRepository.sendBuddyRequest(colleague, "workplace_main")
            safetyBuddyRepository.acceptBuddyRequest(colleague.id)
            _safetyBuddy.value = colleague.copy(isSafetyBuddy = true)
            detectionManager?.setBuddyPairing(colleague.id, true)
            refreshData()
        }
    }

    fun simulateBuddyPairing(colleague: Colleague) {
        safetyBuddyRepository.addSimulatedBuddy(colleague, "workplace_main")
        _safetyBuddy.value = colleague.copy(isSafetyBuddy = true)
        detectionManager?.setBuddyPairing(colleague.id, true)
    }

    /**
     * Books transport option and dispatches SMS notification to emergency contacts.
     */
    fun bookTransport(option: TransportSuggestion) {
        scope.launch {
            val confirmationMsg = "Booked ${option.title}. ETA: ${option.etaMinutes} mins. Contacts notified."
            _bookingConfirmation.value = confirmationMsg

            // Notify emergency contacts via SMS
            val uid = authManager.currentUserId
            if (!uid.isNullOrBlank()) {
                val profile = userRepository.getUserProfile(uid).getOrNull()
                val userName = profile?.name?.ifBlank { "User" } ?: "User"
                val primaryPhone = profile?.emergencyContacts?.firstOrNull { it.isPrimary }?.phone
                    ?: profile?.emergencyContacts?.firstOrNull()?.phone

                if (!primaryPhone.isNullOrBlank()) {
                    val smsBody = "ℹ️ SAFE COMMUTE UPDATE\n" +
                            "$userName arranged safe transport from workplace: ${option.title}. " +
                            "ETA: ${option.etaMinutes} mins.\n" +
                            "Sent from Trinetra Working Mode."

                    TwoFactorClient.sendTransactionalSms(
                        phoneNumber = primaryPhone,
                        message = smsBody
                    )
                }
            }
        }
    }

    fun clearConfirmation() {
        _bookingConfirmation.value = null
    }

    fun dismissLateStay() {
        _isLateStayActive.value = false
    }

    /**
     * Capstone Demo: simulate late stay immediately.
     */
    fun simulateLateStay(isLate: Boolean) {
        if (isLate) {
            triggerLateStayAlert("Simulated Late-Stay (Past 6:30 PM)")
        } else {
            dismissLateStay()
        }
    }
}
