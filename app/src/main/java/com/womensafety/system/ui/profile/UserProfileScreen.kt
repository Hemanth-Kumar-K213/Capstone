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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onLogout: () -> Unit
) {
    val state by viewModel.profileState.collectAsState()
    val context = LocalContext.current
    val profile = state.userProfile

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showMedicalInfoDialog by remember { mutableStateOf(false) }
    var showAccessDialog by remember { mutableStateOf(false) }
    var showPreferencesDialog by remember { mutableStateOf(false) }
    var showDemosDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    // ─── Sign Out Dialog ─────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign out", color = SafetyTextPrimary) },
            text = { Text("Are you sure you want to sign out of your account?", color = SafetyTextSecondary) },
            containerColor = SafetyCardBackground,
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text("Sign out", color = StatusDenied, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = SafetyTextMuted)
                }
            }
        )
    }

    var isEditingPersonalInfo by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editHomeAddress by remember { mutableStateOf("") }
    var editWorkAddress by remember { mutableStateOf("") }

    LaunchedEffect(profile, showMedicalInfoDialog) {
        if (showMedicalInfoDialog && profile != null) {
            editName = profile.name
            editHomeAddress = profile.savedHomeAddress ?: ""
            editWorkAddress = profile.savedWorkplaceAddress ?: ""
            isEditingPersonalInfo = false
        }
    }

    // ─── Personal Information Dialog ─────────────────────────
    if (showMedicalInfoDialog) {
        AlertDialog(
            onDismissRequest = {
                showMedicalInfoDialog = false
                isEditingPersonalInfo = false
            },
            containerColor = SafetyCardBackground,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Personal information",
                        color = SafetyTextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp
                    )
                    TextButton(onClick = { isEditingPersonalInfo = !isEditingPersonalInfo }) {
                        Text(
                            text = if (isEditingPersonalInfo) "Cancel" else "Edit",
                            color = SafetyPrimaryBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Personal details associated with your Trinetra safety account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isEditingPersonalInfo) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = editHomeAddress,
                            onValueChange = { editHomeAddress = it },
                            label = { Text("Saved Home Location") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = editWorkAddress,
                            onValueChange = { editWorkAddress = it },
                            label = { Text("Saved Work Location") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Phone and Email changes require security verification below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextMuted
                        )
                    } else {
                        ProfileInfoRow(label = "Full Name", value = profile?.name ?: "—")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SafetyCardDivider)

                        ProfileInfoRow(label = "Primary Phone", value = profile?.phone ?: "—")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SafetyCardDivider)

                        ProfileInfoRow(label = "Account Email", value = profile?.primaryEmail ?: "—")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SafetyCardDivider)

                        ProfileInfoRow(label = "Emergency Contact Mode", value = profile?.safetyMode ?: "NORMAL")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SafetyCardDivider)

                        ProfileInfoRow(label = "Saved Home Location", value = profile?.savedHomeAddress?.ifBlank { "Not set" } ?: "Not set")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SafetyCardDivider)

                        ProfileInfoRow(label = "Saved Work Location", value = profile?.savedWorkplaceAddress?.ifBlank { "Not set" } ?: "Not set")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            showMedicalInfoDialog = false
                            onNavigateToChangePhone()
                        }) {
                            Text("Change Phone", color = SafetyPrimaryBlue)
                        }
                        TextButton(onClick = { viewModel.sendPasswordReset() }) {
                            Text("Reset Password", color = SafetyPrimaryBlue)
                        }
                    }
                }
            },
            confirmButton = {
                if (isEditingPersonalInfo) {
                    SafetyPillButton(
                        text = "Save Changes",
                        onClick = {
                            viewModel.updatePersonalInformation(
                                name = editName.trim(),
                                homeAddress = editHomeAddress.trim(),
                                workAddress = editWorkAddress.trim()
                            )
                            isEditingPersonalInfo = false
                            showMedicalInfoDialog = false
                        }
                    )
                } else {
                    SafetyPillButton(
                        text = "Done",
                        onClick = { showMedicalInfoDialog = false }
                    )
                }
            }
        )
    }

    // ─── Current UID Dialog (UID & Sharing) ───────────────────
    if (showAccessDialog) {
        val uid = state.currentUid?.uid ?: ""
        AlertDialog(
            onDismissRequest = { showAccessDialog = false },
            containerColor = SafetyCardBackground,
            title = {
                Text(
                    "Current UID",
                    color = SafetyTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Your Safety UID lets authorized Trusted Contacts monitor your status during emergencies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16181A))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "CURRENT SAFETY UID",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                                color = SafetyTextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (uid.isNotBlank()) uid else "Generating…",
                                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
                                fontWeight = FontWeight.Bold,
                                color = SafetyPrimaryBlue
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Auto-rotates weekly • Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusApproved
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        "${state.trustedContactCount} trusted contact(s) linked to your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                if (uid.isNotBlank()) {
                                    val shareText = "My current Trinetra Safety UID is: $uid"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Safety UID"))
                                }
                            }
                        ) {
                            Text("Share UID", color = SafetyPrimaryBlue, fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = {
                                showAccessDialog = false
                                onNavigateToContacts()
                            }
                        ) {
                            Text("Manage Trusted Contacts", color = SafetyPrimaryBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                SafetyPillButton(text = "Close", onClick = { showAccessDialog = false })
            }
        )
    }

    // ─── Safety Preferences Dialog ───────────────────────────
    if (showPreferencesDialog) {
        var sensitivityDropdownExpanded by remember { mutableStateOf(false) }
        val sensitivityOptions = listOf("LOW", "MEDIUM", "HIGH")

        AlertDialog(
            onDismissRequest = { showPreferencesDialog = false },
            containerColor = SafetyCardBackground,
            title = {
                Text(
                    "Safety Preferences",
                    color = SafetyTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Location Tracking switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Location Tracking", style = MaterialTheme.typography.bodyMedium, color = SafetyTextPrimary)
                            Text("Share coordinates during SOS", style = MaterialTheme.typography.bodySmall, color = SafetyTextMuted)
                        }
                        Switch(
                            checked = profile?.locationTrackingEnabled ?: false,
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
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SafetyCardDivider)

                    // Shake Detection switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Shake Detection", style = MaterialTheme.typography.bodyMedium, color = SafetyTextPrimary)
                            Text("Shake vigorously to trigger SOS", style = MaterialTheme.typography.bodySmall, color = SafetyTextMuted)
                        }
                        Switch(
                            checked = profile?.shakeDetectionEnabled ?: false,
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
                    }

                    if (profile?.shakeDetectionEnabled == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = sensitivityDropdownExpanded,
                            onExpandedChange = { sensitivityDropdownExpanded = !sensitivityDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = profile.shakeSensitivity,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Shake Sensitivity") },
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
                                sensitivityOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        onClick = {
                                            viewModel.updateSafetyPreferences(
                                                locationTrackingEnabled = profile.locationTrackingEnabled,
                                                shakeDetectionEnabled = profile.shakeDetectionEnabled,
                                                shakeSensitivity = opt,
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SafetyCardDivider)

                    // Auto-Call First Emergency Contact
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Call First Contact", style = MaterialTheme.typography.bodyMedium, color = SafetyTextPrimary)
                            Text("Place phone call automatically on SOS", style = MaterialTheme.typography.bodySmall, color = SafetyTextMuted)
                        }
                        Switch(
                            checked = profile?.sosAutoCall ?: false,
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
                    }
                }
            },
            confirmButton = {
                SafetyPillButton(text = "Save", onClick = { showPreferencesDialog = false })
            }
        )
    }

    // ─── Demos Dialog ────────────────────────────────────────
    if (showDemosDialog) {
        AlertDialog(
            onDismissRequest = { showDemosDialog = false },
            containerColor = SafetyCardBackground,
            title = {
                Text(
                    "Safety Demos",
                    color = SafetyTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Test safety systems in simulated environment without contacting emergency services.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    SafetyListRow(
                        title = "Emergency SOS Demo",
                        subtitle = "Test countdown and siren simulation",
                        icon = Icons.Filled.CrisisAlert,
                        onClick = {
                            showDemosDialog = false
                            /* Can trigger simulation */
                        }
                    )
                    HorizontalDivider(color = SafetyCardDivider)

                    SafetyListRow(
                        title = "Safety Check Demo",
                        subtitle = "Experience the check-in timer notification",
                        icon = Icons.Filled.Timer,
                        onClick = {
                            showDemosDialog = false
                        }
                    )
                    HorizontalDivider(color = SafetyCardDivider)

                    SafetyListRow(
                        title = "Shake Sensor Test",
                        subtitle = "Calibrate device accelerometer response",
                        icon = Icons.Filled.Vibration,
                        onClick = {
                            showDemosDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                SafetyPillButton(text = "Close", onClick = { showDemosDialog = false })
            }
        )
    }

    // ─── Feedback Dialog ─────────────────────────────────────
    if (showFeedbackDialog) {
        var feedbackText by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            containerColor = SafetyCardBackground,
            title = { Text("Send feedback", color = SafetyTextPrimary) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Tell us about your experience with Trinetra Women Safety System. Your feedback will be saved to our system to help improve user safety.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("Describe your feedback or suggestion...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SafetyTextPrimary,
                            unfocusedTextColor = SafetyTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                SafetyPillButton(
                    text = if (isSubmitting) "Submitting..." else "Submit",
                    onClick = {
                        if (feedbackText.isNotBlank() && !isSubmitting) {
                            isSubmitting = true
                            viewModel.submitFeedback(feedbackText) { success, errMsg ->
                                isSubmitting = false
                                showFeedbackDialog = false
                                val msg = if (success) {
                                    "✓ Feedback submitted to Firestore successfully!"
                                } else {
                                    "Feedback notice: ${errMsg ?: "Check internet/permissions"}"
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    msg,
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } else if (feedbackText.isBlank()) {
                            showFeedbackDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel", color = SafetyTextMuted)
                }
            }
        )
    }

    if (state.isLoading) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SafetyDarkBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = SafetyPrimaryBlue)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SafetyDarkBackground)
    ) {
        // Top App Bar matching Image 1
        SafetyTopAppBar(
            title = "Your info",
            onNotificationClick = { /* Notifications */ },
            onProfileClick = { /* Profile */ }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SuccessMessage(state.successMessage)
            if (state.successMessage != null) Spacer(modifier = Modifier.height(8.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

            // ─── Group 1: Emergency info (Exact match to Image 1) ────
            SafetyListGroup(title = "Emergency info") {
                // Row 1: Personal information
                SafetyListRow(
                    title = "Personal information",
                    icon = Icons.Outlined.AssignmentInd,
                    onClick = { showMedicalInfoDialog = true }
                )

                HorizontalDivider(color = SafetyCardDivider)

                // Row 2: Emergency contacts
                SafetyListRow(
                    title = "Emergency contacts",
                    icon = Icons.Outlined.Badge,
                    onClick = onNavigateToEmergencyContacts
                )

                HorizontalDivider(color = SafetyCardDivider)

                // Row 3: Current UID
                SafetyListRow(
                    title = "Current UID",
                    icon = Icons.Outlined.Visibility,
                    onClick = { showAccessDialog = true }
                )

                HorizontalDivider(color = SafetyCardDivider)

                // Row 4: Safety preferences
                SafetyListRow(
                    title = "Safety preferences",
                    icon = Icons.Outlined.Tune,
                    onClick = { showPreferencesDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Group 2: Your videos (Exact match to Image 1) ───────
            SafetyListGroup(title = "Your videos") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No emergency videos",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = SafetyTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Group 3: Help & support (Exact match to Image 1) ────
            SafetyListGroup(title = "Help & support") {
                // Row 1: Demos
                SafetyListRow(
                    title = "Demos",
                    icon = Icons.Outlined.TipsAndUpdates,
                    onClick = { showDemosDialog = true }
                )

                HorizontalDivider(color = SafetyCardDivider)

                // Row 2: Help
                SafetyListRow(
                    title = "Help",
                    icon = Icons.Outlined.HelpOutline,
                    onClick = onNavigateToSetupWizard
                )

                HorizontalDivider(color = SafetyCardDivider)

                // Row 3: Send feedback
                SafetyListRow(
                    title = "Send feedback",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    onClick = { showFeedbackDialog = true }
                )

                HorizontalDivider(color = SafetyCardDivider)

                // Row 4: Sign out
                SafetyListRow(
                    title = "Sign out",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = StatusDenied,
                    onClick = { showLogoutDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SafetyTextMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = SafetyTextPrimary
        )
    }
}
