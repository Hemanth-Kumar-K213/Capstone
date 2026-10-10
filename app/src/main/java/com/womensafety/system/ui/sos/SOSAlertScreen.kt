package com.womensafety.system.ui.sos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
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
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.SoundManager
import com.womensafety.system.viewmodel.SOSViewModel

@Composable
fun SOSAlertScreen(
    eventId: String,
    viewModel: SOSViewModel,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val activeEvent by viewModel.activeSOSEvent.collectAsState()
    val countdownSeconds by viewModel.sosCountdownSeconds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isSirenMuted by remember { mutableStateOf(false) }

    // Intercept back button to prevent accidental app dismiss during emergency
    BackHandler {
        // Must resolve through I'm Safe or False Alarm buttons
    }

    // Siren pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4A0000).copy(alpha = pulseAlpha),
                        Color(0xFF1F0000),
                        BackgroundDark
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Siren Icon Badge
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(StatusDenied.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Active Alarm",
                tint = StatusDenied,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "EMERGENCY SOS ACTIVE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = StatusDenied,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            "Auto-resolving in ${viewModel.formatCountdown(countdownSeconds)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = GradientGold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Delivery Status Card
        GlassCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(StatusDenied.copy(alpha = 0.2f), StatusDenied.copy(alpha = 0.05f))
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "EMERGENCY DISPATCH STATUS",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = GradientGold,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                val smsCount = activeEvent?.smsSent ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (smsCount > 0) Icons.Filled.CheckCircle else Icons.Filled.Info,
                        contentDescription = null,
                        tint = if (smsCount > 0) StatusApproved else GradientAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (smsCount > 0) "Sent SMS to $smsCount Emergency Contact(s)"
                        else "Cellular SMS: No emergency contacts added or SMS permission pending",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (smsCount > 0) OnSurfaceDark else OnSurfaceVariantDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusApproved, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Notifying Approved Trusted Contacts via FCM Push",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (activeEvent?.callMade == true) Icons.Filled.CheckCircle else Icons.Filled.PhoneInTalk,
                        contentDescription = null,
                        tint = if (activeEvent?.callMade == true) StatusApproved else OnSurfaceVariantDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (activeEvent?.callMade == true) "Auto-call placed to primary contact"
                        else "Auto-call: Not configured or unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (activeEvent?.callMade == true) OnSurfaceDark else OnSurfaceVariantDark
                    )
                }

                if (activeEvent?.latitude != null && activeEvent?.longitude != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Broadcast Coordinates: ${String.format("%.5f", activeEvent!!.latitude)}, ${String.format("%.5f", activeEvent!!.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = GradientAmber
                    )

                    if (!activeEvent?.address.isNullOrBlank()) {
                        Text(
                            activeEvent!!.address!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Siren Mute/Unmute Toggle
        OutlinedButton(
            onClick = {
                if (isSirenMuted) {
                    SoundManager.playSiren(context)
                    isSirenMuted = false
                } else {
                    SoundManager.stopSiren()
                    isSirenMuted = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isSirenMuted) StatusDenied else GradientGold
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                if (isSirenMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isSirenMuted) "UNMUTE SIREN ALARM" else "MUTE SIREN ALARM",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // "I'M SAFE" Primary Action Button
        Button(
            onClick = {
                viewModel.markAsSafe(context, eventId) {
                    onNavigateToHome()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = OnPrimary, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, tint = OnPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "I'M SAFE (RESOLVE SOS)",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = OnPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // "FALSE ALARM" Secondary Action Button
        OutlinedButton(
            onClick = {
                viewModel.markAsFalseAlarm(context, eventId) {
                    onNavigateToHome()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusPending),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("CANCEL AS FALSE ALARM", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
