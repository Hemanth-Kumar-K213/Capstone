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
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurfaceDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GradientDeepPurple.copy(alpha = 0.5f), BackgroundDark)
                    )
                )
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "TRUSTED CONTACT LOGIN",
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 2.sp),
                fontWeight = FontWeight.Bold,
                color = GradientRose
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
                Button(
                    onClick = { activity?.let { viewModel.sendLoginOtp(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !state.isLoading,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = GradientRose)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = OnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("SEND OTP", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                OtpInputField(
                    otpCode = state.otpCode,
                    onOtpChange = { viewModel.updateLoginOtp(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.verifyLoginOtp() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !state.isLoading,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = GradientRose)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = OnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("VERIFY & LOGIN", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state.resendCooldown > 0) {
                    Text(
                        "Resend available in ${state.resendCooldown}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                } else {
                    TextButton(
                        onClick = { activity?.let { viewModel.sendLoginOtp(it) } }
                    ) {
                        Text("Resend OTP", color = GradientRose)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Don't have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantDark
            )

            TextButton(onClick = onNavigateToSignup) {
                Text(
                    "SIGN UP",
                    fontWeight = FontWeight.Bold,
                    color = GradientRose
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
