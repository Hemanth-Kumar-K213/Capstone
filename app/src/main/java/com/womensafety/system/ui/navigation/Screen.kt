package com.womensafety.system.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the app.
 */

// ─── Pre-Auth Routes ─────────────────────────────────────────────
@Serializable object Splash
@Serializable object RoleSelection

// ─── User Auth Routes ────────────────────────────────────────────
@Serializable object UserLogin
@Serializable object UserSignupStep1
@Serializable object UserSignupStep2
@Serializable data class OTPVerification(val phoneNumber: String, val userName: String)
@Serializable object UserSignupStep3
@Serializable object UserSignupStep4
@Serializable object UserSignupStep5
@Serializable object UserSignupStep6

// ─── Trusted Contact Auth Routes ─────────────────────────────────
@Serializable object TCLogin
@Serializable object TCSignup

// ─── User Main Routes ────────────────────────────────────────────
@Serializable object UserHome
@Serializable object UserSafety
@Serializable object UserContacts
@Serializable object UserProfile
@Serializable object ChangePhone
@Serializable object PermissionRequests
@Serializable object HeartRateMonitoringScreen
@Serializable object RoutineMonitoringScreen

// ─── User Setup Wizard Routes ────────────────────────────────────
@Serializable object WelcomeSetupScreen
@Serializable object PermissionsSetupScreen
@Serializable object EmergencyContactsSetupScreen
@Serializable object SOSPreferencesScreen
@Serializable object SetupCompleteScreen

// ─── Emergency Contacts Management Routes ─────────────────────────
@Serializable object EmergencyContactsListScreen
@Serializable object AddEmergencyContactScreen
@Serializable data class EditEmergencyContactScreen(val contactId: String)

// ─── Location Tracking Routes ────────────────────────────────────
@Serializable object LocationSettingsScreen
@Serializable object LocationHistoryScreen
@Serializable data class LiveLocationMapScreen(val userId: String)
@Serializable object LocationTestScreen
@Serializable object MapTestScreen

// ─── Shake Detection Routes ──────────────────────────────────────
@Serializable object ShakeDetectionSettingsScreen

// ─── Emergency SOS Alert Routes ──────────────────────────────────
@Serializable data class SOSAlertScreen(val eventId: String)
@Serializable object SOSHistoryScreen
@Serializable data class SOSHistoryDetailScreen(val eventId: String)
@Serializable data class SOSReceivedScreen(val eventId: String, val userId: String)
@Serializable data class SOSEventMapScreen(val eventId: String)
@Serializable object ProactivePromptScreen
@Serializable object TravelMode
@Serializable object ActiveJourney
@Serializable object TravelHistory

// ─── Trusted Contact Main Routes ─────────────────────────────────
@Serializable object TCHome
@Serializable object AddUser
@Serializable object TCProfile


