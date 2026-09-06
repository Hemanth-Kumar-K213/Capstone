package com.womensafety.system.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.StepIndicator
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.SetupWizardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSPreferencesScreen(
    viewModel: SetupWizardViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var sensitivityDropdownExpanded by remember { mutableStateOf(false) }
    val sensitivityOptions = listOf("LOW", "MEDIUM", "HIGH")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GradientDeepPurple.copy(alpha = 0.8f),
                        BackgroundDark,
                        BackgroundDark
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        StepIndicator(currentStep = 3, totalSteps = 4)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "SAFETY PREFERENCES",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = GradientGold,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Configure SOS Preferences",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Customize how emergency features operate on your phone. You can always change these settings in your Profile.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantDark
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Preference 1: Location Tracking
        PreferenceToggleCard(
            icon = Icons.Filled.LocationOn,
            iconColor = GradientAmber,
            title = "Enable Location Tracking",
            description = "Periodically monitor location in emergency mode and append GPS coordinates to alert messages.",
            checked = state.locationTrackingEnabled,
            onCheckedChange = { viewModel.updateLocationTracking(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Preference 2: Shake Detection
        PreferenceToggleCard(
            icon = Icons.Filled.Vibration,
            iconColor = GradientGold,
            title = "Enable Shake Detection",
            description = "Automatically trigger emergency SOS mode when you vigorously shake your phone.",
            checked = state.shakeDetectionEnabled,
            onCheckedChange = { viewModel.updateShakeDetection(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Preference 3: Shake Sensitivity Dropdown
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GradientRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = if (state.shakeDetectionEnabled) GradientRose else OnSurfaceVariantDark.copy(alpha = 0.4f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shake Sensitivity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.shakeDetectionEnabled) OnSurfaceDark else OnSurfaceVariantDark.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Higher sensitivity detects gentler shakes; lower sensitivity prevents accidental triggers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark.copy(alpha = if (state.shakeDetectionEnabled) 0.9f else 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = sensitivityDropdownExpanded && state.shakeDetectionEnabled,
                    onExpandedChange = {
                        if (state.shakeDetectionEnabled) {
                            sensitivityDropdownExpanded = !sensitivityDropdownExpanded
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = state.shakeSensitivity,
                        onValueChange = {},
                        readOnly = true,
                        enabled = state.shakeDetectionEnabled,
                        label = { Text("Sensitivity Level") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sensitivityDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, state.shakeDetectionEnabled)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GradientAmber,
                            unfocusedBorderColor = OutlineDark,
                            disabledBorderColor = OutlineDark.copy(alpha = 0.3f),
                            disabledTextColor = OnSurfaceVariantDark.copy(alpha = 0.4f),
                            disabledLabelColor = OnSurfaceVariantDark.copy(alpha = 0.4f)
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = sensitivityDropdownExpanded && state.shakeDetectionEnabled,
                        onDismissRequest = { sensitivityDropdownExpanded = false }
                    ) {
                        sensitivityOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option,
                                        fontWeight = if (option == state.shakeSensitivity) FontWeight.Bold else FontWeight.Normal,
                                        color = if (option == state.shakeSensitivity) GradientAmber else OnSurfaceDark
                                    )
                                },
                                onClick = {
                                    viewModel.updateShakeSensitivity(option)
                                    sensitivityDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preference 4: Auto-call first contact
        PreferenceToggleCard(
            icon = Icons.Filled.Call,
            iconColor = GradientRose,
            title = "Auto-Call First Emergency Contact",
            description = "Initiates an outgoing phone call to your top emergency contact immediately upon triggering SOS.",
            checked = state.sosAutoCall,
            onCheckedChange = { viewModel.updateSosAutoCall(it) }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Next Button
        PrimaryButton(
            text = "REVIEW & COMPLETE",
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PreferenceToggleCard(
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnPrimary,
                    checkedTrackColor = GradientAmber,
                    uncheckedThumbColor = OnSurfaceVariantDark,
                    uncheckedTrackColor = SurfaceVariantDark
                )
            )
        }
    }
}
