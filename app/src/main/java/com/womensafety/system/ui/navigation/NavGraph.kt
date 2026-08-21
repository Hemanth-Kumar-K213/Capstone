package com.womensafety.system.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
                UserHomeScreen(viewModel = homeViewModel)
            }

            composable<UserSafety> {
                SafetyPlaceholderScreen()
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
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigate(RoleSelection) {
                            popUpTo(0) { inclusive = true }
                        }
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

            // ─── TC Main Screens ─────────────────────────────
            composable<TCHome> {
                val tcHomeViewModel: TrustedContactHomeViewModel = viewModel()
                TCHomeScreen(
                    viewModel = tcHomeViewModel,
                    onNavigateToAddUser = { navController.navigate(AddUser) }
                )
            }

            composable<AddUser> {
                val tcHomeViewModel: TrustedContactHomeViewModel = viewModel()
                AddUserScreen(
                    viewModel = tcHomeViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<TCProfile> {
                val tcHomeViewModel: TrustedContactHomeViewModel = viewModel()
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

