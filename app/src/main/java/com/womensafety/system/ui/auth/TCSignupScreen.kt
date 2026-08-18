package com.womensafety.system.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.TrustedContactAuthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TCSignupScreen(
    viewModel: TrustedContactAuthViewModel,
    onSignupSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.signupState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(state.accountCreated) {
        if (state.accountCreated) onSignupSuccess()
    }

    LaunchedEffect(state.resendCooldown) {
        if (state.resendCooldown > 0) {
            delay(1000)
            viewModel.updateSignupResendCooldown(state.resendCooldown - 1)
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Trusted Contact Sign Up") },
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
                "Create Account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Register as a Trusted Contact to help keep someone safe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

            AppTextField(
                value = state.name,
                onValueChange = { viewModel.updateSignupName(it) },
                label = "Full Name",
                imeAction = ImeAction.Next,
                enabled = !state.otpSent
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(
                value = state.phone,
                onValueChange = { viewModel.updateSignupPhone(it) },
                label = "Phone Number (e.g., +91XXXXXXXXXX)",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                enabled = !state.otpSent
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!state.otpSent) {
                PrimaryButton(
                    text = "SEND OTP",
                    onClick = { activity?.let { viewModel.sendSignupOtp(it) } },
                    isLoading = state.isLoading
                )
            } else {
                OtpInputField(
                    otpCode = state.otpCode,
                    onOtpChange = { viewModel.updateSignupOtp(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = "VERIFY & CREATE ACCOUNT",
                    onClick = { viewModel.verifySignupOtp() },
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
                        onClick = { activity?.let { viewModel.sendSignupOtp(it) } }
                    ) {
                        Text("Resend OTP")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
