package com.womensafety.system.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.AppTextField
import com.womensafety.system.ui.components.ErrorMessage
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.Validators
import com.womensafety.system.viewmodel.EmergencyContactViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEmergencyContactScreen(
    contactId: String,
    viewModel: EmergencyContactViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val existingContact = remember(state.contacts, contactId) {
        viewModel.getContactById(contactId)
    }

    var name by remember(existingContact) { mutableStateOf(existingContact?.name ?: "") }
    var phone by remember(existingContact) { mutableStateOf(existingContact?.phone ?: "") }
    var formError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SurfaceVariantDark,
            title = {
                Text(
                    "Delete Emergency Contact",
                    color = OnSurfaceDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to remove $name from your emergency contacts?",
                    color = OnSurfaceVariantDark
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteContact(contactId, onSuccess = onBack)
                    }
                ) {
                    Text("Delete", color = StatusDenied, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Edit Emergency Contact",
                        color = OnSurfaceDark,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OnSurfaceDark
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete Contact",
                            tint = StatusDenied
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            GradientDeepPurple.copy(alpha = 0.5f),
                            BackgroundDark
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "EDIT CONTACT",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = GradientGold,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Update the name or phone number for this emergency contact.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )

            Spacer(modifier = Modifier.height(20.dp))

            ErrorMessage(formError ?: state.error)
            if (formError != null || state.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            GlassCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    AppTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            formError = null
                        },
                        label = "Full Name",
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = GradientAmber)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AppTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            formError = null
                        },
                        label = "Phone Number",
                        keyboardType = KeyboardType.Phone,
                        leadingIcon = {
                            Icon(Icons.Filled.Phone, contentDescription = null, tint = GradientAmber)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "UPDATE CONTACT",
                isLoading = state.isLoading,
                onClick = {
                    val trimmedName = name.trim()
                    val normalizedPhone = Validators.normalizePhone(phone.trim())

                    if (trimmedName.isBlank()) {
                        formError = "Please enter contact name."
                        return@PrimaryButton
                    }

                    if (!Validators.isValidPhone(normalizedPhone)) {
                        formError = "Please enter a valid phone number with country code."
                        return@PrimaryButton
                    }

                    viewModel.updateContact(
                        contactId = contactId,
                        name = trimmedName,
                        phone = normalizedPhone,
                        onSuccess = onBack
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = StatusDenied
                )
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("DELETE CONTACT", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
