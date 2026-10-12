package com.womensafety.system.ui.auth

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.TrustedContactAuthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TCLoginScreen(
    viewModel: TrustedContactAuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.loginState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    LaunchedEffect(state.resendCooldown) {
        if (state.resendCooldown > 0) {
            delay(1000)
            viewModel.updateLoginResendCooldown(state.resendCooldown - 1)
        }
    }

    Scaffold(
        containerColor = SafetyDarkBackground,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SafetyTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SafetyDarkBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SafetyDarkBackground)
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Guardian Sign In",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SafetyTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Trusted Contact Phone Verification",
                style = MaterialTheme.typography.bodyMedium,
                color = SafetyTextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

            AppTextField(
                value = state.phone,
                onValueChange = { viewModel.updateLoginPhone(it) },
                label = "Phone Number (e.g., +91XXXXXXXXXX)",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                enabled = !state.otpSent
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!state.otpSent) {
                SafetyPillButton(
                    text = if (state.isLoading) "Sending OTP..." else "Send Verification OTP",
                    onClick = { if (!state.isLoading) viewModel.sendLoginOtp(activity) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            } else {
                OtpInputField(
                    otpCode = state.otpCode,
                    onOtpChange = { viewModel.updateLoginOtp(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                SafetyPillButton(
                    text = if (state.isLoading) "Verifying..." else "Verify & Sign In",
                    onClick = { if (!state.isLoading) viewModel.verifyLoginOtp() },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (state.resendCooldown > 0) {
                    Text(
                        "Resend available in ${state.resendCooldown}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextSecondary
                    )
                } else {
                    TextButton(
                        onClick = { viewModel.sendLoginOtp(activity) }
                    ) {
                        Text("Resend OTP", color = SafetyPrimaryBlue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafetyTextSecondary
                )
                TextButton(onClick = onNavigateToSignup) {
                    Text(
                        "Sign Up",
                        fontWeight = FontWeight.Bold,
                        color = SafetyPrimaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
