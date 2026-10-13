package com.womensafety.system.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.OTPViewModel
import kotlinx.coroutines.delay

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
    val isDark = LocalDarkTheme.current
    var otpCode by remember { mutableStateOf("") }
    var timeRemaining by remember { mutableIntStateOf(600) } // 10 minutes
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Security Verification",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val bgGradient = if (isDark) {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF1E1B4B).copy(alpha = 0.4f), BackgroundDark)
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color(0xFFEEF2FF), BackgroundLight)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(padding)
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Lock Icon Badge
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Verify Your Phone",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We sent a 6-digit security code via SMS to\n$phoneNumber",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(10.dp))

            if (state.isVerified) {
                AppCard(
                    accentGradient = Brush.horizontalGradient(
                        colors = listOf(StatusApproved.copy(alpha = 0.15f), StatusApproved.copy(alpha = 0.05f))
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusApproved,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Phone number verified successfully!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusApproved
                        )
                    }
                }
            } else {
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    ),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (state.attempts > 0) {
                                Text(
                                    text = "Attempts: ${state.attempts}/${state.maxAttempts}",
                                    color = if (state.attempts >= 3) EmergencyRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (timeRemaining > 0) {
                                val mins = timeRemaining / 60
                                val secs = timeRemaining % 60
                                Text(
                                    text = "Expires in %02d:%02d".format(mins, secs),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = "VERIFY OTP",
                    onClick = {
                        viewModel.verifyOTP(phoneNumber, otpCode)
                    },
                    enabled = otpCode.length == 6 && !state.isLoading && state.attempts < state.maxAttempts,
                    isLoading = state.isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (timeRemaining > 0 && timerActive) {
                    Text(
                        text = "Didn't receive SMS? You can resend once the timer expires.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
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
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
