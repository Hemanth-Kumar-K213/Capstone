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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.ErrorMessage
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.StepIndicator
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.SetupWizardViewModel

@Composable
fun SetupCompleteScreen(
    viewModel: SetupWizardViewModel,
    onNavigateToHome: () -> Unit
) {
    val state by viewModel.state.collectAsState()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GradientDeepPurple.copy(alpha = 0.8f),
                        BackgroundDark,
                        BackgroundDark
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepIndicator(currentStep = 4, totalSteps = 4)

        Spacer(modifier = Modifier.height(32.dp))

        // Success Icon
        Box(
            modifier = Modifier
                .size(90.dp)
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
            text = "All Set!",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
            fontWeight = FontWeight.Bold,
            color = GradientGold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your safety network is activated and ready to protect you. You can update any of these settings at any time in your Profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantDark,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        ErrorMessage(state.error)
        if (state.error != null) Spacer(modifier = Modifier.height(12.dp))

        // Summary Card
        GlassCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "SETUP SUMMARY",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = GradientGold,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                SummaryItemRow(
                    label = "Emergency Permissions",
                    value = "${state.grantedPermissions.size} permission(s) granted",
                    isPositive = state.grantedPermissions.isNotEmpty()
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = GlassBorder
                )

                SummaryItemRow(
                    label = "Emergency Contacts",
                    value = "${state.emergencyContactsList.size} SMS contact(s) added",
                    isPositive = state.emergencyContactsList.isNotEmpty()
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = GlassBorder
                )

                SummaryItemRow(
                    label = "Location Tracking",
                    value = if (state.locationTrackingEnabled) "Enabled (Real-time GPS)" else "Disabled",
                    isPositive = state.locationTrackingEnabled
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = GlassBorder
                )

                SummaryItemRow(
                    label = "Shake Detection",
                    value = if (state.shakeDetectionEnabled) "Active (${state.shakeSensitivity} Sensitivity)" else "Disabled",
                    isPositive = state.shakeDetectionEnabled
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = GlassBorder
                )

                SummaryItemRow(
                    label = "Auto-Call Contact",
                    value = if (state.sosAutoCall) "Enabled" else "Disabled",
                    isPositive = state.sosAutoCall
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Go to Home button
        PrimaryButton(
            text = "GO TO HOME",
            isLoading = state.isLoading,
            onClick = {
                viewModel.completeSetup(onSuccess = onNavigateToHome)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
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
            tint = if (isPositive) StatusApproved else OnSurfaceVariantDark,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceDark
            )
        }
    }
}
