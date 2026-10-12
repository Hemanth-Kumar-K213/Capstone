package com.womensafety.system.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.system.ui.components.ErrorMessage
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.OTPViewModel
import kotlinx.coroutines.delay

/**
 * Production-ready Phone OTP Verification Composable.
 * Interfaces directly with Twilio SMS Cloud Functions for reliable 2FA verification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTPVerificationScreen(
    phoneNumber: String,
    userName: String,
    onOTPVerified: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: OTPViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var otpCode by remember { mutableStateOf("") }
    var timeRemaining by remember { mutableStateOf(600) } // 10 minutes (600 seconds)
    var timerActive by remember { mutableStateOf(true) }

    // Dispatch Twilio OTP on initial appearance
    LaunchedEffect(phoneNumber) {
        if (!state.otpSent) {
            viewModel.sendOTP(phoneNumber, userName)
        }
    }

    // 10-minute expiry countdown timer
    LaunchedEffect(state.otpSent, timerActive) {
        if (state.otpSent && timerActive) {
            while (timeRemaining > 0) {
                delay(1000)
                timeRemaining--
            }
            timerActive = false
        }
    }

    // Automatically navigate when OTP successfully verified
    LaunchedEffect(state.isVerified) {
        if (state.isVerified) {
            delay(500)
            onOTPVerified()
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Phone Verification", color = OnSurfaceDark) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OnSurfaceDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
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

            // Lock Icon Badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(SurfaceVariantDark, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Verification Lock",
                    modifier = Modifier.size(40.dp),
                    tint = GradientAmber
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Verify Your Phone",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We sent a 6-digit security code via SMS to\n$phoneNumber",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error notification if verification fails
            ErrorMessage(state.error)
            if (state.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (state.isVerified) {
                // Success Badge Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusApproved.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusApproved
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Phone number verified successfully!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusApproved
                        )
                    }
                }
            } else {
                // OTP Input Field
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }
                        if (digitsOnly.length <= 6) {
                            otpCode = digitsOnly
                        }
                    },
                    label = { Text("Enter 6-Digit OTP") },
                    placeholder = { Text("000000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GradientAmber,
                        unfocusedBorderColor = OutlineDark,
                        focusedTextColor = OnSurfaceDark,
                        unfocusedTextColor = OnSurfaceDark
                    ),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (state.attempts > 0) {
                                Text(
                                    text = "Attempts: ${state.attempts}/${state.maxAttempts}",
                                    color = if (state.attempts >= 3) StatusDenied else OnSurfaceVariantDark,
                                    fontSize = 12.sp
                                )
                            }
                            if (timeRemaining > 0) {
                                val mins = timeRemaining / 60
                                val secs = timeRemaining % 60
                                Text(
                                    text = "Expires in %02d:%02d".format(mins, secs),
                                    color = GradientAmber,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Verify Button
                PrimaryButton(
                    text = "VERIFY OTP",
                    onClick = {
                        viewModel.verifyOTP(phoneNumber, otpCode)
                    },
                    enabled = otpCode.length == 6 && !state.isLoading && state.attempts < state.maxAttempts,
                    isLoading = state.isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Resend OTP Action
                if (timeRemaining > 0 && timerActive) {
                    Text(
                        text = "Didn't receive SMS? You can resend after the timer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                } else {
                    TextButton(
                        onClick = {
                            viewModel.resendOTP(phoneNumber, userName)
                            timeRemaining = 600
                            timerActive = true
                        }
                    ) {
                        Text(
                            text = "Resend OTP",
                            color = GradientAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
