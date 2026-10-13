package com.womensafety.system.ui.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.UserProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    onNavigateToChangePhone: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToEmergencyContacts: () -> Unit,
    onNavigateToSetupWizard: () -> Unit,
    onNavigateToHeartRate: () -> Unit = {},
    onLogout: () -> Unit
) {
    val state by viewModel.profileState.collectAsState()
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    var sensitivityDropdownExpanded by remember { mutableStateOf(false) }
    val sensitivityOptions = listOf("LOW", "MEDIUM", "HIGH")

    var modeDropdownExpanded by remember { mutableStateOf(false) }
    val modeOptions = listOf("NORMAL", "CAUTIOUS", "HIGH_RISK")

    var toleranceDropdownExpanded by remember { mutableStateOf(false) }
    val toleranceOptions = listOf(100, 200, 300, 500)

    val profile = state.userProfile

    // Location coordinates local edit state
    var workplaceLatText by remember(profile?.savedWorkplaceLatitude) {
        mutableStateOf(profile?.savedWorkplaceLatitude?.toString() ?: "")
    }
    var workplaceLngText by remember(profile?.savedWorkplaceLongitude) {
        mutableStateOf(profile?.savedWorkplaceLongitude?.toString() ?: "")
    }
    var workplaceAddrText by remember(profile?.savedWorkplaceAddress) {
        mutableStateOf(profile?.savedWorkplaceAddress ?: "")
    }

    var homeLatText by remember(profile?.savedHomeLatitude) {
        mutableStateOf(profile?.savedHomeLatitude?.toString() ?: "")
    }
    var homeLngText by remember(profile?.savedHomeLongitude) {
        mutableStateOf(profile?.savedHomeLongitude?.toString() ?: "")
    }
    var homeAddrText by remember(profile?.savedHomeAddress) {
        mutableStateOf(profile?.savedHomeAddress ?: "")
    }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Sign Out",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out? Background safety monitoring services will pause until you sign back in.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Sign Out", color = EmergencyRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (state.isLoading && state.userProfile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
        return
    }

    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF131627), BackgroundDark)
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFEEF2FF), BackgroundLight)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // ─── 1. Hero Identity Header ───────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryIndigo, SecondaryViolet)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (profile?.name?.firstOrNull()?.uppercase() ?: "U"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = profile?.name ?: "User Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = StatusApproved,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = profile?.primaryEmail ?: "Protected Account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Feedback Banners
        SuccessMessage(state.successMessage)
        if (state.successMessage != null) Spacer(modifier = Modifier.height(8.dp))

        ErrorMessage(state.error)
        if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

        // ─── 2. Group 1: Personal Details & Credentials ────────────
        SettingsCardGroup(
            title = "Personal Details & Security",
            subtitle = "Identity, primary contact and authentication"
        ) {
            ProfileInfoRow(
                label = "Full Name",
                value = profile?.name?.ifBlank { "—" } ?: "—",
                icon = Icons.Filled.Badge
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            ProfileInfoRow(
                label = "Email Address",
                value = profile?.primaryEmail?.ifBlank { "—" } ?: "—",
                icon = Icons.Filled.Email
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            ProfileInfoRow(
                label = "Phone Number",
                value = profile?.phone?.ifBlank { "—" } ?: "—",
                icon = Icons.Filled.Phone
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            SettingsActionRow(
                title = "Change Phone Number",
                subtitle = "Update emergency SMS alert sender",
                actionText = "Change",
                onClick = onNavigateToChangePhone,
                icon = Icons.Filled.PhoneIphone
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            SettingsActionRow(
                title = "Reset Password",
                subtitle = "Send password reset instructions to email",
                actionText = "Send Link",
                onClick = { viewModel.sendPasswordReset() },
                icon = Icons.Filled.LockReset
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ─── 3. Group 2: Automated Safety Preferences ──────────────
        SettingsCardGroup(
            title = "Automated Safety Triggers",
            subtitle = "Continuous sensors, auto-call and anomaly rules"
        ) {
            SettingsToggleRow(
                title = "Background Location Tracking",
                subtitle = "Broadcast real-time GPS coordinates during SOS",
                checked = profile?.locationTrackingEnabled ?: false,
                icon = Icons.Filled.LocationOn,
                onCheckedChange = { isChecked ->
                    viewModel.updateSafetyPreferences(
                        locationTrackingEnabled = isChecked,
                        shakeDetectionEnabled = profile?.shakeDetectionEnabled ?: false,
                        shakeSensitivity = profile?.shakeSensitivity ?: "MEDIUM",
                        sosAutoCall = profile?.sosAutoCall ?: false,
                        proactiveMonitoringEnabled = profile?.proactiveMonitoringEnabled ?: false,
                        safetyMode = profile?.safetyMode ?: "NORMAL",
                        routeDeviationToleranceMeters = profile?.routeDeviationToleranceMeters ?: 300
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            SettingsToggleRow(
                title = "Shake-to-SOS Detection",
                subtitle = "Vigorously shake device to trigger emergency dispatch",
                checked = profile?.shakeDetectionEnabled ?: false,
                icon = Icons.Filled.Vibration,
                onCheckedChange = { isChecked ->
                    viewModel.updateSafetyPreferences(
                        locationTrackingEnabled = profile?.locationTrackingEnabled ?: false,
                        shakeDetectionEnabled = isChecked,
                        shakeSensitivity = profile?.shakeSensitivity ?: "MEDIUM",
                        sosAutoCall = profile?.sosAutoCall ?: false,
                        proactiveMonitoringEnabled = profile?.proactiveMonitoringEnabled ?: false,
                        safetyMode = profile?.safetyMode ?: "NORMAL",
                        routeDeviationToleranceMeters = profile?.routeDeviationToleranceMeters ?: 300
                    )
                }
            )

            if (profile?.shakeDetectionEnabled == true) {
                Spacer(modifier = Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = sensitivityDropdownExpanded,
                    onExpandedChange = { sensitivityDropdownExpanded = !sensitivityDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = profile.shakeSensitivity,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Shake Sensitivity Level") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sensitivityDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = sensitivityDropdownExpanded,
                        onDismissRequest = { sensitivityDropdownExpanded = false }
                    ) {
                        sensitivityOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    viewModel.updateSafetyPreferences(
                                        locationTrackingEnabled = profile.locationTrackingEnabled,
                                        shakeDetectionEnabled = profile.shakeDetectionEnabled,
                                        shakeSensitivity = option,
                                        sosAutoCall = profile.sosAutoCall,
                                        proactiveMonitoringEnabled = profile.proactiveMonitoringEnabled,
                                        safetyMode = profile.safetyMode,
                                        routeDeviationToleranceMeters = profile.routeDeviationToleranceMeters
                                    )
                                    sensitivityDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            SettingsToggleRow(
                title = "Auto-Call Primary Contact",
                subtitle = "Place cellular phone call immediately on SOS dispatch",
                checked = profile?.sosAutoCall ?: false,
                icon = Icons.Filled.PhoneInTalk,
                onCheckedChange = { isChecked ->
                    viewModel.updateSafetyPreferences(
                        locationTrackingEnabled = profile?.locationTrackingEnabled ?: false,
                        shakeDetectionEnabled = profile?.shakeDetectionEnabled ?: false,
                        shakeSensitivity = profile?.shakeSensitivity ?: "MEDIUM",
                        sosAutoCall = isChecked,
                        proactiveMonitoringEnabled = profile?.proactiveMonitoringEnabled ?: false,
                        safetyMode = profile?.safetyMode ?: "NORMAL",
                        routeDeviationToleranceMeters = profile?.routeDeviationToleranceMeters ?: 300
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            SettingsToggleRow(
                title = "Proactive Journey Monitoring",
                subtitle = "Auto-detect route deviations & sudden inactivity",
                checked = profile?.proactiveMonitoringEnabled ?: false,
                icon = Icons.Filled.TrackChanges,
                onCheckedChange = { isChecked ->
                    viewModel.updateSafetyPreferences(
                        locationTrackingEnabled = profile?.locationTrackingEnabled ?: false,
                        shakeDetectionEnabled = profile?.shakeDetectionEnabled ?: false,
                        shakeSensitivity = profile?.shakeSensitivity ?: "MEDIUM",
                        sosAutoCall = profile?.sosAutoCall ?: false,
                        proactiveMonitoringEnabled = isChecked,
                        safetyMode = profile?.safetyMode ?: "NORMAL",
                        routeDeviationToleranceMeters = profile?.routeDeviationToleranceMeters ?: 300
                    )
                }
            )

            if (profile?.proactiveMonitoringEnabled == true) {
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = modeDropdownExpanded,
                    onExpandedChange = { modeDropdownExpanded = !modeDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = profile.safetyMode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Safety Mode Multiplier") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = modeDropdownExpanded,
                        onDismissRequest = { modeDropdownExpanded = false }
                    ) {
                        modeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    viewModel.updateSafetyPreferences(
                                        locationTrackingEnabled = profile.locationTrackingEnabled,
                                        shakeDetectionEnabled = profile.shakeDetectionEnabled,
                                        shakeSensitivity = profile.shakeSensitivity,
                                        sosAutoCall = profile.sosAutoCall,
                                        proactiveMonitoringEnabled = profile.proactiveMonitoringEnabled,
                                        safetyMode = option,
                                        routeDeviationToleranceMeters = profile.routeDeviationToleranceMeters
                                    )
                                    modeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = toleranceDropdownExpanded,
                    onExpandedChange = { toleranceDropdownExpanded = !toleranceDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = "${profile.routeDeviationToleranceMeters} meters",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Route Deviation Tolerance") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toleranceDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = toleranceDropdownExpanded,
                        onDismissRequest = { toleranceDropdownExpanded = false }
                    ) {
                        toleranceOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("$option meters") },
                                onClick = {
                                    viewModel.updateSafetyPreferences(
                                        locationTrackingEnabled = profile.locationTrackingEnabled,
                                        shakeDetectionEnabled = profile.shakeDetectionEnabled,
                                        shakeSensitivity = profile.shakeSensitivity,
                                        sosAutoCall = profile.sosAutoCall,
                                        proactiveMonitoringEnabled = profile.proactiveMonitoringEnabled,
                                        safetyMode = profile.safetyMode,
                                        routeDeviationToleranceMeters = option
                                    )
                                    toleranceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ─── 4. Group: Smartwatch & Wearables ──────────────────────
        SettingsCardGroup(
            title = "Smartwatch & Wearables",
            subtitle = "Bluetooth LE GATT 0x180D & Health Connect telemetry"
        ) {
            SettingsActionRow(
                title = "Smartwatch & Heart Rate Monitoring",
                subtitle = "Direct BLE watch connection, stress anomaly detection & Capstone harness",
                actionText = "Open",
                onClick = onNavigateToHeartRate,
                icon = Icons.Filled.Favorite
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ─── 5. Group 3: Saved Safe Places (Geofencing) ────────────
        SettingsCardGroup(
            title = "Saved Safe Locations",
            subtitle = "Coordinates for automated arrival & departure tracking"
        ) {
            Text(
                text = "Workplace / College",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = workplaceLatText,
                    onValueChange = { workplaceLatText = it },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = workplaceLngText,
                    onValueChange = { workplaceLngText = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = workplaceAddrText,
                onValueChange = { workplaceAddrText = it },
                label = { Text("Workplace Address (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            Text(
                text = "Home Residence",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = homeLatText,
                    onValueChange = { homeLatText = it },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = homeLngText,
                    onValueChange = { homeLngText = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = homeAddrText,
                onValueChange = { homeAddrText = it },
                label = { Text("Home Address (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            PrimaryButton(
                text = "Save Location Coordinates",
                onClick = {
                    val wLat = workplaceLatText.toDoubleOrNull()
                    val wLng = workplaceLngText.toDoubleOrNull()
                    val hLat = homeLatText.toDoubleOrNull()
                    val hLng = homeLngText.toDoubleOrNull()

                    viewModel.updateSafetyPreferences(
                        savedWorkplaceLatitude = wLat,
                        savedWorkplaceLongitude = wLng,
                        savedWorkplaceAddress = workplaceAddrText.takeIf { it.isNotBlank() },
                        savedHomeLatitude = hLat,
                        savedHomeLongitude = hLng,
                        savedHomeAddress = homeAddrText.takeIf { it.isNotBlank() }
                    )
                },
                icon = Icons.Filled.Save
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ─── 5. Group 4: Safety Mesh & Emergency UID ───────────────
        SettingsCardGroup(
            title = "Safety Mesh & Identity",
            subtitle = "Active guardians, SMS contacts and rotating security UID"
        ) {
            // Rotating UID Display
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CURRENT SAFETY UID",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.currentUid?.uid ?: "Generating…",
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 2.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.currentUid?.isCurrentlyActive == true) "Active • " else "Inactive • ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.currentUid?.isCurrentlyActive == true) StatusApproved else EmergencyRed
                    )
                    Text(
                        text = if (state.currentUid != null) DateUtils.getTimeUntilExpiry(state.currentUid!!.expiresAt) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryButton(
                    text = "Share Safety UID",
                    onClick = {
                        val uid = state.currentUid?.uid ?: return@PrimaryButton
                        val shareText = "My current Trinetra Safety UID is: $uid"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Current UID"))
                    },
                    enabled = state.currentUid?.isCurrentlyActive == true,
                    icon = Icons.Filled.Share
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            SettingsActionRow(
                title = "Emergency Contacts (SMS)",
                subtitle = "${state.emergencyContactCount} contact(s) receive direct SMS alert",
                actionText = "Manage",
                onClick = onNavigateToEmergencyContacts,
                icon = Icons.Filled.ContactPhone
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            SettingsActionRow(
                title = "Trusted Guardians",
                subtitle = "${state.trustedContactCount} paired guardian(s) with live tracking",
                actionText = if (state.pendingRequestCount > 0) "${state.pendingRequestCount} Pending" else "Manage",
                onClick = onNavigateToContacts,
                icon = Icons.Filled.Shield
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ─── 6. Group 5: Setup & Session Operations ────────────────
        SettingsCardGroup(
            title = "App & Session Operations"
        ) {
            SettingsActionRow(
                title = "Re-Run Setup Wizard",
                subtitle = "Re-validate permissions and emergency setup",
                onClick = onNavigateToSetupWizard,
                icon = Icons.Filled.SettingsBackupRestore
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showLogoutDialog = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = EmergencyRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sign Out of Trinetra",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmergencyRed
                    )
                    Text(
                        text = "Pause automated background safety monitoring",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Generous bottom clearance
        Spacer(modifier = Modifier.height(64.dp))
    }
}
