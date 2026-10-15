package com.womensafety.system.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.womensafety.system.ui.auth.*
import com.womensafety.system.ui.contacts.AddEmergencyContactScreen
import com.womensafety.system.ui.contacts.AddUserScreen
import com.womensafety.system.ui.contacts.EditEmergencyContactScreen
import com.womensafety.system.ui.contacts.EmergencyContactsListScreen
import com.womensafety.system.ui.contacts.PermissionRequestScreen
import com.womensafety.system.ui.contacts.TrustedContactsScreen
import com.womensafety.system.ui.home.TCHomeScreen
import com.womensafety.system.ui.home.UserHomeScreen
import com.womensafety.system.ui.profile.ChangePhoneScreen
import com.womensafety.system.ui.profile.TCProfileScreen
import com.womensafety.system.ui.profile.UserProfileScreen
import com.womensafety.system.ui.safety.SafetyPlaceholderScreen
import com.womensafety.system.ui.setup.*
import com.womensafety.system.viewmodel.*

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    tcAuthViewModel: TrustedContactAuthViewModel = viewModel()
) {
    val sessionState by authViewModel.sessionState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Shared SetupWizardViewModel for all 5 setup steps
    val setupWizardViewModel: SetupWizardViewModel = viewModel()
    val emergencyContactViewModel: EmergencyContactViewModel = viewModel()
    val tcHomeViewModel: TrustedContactHomeViewModel = viewModel()
    val sharedSOSViewModel: SOSViewModel = viewModel()

    val isSafetyCheckActive by com.womensafety.system.manager.SafetyMonitoringManager.isSafetyCheckActive.collectAsState()
    LaunchedEffect(isSafetyCheckActive) {
        if (isSafetyCheckActive) {
            navController.navigate(ProactivePromptScreen) {
                launchSingleTop = true
            }
        }
    }

    val journeyState by com.womensafety.system.manager.TravelModeManager.journeyState.collectAsState()
    LaunchedEffect(journeyState) {
        if (journeyState == com.womensafety.system.manager.JourneyState.ACTIVE ||
            journeyState == com.womensafety.system.manager.JourneyState.DEVIATION_DETECTED ||
            journeyState == com.womensafety.system.manager.JourneyState.NO_RESPONSE
        ) {
            navController.navigate(ActiveJourney) {
                launchSingleTop = true
            }
        }
    }

    val activeEvent by com.womensafety.system.manager.SOSManager.activeEvent.collectAsState()
    LaunchedEffect(activeEvent) {
        activeEvent?.let { event ->
            if (event.status == com.womensafety.system.data.model.SOSStatus.ACTIVE && currentRoute?.contains("SOSAlertScreen") != true) {
                navController.navigate(SOSAlertScreen(eventId = event.id)) {
                    popUpTo(UserHome) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    // Automatically navigate back to SOSAlertScreen when returning from an active call or background state
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, lifecycleEvent ->
            if (lifecycleEvent == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val currentActive = com.womensafety.system.manager.SOSManager.activeEvent.value
                if (currentActive != null && currentActive.status == com.womensafety.system.data.model.SOSStatus.ACTIVE) {
                    val activeRoute = navController.currentBackStackEntry?.destination?.route
                    if (activeRoute?.contains("SOSAlertScreen") != true) {
                        navController.navigate(SOSAlertScreen(eventId = currentActive.id)) {
                            popUpTo(UserHome) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Determine if we should show bottom nav
    val userMainRoutes = listOf(
        UserHome::class.qualifiedName,
        UserSafety::class.qualifiedName,
        UserContacts::class.qualifiedName,
        UserProfile::class.qualifiedName
    )
    val tcMainRoutes = listOf(
        TCHome::class.qualifiedName,
        TCProfile::class.qualifiedName
    )

    val showUserBottomNav = userMainRoutes.any { currentRoute?.contains(it ?: "") == true }
    val showTCBottomNav = tcMainRoutes.any { currentRoute?.contains(it ?: "") == true }

    Scaffold(
        bottomBar = {
            when {
                showUserBottomNav -> UserBottomNavBar(navController)
                showTCBottomNav -> TCBottomNavBar(navController)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Splash,
            modifier = Modifier.padding(paddingValues)
        ) {
            // ─── Pre-Auth ────────────────────────────────────
            composable<Splash> {
                SplashScreen(
                    sessionState = sessionState,
                    onNavigateToRoleSelection = {
                        navController.navigate(RoleSelection) {
                            popUpTo(Splash) { inclusive = true }
                        }
                    },
                    onNavigateToUserHome = {
                        navController.navigate(UserHome) {
                            popUpTo(Splash) { inclusive = true }
                        }
                    },
                    onNavigateToWelcomeSetup = {
                        navController.navigate(WelcomeSetupScreen) {
                            popUpTo(Splash) { inclusive = true }
                        }
                    },
                    onNavigateToTCHome = {
                        navController.navigate(TCHome) {
                            popUpTo(Splash) { inclusive = true }
                        }
                    }
                )
            }

            composable<RoleSelection> {
                RoleSelectionScreen(
                    onUserSelected = { navController.navigate(UserLogin) },
                    onTrustedContactSelected = { navController.navigate(TCLogin) }
                )
            }

            // ─── User Auth ───────────────────────────────────
            composable<UserLogin> {
                UserLoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        navController.navigate(UserHome) {
                            popUpTo(RoleSelection) { inclusive = true }
                        }
                    },
                    onNavigateToSignup = { navController.navigate(UserSignupStep1) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<UserSignupStep1> {
                UserSignupStep1Screen(
                    viewModel = authViewModel,
                    onNext = { navController.navigate(UserSignupStep2) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<UserSignupStep2> {
                UserSignupStep2Screen(
                    viewModel = authViewModel,
                    onNext = { navController.navigate(UserSignupStep3) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<OTPVerification> { backStackEntry ->
                val args = backStackEntry.toRoute<OTPVerification>()
                OTPVerificationScreen(
                    phoneNumber = args.phoneNumber,
                    userName = args.userName,
                    onOTPVerified = {
                        authViewModel.onPhoneOTPVerified()
                        navController.navigate(UserSignupStep3) {
                            popUpTo(UserSignupStep2) { inclusive = true }
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable<UserSignupStep3> {
                UserSignupStep3Screen(
                    viewModel = authViewModel,
                    onNext = { navController.navigate(UserSignupStep4) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<UserSignupStep4> {
                UserSignupStep4Screen(
                    viewModel = authViewModel,
                    onNext = { navController.navigate(UserSignupStep5) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<UserSignupStep5> {
                UserSignupStep5Screen(
                    viewModel = authViewModel,
                    onAccountCreated = { navController.navigate(UserSignupStep6) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<UserSignupStep6> {
                UserSignupStep6Screen(
                    viewModel = authViewModel,
                    onContinueToHome = {
                        navController.navigate(WelcomeSetupScreen) {
                            popUpTo(RoleSelection) { inclusive = true }
                        }
                    }
                )
            }

            // ─── Initial Setup Wizard (5 Screens) ────────────
            composable<WelcomeSetupScreen> {
                WelcomeSetupScreen(
                    onGetStarted = {
                        navController.navigate(PermissionsSetupScreen)
                    },
                    onSkip = {
                        setupWizardViewModel.skipSetup {
                            navController.navigate(UserHome) {
                                popUpTo(RoleSelection) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable<PermissionsSetupScreen> {
                PermissionsSetupScreen(
                    viewModel = setupWizardViewModel,
                    onNext = {
                        navController.navigate(EmergencyContactsSetupScreen)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<EmergencyContactsSetupScreen> {
                EmergencyContactsSetupScreen(
                    viewModel = setupWizardViewModel,
                    onNext = {
                        navController.navigate(SOSPreferencesScreen)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SOSPreferencesScreen> {
                SOSPreferencesScreen(
                    viewModel = setupWizardViewModel,
                    onNext = {
                        navController.navigate(SetupCompleteScreen)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SetupCompleteScreen> {
                SetupCompleteScreen(
                    viewModel = setupWizardViewModel,
                    onNavigateToHome = {
                        navController.navigate(UserHome) {
                            popUpTo(RoleSelection) { inclusive = true }
                        }
                    }
                )
            }

            // ─── Emergency Contacts Management ───────────────
            composable<EmergencyContactsListScreen> {
                EmergencyContactsListScreen(
                    viewModel = emergencyContactViewModel,
                    onNavigateToAdd = { navController.navigate(AddEmergencyContactScreen) },
                    onNavigateToEdit = { contactId ->
                        navController.navigate(EditEmergencyContactScreen(contactId = contactId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AddEmergencyContactScreen> {
                AddEmergencyContactScreen(
                    viewModel = emergencyContactViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<EditEmergencyContactScreen> { backStackEntry ->
                val editRoute = backStackEntry.toRoute<EditEmergencyContactScreen>()
                EditEmergencyContactScreen(
                    contactId = editRoute.contactId,
                    viewModel = emergencyContactViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── TC Auth ─────────────────────────────────────
            composable<TCLogin> {
                TCLoginScreen(
                    viewModel = tcAuthViewModel,
                    onLoginSuccess = {
                        navController.navigate(TCHome) {
                            popUpTo(RoleSelection) { inclusive = true }
                        }
                    },
                    onNavigateToSignup = { navController.navigate(TCSignup) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<TCSignup> {
                TCSignupScreen(
                    viewModel = tcAuthViewModel,
                    onSignupSuccess = {
                        navController.navigate(TCHome) {
                            popUpTo(RoleSelection) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── User Main Screens ───────────────────────────
            composable<UserHome> {
                val homeViewModel: UserHomeViewModel = viewModel()
                UserHomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToLocationSettings = { navController.navigate(LocationSettingsScreen) },
                    onNavigateToShakeSettings = { navController.navigate(ShakeDetectionSettingsScreen) },
                    onNavigateToSafety = {
                        navController.navigate(UserSafety) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTravelMode = { navController.navigate(com.womensafety.system.ui.navigation.TravelMode) },
                    onNavigateToLiveMap = { targetId ->
                        navController.navigate(LiveLocationMapScreen(userId = targetId))
                    },
                    onNavigateToHeartRate = {
                        navController.navigate(HeartRateMonitoringScreen)
                    },
                    onNavigateToRoutine = {
                        navController.navigate(RoutineMonitoringScreen)
                    },
                    onNavigateToDiagnostic = {
                        navController.navigate(AccelerometerDiagnosticScreen)
                    },
                    onNavigateToBleDiagnostic = {
                        navController.navigate(BleDiscoveryTestScreen)
                    },
                    onNavigateToContacts = {
                        navController.navigate(UserContacts) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable<UserSafety> {
                val profileViewModel: UserProfileViewModel = viewModel()
                com.womensafety.system.ui.safety.SafetyScreen(
                    sosViewModel = sharedSOSViewModel,
                    profileViewModel = profileViewModel,
                    onNavigateToAlert = { eventId ->
                        navController.navigate(SOSAlertScreen(eventId = eventId)) {
                            popUpTo(UserHome) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToHistory = { navController.navigate(SOSHistoryScreen) },
                    onNavigateToHistoryDetail = { eventId ->
                        navController.navigate(SOSHistoryDetailScreen(eventId = eventId))
                    }
                )
            }

            composable<UserContacts> {
                val permissionViewModel: PermissionViewModel = viewModel()
                TrustedContactsScreen(
                    viewModel = permissionViewModel,
                    onNavigateToRequests = { navController.navigate(PermissionRequests) }
                )
            }

            composable<UserProfile> {
                val profileViewModel: UserProfileViewModel = viewModel()
                UserProfileScreen(
                    viewModel = profileViewModel,
                    onNavigateToChangePhone = { navController.navigate(ChangePhone) },
                    onNavigateToContacts = {
                        navController.navigate(UserContacts) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToEmergencyContacts = {
                        navController.navigate(EmergencyContactsListScreen)
                    },
                    onNavigateToSetupWizard = {
                        navController.navigate(WelcomeSetupScreen)
                    },
                    onNavigateToHeartRate = {
                        navController.navigate(HeartRateMonitoringScreen)
                    },
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigate(RoleSelection) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable<HeartRateMonitoringScreen> {
                val hrViewModel: HeartRateMonitoringViewModel = viewModel()
                com.womensafety.system.ui.health.HeartRateMonitoringScreen(
                    viewModel = hrViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<RoutineMonitoringScreen> {
                val routineViewModel: RoutineMonitoringViewModel = viewModel()
                com.womensafety.system.ui.safety.RoutineMonitoringScreen(
                    viewModel = routineViewModel,
                    onBack = { navController.popBackStack() },
                    onTriggerSOS = {
                        navController.navigate(UserSafety)
                    }
                )
            }

            composable<ChangePhone> {
                val profileViewModel: UserProfileViewModel = viewModel()
                ChangePhoneScreen(
                    viewModel = profileViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<PermissionRequests> {
                val permissionViewModel: PermissionViewModel = viewModel()
                PermissionRequestScreen(
                    viewModel = permissionViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── Location Tracking Screens ───────────────────
            composable<LocationSettingsScreen> {
                val locationViewModel: LocationViewModel = viewModel()
                com.womensafety.system.ui.location.LocationSettingsScreen(
                    viewModel = locationViewModel,
                    onNavigateToHistory = { navController.navigate(LocationHistoryScreen) },
                    onNavigateToDiagnostics = { navController.navigate(LocationTestScreen) },
                    onNavigateToMapTest = { navController.navigate(MapTestScreen) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<LocationTestScreen> {
                val locationViewModel: LocationViewModel = viewModel()
                val currentUserId = locationViewModel.currentUserId ?: ""
                com.womensafety.system.ui.location.LocationTestScreen(
                    currentUserId = currentUserId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<MapTestScreen> {
                com.womensafety.system.ui.location.MapTestScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable<LocationHistoryScreen> {
                val locationViewModel: LocationViewModel = viewModel()
                com.womensafety.system.ui.location.LocationHistoryScreen(
                    viewModel = locationViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<LiveLocationMapScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<LiveLocationMapScreen>()
                val locationViewModel: LocationViewModel = viewModel()
                com.womensafety.system.ui.location.LiveLocationMapScreen(
                    userId = route.userId,
                    viewModel = locationViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── Shake Detection Screens ─────────────────────
            composable<ShakeDetectionSettingsScreen> {
                val shakeViewModel: ShakeDetectionViewModel = viewModel()
                com.womensafety.system.ui.shake.ShakeDetectionSettingsScreen(
                    viewModel = shakeViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── Emergency SOS Screens ───────────────────────
            composable<SOSAlertScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<SOSAlertScreen>()
                com.womensafety.system.ui.sos.SOSAlertScreen(
                    eventId = route.eventId,
                    viewModel = sharedSOSViewModel,
                    onNavigateToHome = {
                        navController.navigate(UserHome) {
                            popUpTo(UserHome) { inclusive = true }
                        }
                    }
                )
            }

            composable<SOSHistoryScreen> {
                com.womensafety.system.ui.sos.SOSHistoryScreen(
                    viewModel = sharedSOSViewModel,
                    onNavigateToDetail = { eventId ->
                        navController.navigate(SOSHistoryDetailScreen(eventId = eventId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SOSHistoryDetailScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<SOSHistoryDetailScreen>()
                com.womensafety.system.ui.sos.SOSHistoryDetailScreen(
                    eventId = route.eventId,
                    viewModel = sharedSOSViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SOSReceivedScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<SOSReceivedScreen>()
                val locationViewModel: LocationViewModel = viewModel()
                val sosViewModel: SOSViewModel = viewModel()
                com.womensafety.system.ui.sos.SOSReceivedScreen(
                    eventId = route.eventId,
                    userId = route.userId,
                    locationViewModel = locationViewModel,
                    sosViewModel = sosViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SOSEventMapScreen> { backStackEntry ->
                val route = backStackEntry.toRoute<SOSEventMapScreen>()
                val sosViewModel: SOSViewModel = viewModel()
                com.womensafety.system.ui.sos.SOSEventMapScreen(
                    eventId = route.eventId,
                    viewModel = sosViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<ProactivePromptScreen> {
                val safetyViewModel: com.womensafety.system.viewmodel.SafetyMonitoringViewModel = viewModel()
                com.womensafety.system.ui.safety.AreYouSafeScreen(
                    viewModel = safetyViewModel,
                    onNavigateToAlert = { eventId ->
                        navController.navigate(com.womensafety.system.ui.navigation.SOSAlertScreen(eventId = eventId)) {
                            popUpTo(com.womensafety.system.ui.navigation.UserHome) { inclusive = false }
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate(com.womensafety.system.ui.navigation.UserHome) {
                            popUpTo(com.womensafety.system.ui.navigation.UserHome) { inclusive = true }
                        }
                    }
                )
            }

            composable<TravelMode> {
                val travelViewModel: com.womensafety.system.viewmodel.TravelViewModel = viewModel()
                com.womensafety.system.ui.travel.TravelModeScreen(
                    viewModel = travelViewModel,
                    onNavigateToActiveJourney = {
                        navController.navigate(com.womensafety.system.ui.navigation.ActiveJourney) {
                            popUpTo(com.womensafety.system.ui.navigation.TravelMode) { inclusive = false }
                        }
                    },
                    onNavigateToHistory = {
                        navController.navigate(com.womensafety.system.ui.navigation.TravelHistory)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<ActiveJourney> {
                val travelViewModel: com.womensafety.system.viewmodel.TravelViewModel = viewModel()
                com.womensafety.system.ui.travel.ActiveJourneyScreen(
                    viewModel = travelViewModel,
                    onNavigateToHome = {
                        navController.navigate(com.womensafety.system.ui.navigation.UserHome) {
                            popUpTo(com.womensafety.system.ui.navigation.UserHome) { inclusive = true }
                        }
                    }
                )
            }

            composable<TravelHistory> {
                val travelViewModel: com.womensafety.system.viewmodel.TravelViewModel = viewModel()
                com.womensafety.system.ui.travel.TravelHistoryScreen(
                    viewModel = travelViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<RoutineMonitoringScreen> {
                val routineViewModel: com.womensafety.system.viewmodel.RoutineMonitoringViewModel = viewModel()
                val context = androidx.compose.ui.platform.LocalContext.current
                com.womensafety.system.ui.safety.RoutineMonitoringScreen(
                    viewModel = routineViewModel,
                    onBack = { navController.popBackStack() },
                    onTriggerSOS = {
                        sharedSOSViewModel.triggerSOS(
                            context = context,
                            method = com.womensafety.system.data.model.SOSTriggerMethod.BUTTON
                        ) { eventId ->
                            navController.navigate(SOSAlertScreen(eventId))
                        }
                    }
                )
            }

            composable<AccelerometerDiagnosticScreen> {
                com.womensafety.system.ui.safety.AccelerometerDiagnosticScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable<BleDiscoveryTestScreen> {
                com.womensafety.system.ui.safety.BleDiscoveryTestScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ─── TC Main Screens ─────────────────────────────
            composable<TCHome> {
                TCHomeScreen(
                    viewModel = tcHomeViewModel,
                    onNavigateToAddUser = { navController.navigate(AddUser) },
                    onNavigateToLiveMap = { userId ->
                        navController.navigate(LiveLocationMapScreen(userId = userId))
                    }
                )
            }

            composable<AddUser> {
                AddUserScreen(
                    viewModel = tcHomeViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<TCProfile> {
                TCProfileScreen(
                    viewModel = tcHomeViewModel,
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigate(RoleSelection) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

