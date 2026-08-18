package com.womensafety.system.ui.profile

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.UserProfileViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePhoneScreen(
    viewModel: UserProfileViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.changePhoneState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(state.success) {
        if (state.success) {
            delay(1500)
            viewModel.resetChangePhoneState()
            onBack()
        }
    }

    LaunchedEffect(state.resendCooldown) {
        if (state.resendCooldown > 0) {
            delay(1000)
            viewModel.updateResendCooldown(state.resendCooldown - 1)
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Change Phone Number") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Change Phone Number",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Enter your new phone number and verify it with OTP.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

            if (state.success) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Phone number updated successfully!")
                    }
                }
            } else {
                AppTextField(
                    value = state.newPhone,
                    onValueChange = { viewModel.updateNewPhone(it) },
                    label = "New Phone Number (e.g., +91XXXXXXXXXX)",
                    keyboardType = KeyboardType.Phone,
                    enabled = !state.otpSent
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (!state.otpSent) {
                    PrimaryButton(
                        text = "SEND OTP",
                        onClick = { activity?.let { viewModel.sendChangePhoneOtp(it) } },
                        isLoading = state.isLoading
                    )
                } else {
                    OtpInputField(
                        otpCode = state.otpCode,
                        onOtpChange = { viewModel.updateChangePhoneOtp(it) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    PrimaryButton(
                        text = "VERIFY & UPDATE",
                        onClick = { viewModel.verifyAndChangePhone() },
                        isLoading = state.isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (state.resendCooldown > 0) {
                        Text(
                            "Resend available in ${state.resendCooldown}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TextButton(
                            onClick = { activity?.let { viewModel.sendChangePhoneOtp(it) } }
                        ) {
                            Text("Resend OTP")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
