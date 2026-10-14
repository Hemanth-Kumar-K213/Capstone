package com.womensafety.system.ui.safety

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.womensafety.system.data.model.CriticalAlertData
import com.womensafety.system.data.model.FallDetection
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.SleepModeViewModel

@Composable
fun SleepModeCard(
    viewModel: SleepModeViewModel,
    onTriggerSOS: () -> Unit = {},
    onNavigateToDiagnostic: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSleeping by viewModel.isSleeping.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val lastQuality by viewModel.lastSleepQuality.collectAsState()
    val fallAlert by viewModel.fallAlert.collectAsState()
    val fallCountdown by viewModel.fallCountdown.collectAsState()
    val criticalAlert by viewModel.criticalAlert.collectAsState()

    // ─── CRITICAL OVERRIDE FULL-SCREEN DIALOG ───────────────────────────
    if (criticalAlert != null) {
        CriticalEmergencyAlertDialog(
            alert = criticalAlert!!,
            onAcknowledge = { viewModel.dismissCriticalAlert() },
            onTriggerSOS = {
                viewModel.dismissCriticalAlert()
                onTriggerSOS()
            }
        )
    }

    // ─── FALL DETECTION COUNTDOWN DIALOG ────────────────────────────────
    if (fallAlert != null && fallCountdown != null) {
        FallDetectionCountdownDialog(
            countdownSeconds = fallCountdown!!,
            onConfirmSafe = { viewModel.confirmFallSafe() },
            onTriggerHelp = {
                viewModel.confirmFallSafe()
                onTriggerSOS()
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier) {
        AppCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    SecondaryViolet.copy(alpha = 0.15f),
                    DeepCyan.copy(alpha = 0.05f)
                )
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SecondaryViolet.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSleeping) Icons.Filled.Bedtime else Icons.Filled.NightsStay,
                            contentDescription = null,
                            tint = SecondaryViolet,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isSleeping) "Sleeping Mode Active" else "Smart Emergency Sleep Override",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isSleeping) "Location broadcasts paused • Emergency override armed"
                            else "Protects uninterrupted rest without sacrificing safety",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = if (isSleeping) SecondaryViolet else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isSleeping) "Resting" else "Standby",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSleeping) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                // Rules explanation
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PhoneInTalk, contentDescription = null, tint = StatusApproved, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("3 rapid calls within 1 minute overrides silent mode & rings at max volume", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DirectionsRun, contentDescription = null, tint = AmberOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Accelerometer fall detection with 10s confirmation prompt", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = SecondaryViolet, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Immediate alarm wake-up if a trusted contact triggers SOS", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (lastQuality != null && !isSleeping) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(lastQuality!!.iconEmoji, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Last Session Quality: ${lastQuality!!.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isSleeping) {
                    Button(
                        onClick = { viewModel.endSleepMode() },
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wake Up & Resume Normal Monitoring", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.startSleepMode() },
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Sleep Mode Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ─── HARDWARE SENSOR DIAGNOSTIC ────────────────────────────────────
        AppCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(SecondaryViolet.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Sensors, contentDescription = null, tint = SecondaryViolet, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware Sensor Diagnostic", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Verify live accelerometer streaming, 30cm drop magnitude spike & 10s waveform", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToDiagnostic,
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Accelerometer Diagnostic Screen", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CriticalEmergencyAlertDialog(
    alert: CriticalAlertData,
    onAcknowledge: () -> Unit,
    onTriggerSOS: () -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "criticalPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF8B0000).copy(alpha = 0.98f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .scale(pulseScale)
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Warning",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    alert.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    alert.message,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onAcknowledge,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(52.dp)
                ) {
                    Text(
                        "I'M AWAKE — ACKNOWLEDGE",
                        color = Color(0xFF8B0000),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!alert.callerName.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                onAcknowledge()
                                val intent = Intent(Intent.ACTION_DIAL)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call Back", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = onTriggerSOS,
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Filled.Sos, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trigger SOS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FallDetectionCountdownDialog(
    countdownSeconds: Int,
    onConfirmSafe: () -> Unit,
    onTriggerHelp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = CoralRed, modifier = Modifier.size(40.dp))
        },
        title = {
            Text("Fall Detected While Sleeping", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Are you okay? If you do not respond, an emergency alert will automatically be sent to your trusted contacts in:",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(CoralRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$countdownSeconds",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CoralRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmSafe,
                colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I'm Okay — Cancel Alert", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onTriggerHelp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Help Now", color = CoralRed, fontWeight = FontWeight.Bold)
            }
        }
    )
}
