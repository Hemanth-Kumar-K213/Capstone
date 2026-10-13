package com.womensafety.system.ui.sos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.ui.components.AppCard
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
    val isResolvingSafe by viewModel.isResolvingSafe.collectAsState()
    val isResolvingFalseAlarm by viewModel.isResolvingFalseAlarm.collectAsState()

    var isSirenMuted by remember { mutableStateOf(false) }

    val hasSmsPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.retrySmsDispatch(context)
        }
    }

    // Intercept back button to prevent accidental app dismiss during emergency
    BackHandler {
        // Must resolve through I'm Safe or False Alarm buttons
    }

    // Auto-navigate back to Home ONLY after an active emergency event is resolved
    var hasObservedActiveEvent by remember { mutableStateOf(false) }
    LaunchedEffect(activeEvent) {
        if (activeEvent != null && activeEvent?.status == SOSStatus.ACTIVE) {
            hasObservedActiveEvent = true
        } else if (hasObservedActiveEvent && (activeEvent == null || activeEvent?.status != SOSStatus.ACTIVE)) {
            onNavigateToHome()
        }
    }

    // Siren pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
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
                        Color(0xFF450A0A).copy(alpha = pulseAlpha),
                        Color(0xFF1C0505),
                        BackgroundDark
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Siren Icon Badge
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(EmergencyRed.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Active Emergency SOS",
                tint = EmergencyRed,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "EMERGENCY SOS ACTIVE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = EmergencyRed,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "Auto-resolving in ${viewModel.formatCountdown(countdownSeconds)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFDE047)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Delivery Status Card
        AppCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(EmergencyRed.copy(alpha = 0.2f), EmergencyRed.copy(alpha = 0.05f))
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "EMERGENCY DISPATCH STATUS",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                    color = Color(0xFFFDE047),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                val smsCount = activeEvent?.smsSent ?: 0
                if (smsCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = StatusApproved,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Carrier SMS: Sent to $smsCount Emergency Recipient(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceDark
                        )
                    }
                } else if (!hasSmsPermission) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = StatusPending,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "SMS Permission Required for Cellular Alerts",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusPending
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Grant SMS permission to allow background emergency dispatch through your SIM carrier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusPending),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GRANT SMS PERMISSION NOW", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = StatusPending,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Cellular SMS: No emergency contacts configured in profile",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariantDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Broadcast via Messaging App
                OutlinedButton(
                    onClick = {
                        val lat = activeEvent?.latitude ?: 12.9716
                        val lng = activeEvent?.longitude ?: 77.5946
                        val timeStr = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.US).format(java.util.Date(activeEvent?.triggeredAt ?: System.currentTimeMillis()))
                        val msg = "🚨 EMERGENCY ALERT 🚨\n${activeEvent?.userName ?: "User"} triggered SOS and needs urgent help!\n\nLocation:\nhttps://maps.google.com/?q=$lat,$lng\nTime: $timeStr\n\nSent automatically from Trinetra."
                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("smsto:")
                            putExtra("sms_body", msg)
                        }
                        context.startActivity(sendIntent)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigoLight)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryIndigoLight)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BROADCAST VIA SMS APP (PRE-FILLED)", color = PrimaryIndigoLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusApproved, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Notifying Emergency Circle via 2Factor Cloud Service",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceDark
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (activeEvent?.callMade == true) Icons.Filled.CheckCircle else Icons.Filled.PhoneInTalk,
                        contentDescription = null,
                        tint = if (activeEvent?.callMade == true) StatusApproved else OnSurfaceVariantDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (activeEvent?.callMade == true) "Direct emergency call dispatched automatically"
                        else "Direct emergency call connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusApproved
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        com.womensafety.system.manager.SOSManager.initiateEmergencyCall(context, "112")
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed)
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(14.dp), tint = EmergencyRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REDIAL EMERGENCY LINE (112)", color = EmergencyRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (activeEvent?.latitude != null && activeEvent?.longitude != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = OutlineDark.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "Broadcast Coordinates: ${String.format("%.5f", activeEvent!!.latitude)}, ${String.format("%.5f", activeEvent!!.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryIndigoLight
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
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isSirenMuted) EmergencyRed else Color(0xFFFDE047)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSirenMuted) EmergencyRed else Color(0xFFFDE047)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                if (isSirenMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isSirenMuted) "UNMUTE SIREN ALARM" else "MUTE SIREN ALARM",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // "I'M SAFE" Primary Action Button
        Button(
            onClick = {
                viewModel.markAsSafe(context, eventId) {
                    onNavigateToHome()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isResolvingSafe && !isResolvingFalseAlarm,
            colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isResolvingSafe) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "I'M SAFE (RESOLVE SOS)",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // "FALSE ALARM" Secondary Action Button
        OutlinedButton(
            onClick = {
                viewModel.markAsFalseAlarm(context, eventId) {
                    onNavigateToHome()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isResolvingSafe && !isResolvingFalseAlarm,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusPending),
            border = androidx.compose.foundation.BorderStroke(1.dp, StatusPending),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isResolvingFalseAlarm) {
                CircularProgressIndicator(color = StatusPending, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CANCEL AS FALSE ALARM", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
