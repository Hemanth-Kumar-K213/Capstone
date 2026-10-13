package com.womensafety.system.ui.safety

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.EmergencyActionButton
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.SafetyMonitoringViewModel

@Composable
fun AreYouSafeScreen(
    viewModel: SafetyMonitoringViewModel,
    onNavigateToAlert: (String) -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val isCheckActive by viewModel.isSafetyCheckActive.collectAsState()
    val riskScore by viewModel.riskScore.collectAsState()
    val anomalies by viewModel.activeAnomalies.collectAsState()
    val countdown by viewModel.countdownSeconds.collectAsState()

    // Prevent closing the check via back button
    BackHandler {
        // User must tap "I'm Safe" or "I Need Help"
    }

    // Auto-navigate to home if the check becomes inactive (meaning user marked safe)
    LaunchedEffect(isCheckActive) {
        if (!isCheckActive) {
            onNavigateToHome()
        }
    }

    // Pulse animation for the warning glow
    val infiniteTransition = rememberInfiniteTransition(label = "warning_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AmberWarning.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Pulse Icon badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(AmberWarning.copy(alpha = 0.2f))
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AmberWarning.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "ARE YOU SAFE?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "PROACTIVE MONITORING ALERT",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
            fontWeight = FontWeight.Bold,
            color = AmberWarning
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Risk Stats Card
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "ANOMALIES DETECTED",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Calculated Risk Score:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = (if (riskScore > 60) EmergencyRed else AmberWarning).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "$riskScore / 100",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (riskScore > 60) EmergencyRed else AmberWarning,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (anomalies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(10.dp))

                anomalies.forEach { anomaly ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "• $anomaly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Countdown Timer Text
        Text(
            "Auto-escalating to SOS in",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "$countdown s",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = EmergencyRed,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Button 1: "I'M SAFE"
        Button(
            onClick = {
                viewModel.markAsSafe(context)
                onNavigateToHome()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = OnPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "I'M SAFE",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = OnPrimary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Button 2: "I NEED HELP" (SOS Escalation)
        Button(
            onClick = {
                viewModel.triggerSOS(context)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = OnPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "I NEED HELP (TRIGGER SOS)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = OnPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
