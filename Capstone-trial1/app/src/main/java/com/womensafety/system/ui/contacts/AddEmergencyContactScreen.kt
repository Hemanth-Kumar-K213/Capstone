package com.womensafety.system.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
fun AddEmergencyContactScreen(
    viewModel: EmergencyContactViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isPrimary by remember { mutableStateOf(false) }
    var enableEscalation by remember { mutableStateOf(true) }
    var formError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add Emergency Contact",
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
                text = "CONTACT DETAILS",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = GradientGold,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Add a trusted person who should receive immediate SMS coordinates and emergency alerts.",
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
                        label = "Phone Number (e.g. +91XXXXXXXXXX)",
                        keyboardType = KeyboardType.Phone,
                        leadingIcon = {
                            Icon(Icons.Filled.Phone, contentDescription = null, tint = GradientAmber)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isPrimary,
                            onCheckedChange = { isPrimary = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GradientAmber)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Primary Contact", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark, fontWeight = FontWeight.Bold)
                            Text("Make this the default emergency call recipient", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = enableEscalation,
                            onCheckedChange = { enableEscalation = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GradientAmber)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Enable Alert Escalation", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark, fontWeight = FontWeight.Bold)
                            Text("Sends automated SMS with live coordinates", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "SAVE CONTACT",
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

                    viewModel.addContact(
                        name = trimmedName,
                        phone = normalizedPhone,
                        isPrimary = isPrimary,
                        enableEscalation = enableEscalation,
                        onSuccess = onBack
                    )
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
