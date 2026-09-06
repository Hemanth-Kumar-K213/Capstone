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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.EmergencyContact
import com.womensafety.system.ui.components.AppTextField
import com.womensafety.system.ui.components.ErrorMessage
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.StepIndicator
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.Validators
import com.womensafety.system.viewmodel.SetupWizardViewModel

@Composable
fun EmergencyContactsSetupScreen(
    viewModel: SetupWizardViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    var dialogName by remember { mutableStateOf("") }
    var dialogPhone by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                dialogError = null
            },
            containerColor = SurfaceVariantDark,
            title = {
                Text(
                    "Add Emergency Contact",
                    color = OnSurfaceDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Enter the contact's name and phone number (e.g. +91XXXXXXXXXX):",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AppTextField(
                        value = dialogName,
                        onValueChange = {
                            dialogName = it
                            dialogError = null
                        },
                        label = "Contact Name",
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = GradientAmber)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AppTextField(
                        value = dialogPhone,
                        onValueChange = {
                            dialogPhone = it
                            dialogError = null
                        },
                        label = "Phone Number",
                        keyboardType = KeyboardType.Phone,
                        leadingIcon = {
                            Icon(Icons.Filled.Phone, contentDescription = null, tint = GradientAmber)
                        }
                    )

                    if (dialogError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = dialogError ?: "",
                            color = StatusDenied,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = dialogName.trim()
                        val normalizedPhone = Validators.normalizePhone(dialogPhone.trim())
                        if (trimmedName.isBlank()) {
                            dialogError = "Please enter contact name."
                            return@Button
                        }
                        if (!Validators.isValidPhone(normalizedPhone)) {
                            dialogError = "Please enter a valid phone number."
                            return@Button
                        }
                        val success = viewModel.addEmergencyContact(trimmedName, normalizedPhone)
                        if (success) {
                            dialogName = ""
                            dialogPhone = ""
                            dialogError = null
                            showAddDialog = false
                        } else {
                            dialogError = state.error
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GradientAmber)
                ) {
                    Text("ADD CONTACT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    dialogError = null
                }) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

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
        StepIndicator(currentStep = 2, totalSteps = 4)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "EMERGENCY CONTACTS",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = GradientGold,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Add Emergency Contacts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "These people will receive SMS alerts with your real-time GPS location during emergencies. They don't need to install this app.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        ErrorMessage(state.error)

        // Contact count and Add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${state.emergencyContactsList.size} Contact(s) Added",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (state.emergencyContactsList.isNotEmpty()) StatusApproved else StatusPending
            )

            OutlinedButton(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GradientRose),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ADD CONTACT", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.emergencyContactsList.isEmpty()) {
            GlassCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(GradientRose.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.ContactPhone,
                            contentDescription = null,
                            tint = GradientRose,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No emergency contacts added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "You must add at least 1 emergency contact (e.g. parent, guardian, partner) to proceed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            state.emergencyContactsList.forEach { contact ->
                EmergencyContactItemCard(
                    contact = contact,
                    onDelete = { viewModel.removeEmergencyContact(contact.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Next Button (disabled if < 1 contact)
        PrimaryButton(
            text = "CONTINUE TO SOS PREFERENCES",
            enabled = state.emergencyContactsList.isNotEmpty(),
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun EmergencyContactItemCard(
    contact: EmergencyContact,
    onDelete: () -> Unit
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
                    .background(GradientRose.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = GradientRose,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariantDark
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove contact",
                    tint = StatusDenied.copy(alpha = 0.8f)
                )
            }
        }
    }
}
