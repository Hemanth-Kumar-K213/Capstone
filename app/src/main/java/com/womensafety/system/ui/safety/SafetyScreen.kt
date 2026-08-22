package com.womensafety.system.ui.safety

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.data.model.SOSTriggerMethod
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.SOSViewModel
import com.womensafety.system.viewmodel.UserProfileViewModel
import kotlinx.coroutines.delay

@Composable
fun SafetyScreen(
    sosViewModel: SOSViewModel,
    profileViewModel: UserProfileViewModel,
    onNavigateToAlert: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToHistoryDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val activeSOSEvent by sosViewModel.activeSOSEvent.collectAsState()
    val sosHistory by sosViewModel.sosHistory.collectAsState()
    val profileState by profileViewModel.profileState.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmCountdown by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        sosViewModel.loadSOSHistory()
        profileViewModel.loadProfile()
    }

    // If there is already an active SOS event, navigate to the active alert screen
    LaunchedEffect(activeSOSEvent) {
        if (activeSOSEvent != null && activeSOSEvent!!.status == SOSStatus.ACTIVE) {
            onNavigateToAlert(activeSOSEvent!!.id)
        }
    }

    // Confirmation Countdown Dialog
    if (showConfirmDialog) {
        LaunchedEffect(showConfirmDialog) {
            confirmCountdown = 3
            while (confirmCountdown > 0) {
                delay(1000)
                confirmCountdown -= 1
            }
            // Auto-trigger when countdown reaches 0
            showConfirmDialog = false
            sosViewModel.triggerSOS(context, SOSTriggerMethod.BUTTON) { eventId ->
                onNavigateToAlert(eventId)
            }
        }

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = SurfaceVariantDark,
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = StatusDenied,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "Trigger Emergency SOS?",
                    color = OnSurfaceDark,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Alerting in $confirmCountdown seconds...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StatusDenied
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This will immediately dispatch cellular SMS to your emergency contacts, sound sirens, and notify trusted contacts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        sosViewModel.triggerSOS(context, SOSTriggerMethod.BUTTON) { eventId ->
                            onNavigateToAlert(eventId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDenied)
                ) {
                    Text("TRIGGER NOW", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("CANCEL", color = OnSurfaceVariantDark)
                }
            }
        )
    }

    // Pulse animation for the giant SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientDeepPurple.copy(alpha = 0.6f), BackgroundDark)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "SAFETY CENTER",
            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp),
            fontWeight = FontWeight.Bold,
            color = GradientGold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "Instant emergency dispatch and response network",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantDark
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Giant SOS Button with Pulsing Ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Pulsing Background Glow Ring
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(StatusDenied.copy(alpha = 0.35f), StatusDenied.copy(alpha = 0.02f))
                        )
                    )
            )

            // Inner SOS Button
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(StatusDenied, StatusDenied.copy(alpha = 0.85f))
                        )
                    )
                    .clickable { showConfirmDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Sos,
                        contentDescription = "SOS Button",
                        tint = OnPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "TAP FOR SOS",
                        color = OnPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Emergency Network Summary Card
        GlassCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    GradientAmber.copy(alpha = 0.08f),
                    GradientGold.copy(alpha = 0.04f)
                )
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "ACTIVE SAFETY NETWORK",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    color = GradientGold,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Group, contentDescription = null, tint = GradientAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Trusted Contacts", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark)
                    }
                    Text(
                        "${profileState.trustedContactCount} Notified",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GradientAmber
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = GlassBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sms, contentDescription = null, tint = GradientRose, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Emergency SMS Contacts", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark)
                    }
                    Text(
                        "${profileState.emergencyContactCount} Contacts",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GradientRose
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = GlassBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PhoneInTalk, contentDescription = null, tint = StatusApproved, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Auto-Call Primary Contact", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDark)
                    }
                    Text(
                        if (profileState.userProfile?.sosAutoCall == true) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (profileState.userProfile?.sosAutoCall == true) StatusApproved else OnSurfaceVariantDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent SOS History Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "RECENT INCIDENTS",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = OnSurfaceVariantDark,
                fontWeight = FontWeight.Bold
            )

            if (sosHistory.isNotEmpty()) {
                TextButton(onClick = onNavigateToHistory) {
                    Text("View Full History", color = GradientGold, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (sosHistory.isEmpty()) {
            GlassCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No past SOS incidents recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariantDark
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sosHistory.take(3).forEach { event ->
                    RecentSOSItem(
                        event = event,
                        onClick = { onNavigateToHistoryDetail(event.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RecentSOSItem(
    event: SOSEvent,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when (event.status) {
                            SOSStatus.ACTIVE -> StatusDenied.copy(alpha = 0.2f)
                            SOSStatus.RESOLVED -> StatusApproved.copy(alpha = 0.2f)
                            else -> StatusPending.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (event.status) {
                        SOSStatus.ACTIVE -> Icons.Filled.Warning
                        SOSStatus.RESOLVED -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.Info
                    },
                    contentDescription = null,
                    tint = when (event.status) {
                        SOSStatus.ACTIVE -> StatusDenied
                        SOSStatus.RESOLVED -> StatusApproved
                        else -> StatusPending
                    },
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    DateUtils.formatDateTime(event.triggeredAt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceDark
                )
                Text(
                    "Trigger: ${event.triggerMethod.name} • Status: ${event.status.name}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = OnSurfaceVariantDark
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceVariantDark.copy(alpha = 0.5f)
            )
        }
    }
}
