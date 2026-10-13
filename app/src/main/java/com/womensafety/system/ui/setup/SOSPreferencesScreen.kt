package com.womensafety.system.ui.setup

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.StepIndicator
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
    val isDark = LocalDarkTheme.current
    var sensitivityDropdownExpanded by remember { mutableStateOf(false) }
    val sensitivityOptions = listOf("LOW", "MEDIUM", "HIGH")

    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1E1B4B).copy(alpha = 0.5f), BackgroundDark)
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFEEF2FF), BackgroundLight)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            StepIndicator(currentStep = 3, totalSteps = 4)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SAFETY PREFERENCES",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Configure Trigger Behavior",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Personalize emergency response options. You can adjust these settings at any time in your Settings & Profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Preference 1: Location Tracking
            PreferenceToggleCard(
                icon = Icons.Filled.LocationOn,
                iconColor = PrimaryIndigo,
                title = "Continuous Location Tracking",
                description = "Periodically syncs GPS coordinates to the safety cloud and attaches live maps to SMS broadcasts.",
                checked = state.locationTrackingEnabled,
                onCheckedChange = { viewModel.updateLocationTracking(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Preference 2: Shake Detection
            PreferenceToggleCard(
                icon = Icons.Filled.Vibration,
                iconColor = SecondaryViolet,
                title = "Shake-to-Alert Detection",
                description = "Automatically escalates to SOS when vigorous shaking motion is detected on your device.",
                checked = state.shakeDetectionEnabled,
                onCheckedChange = { viewModel.updateShakeDetection(it) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Preference 3: Shake Sensitivity Dropdown
            AppCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SecondaryViolet.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                tint = if (state.shakeDetectionEnabled) SecondaryViolet else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shake Sensitivity Level",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.shakeDetectionEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "High sensitivity triggers on light shakes; Low sensitivity prevents false alarms during activities like running.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (state.shakeDetectionEnabled) 0.9f else 0.4f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                                            color = if (option == state.shakeSensitivity) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                iconColor = AccentBlue,
                title = "Auto-Call Primary Contact",
                description = "Automatically triggers an outgoing voice call to your top emergency contact during SOS activation.",
                checked = state.sosAutoCall,
                onCheckedChange = { viewModel.updateSosAutoCall(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "REVIEW & COMPLETE",
                onClick = onNext
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreferenceToggleCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    AppCard {
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
                    .background(iconColor.copy(alpha = 0.14f)),
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
