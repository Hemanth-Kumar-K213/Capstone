package com.womensafety.system.ui.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.ErrorMessage
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.StepIndicator
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.SetupWizardViewModel

@Composable
fun SetupCompleteScreen(
    viewModel: SetupWizardViewModel,
    onNavigateToHome: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val isDark = LocalDarkTheme.current

    // Icon scale animation
    val iconScale = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1E1B4B).copy(alpha = 0.5f), BackgroundDark)
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFEEF2FF), BackgroundLight)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            StepIndicator(currentStep = 4, totalSteps = 4)

            Spacer(modifier = Modifier.height(32.dp))

            // Success Icon
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(iconScale.value)
                    .clip(CircleShape)
                    .background(StatusApproved.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Success",
                    tint = StatusApproved,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "System Initialized!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your safety shield is armed and ready to protect you. You can update any of these preferences anytime from your Profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(12.dp))

            // Summary Card
            AppCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "CONFIGURATION SUMMARY",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SummaryItemRow(
                        label = "Emergency Permissions",
                        value = "${state.grantedPermissions.size} system permission(s) granted",
                        isPositive = state.grantedPermissions.isNotEmpty()
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    SummaryItemRow(
                        label = "Emergency Contacts",
                        value = "${state.emergencyContactsList.size} SMS recipient(s) enrolled",
                        isPositive = state.emergencyContactsList.isNotEmpty()
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    SummaryItemRow(
                        label = "Location Tracking",
                        value = if (state.locationTrackingEnabled) "Enabled (Continuous GPS sharing)" else "Disabled",
                        isPositive = state.locationTrackingEnabled
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    SummaryItemRow(
                        label = "Shake Detection",
                        value = if (state.shakeDetectionEnabled) "Active (${state.shakeSensitivity} Sensitivity)" else "Disabled",
                        isPositive = state.shakeDetectionEnabled
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    SummaryItemRow(
                        label = "Auto-Call Contact",
                        value = if (state.sosAutoCall) "Enabled (Outgoing call on SOS)" else "Disabled",
                        isPositive = state.sosAutoCall
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            PrimaryButton(
                text = "LAUNCH SAFETY DASHBOARD",
                isLoading = state.isLoading,
                onClick = {
                    viewModel.completeSetup(onSuccess = onNavigateToHome)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryItemRow(
    label: String,
    value: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = if (isPositive) StatusApproved else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
