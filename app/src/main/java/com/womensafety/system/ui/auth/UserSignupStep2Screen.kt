package com.womensafety.system.ui.auth

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
import androidx.compose.ui.unit.dp
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSignupStep2Screen(
    viewModel: AuthViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.signupState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // Auto-navigate when verified
    LaunchedEffect(state.phoneVerified) {
        if (state.phoneVerified) {
            delay(500)
            onNext()
        }
    }

    // Resend cooldown timer
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
                title = { Text("Phone Verification") },
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
            Spacer(modifier = Modifier.height(16.dp))

            StepIndicator(currentStep = 2, totalSteps = 6)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Phone Verification",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Verify your phone number: ${state.phone}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

            if (state.phoneVerified) {
                // Verification complete
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
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Phone verified successfully!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else if (state.verificationId == null) {
                // Send OTP
                PrimaryButton(
                    text = "SEND OTP",
                    onClick = { viewModel.sendPhoneOtp() },
                    isLoading = state.isLoading
                )
            } else {
                // Enter OTP
                OtpInputField(
                    otpCode = state.otpCode,
                    onOtpChange = { viewModel.updateOtpCode(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = "VERIFY OTP",
                    onClick = { viewModel.verifyOtp() },
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
                        onClick = { viewModel.sendPhoneOtp() }
                    ) {
                        Text("Resend OTP")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
