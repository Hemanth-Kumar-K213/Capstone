package com.womensafety.system.ui.safety

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.data.model.SOSTriggerMethod
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.utils.SoundManager
import com.womensafety.system.viewmodel.SOSViewModel
import com.womensafety.system.viewmodel.UserProfileViewModel
import kotlinx.coroutines.delay

@Composable
fun SafetyScreen(
    sosViewModel: SOSViewModel,
    profileViewModel: UserProfileViewModel,
    onNavigateToAlert: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToHistoryDetail: (String) -> Unit,
    onNavigateToLiveMap: (String) -> Unit = {},
    onNavigateToTravelMode: () -> Unit = {},
    onNavigateToShakeSettings: () -> Unit = {},
    onNavigateToProactivePrompt: () -> Unit = {}
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

    var showSafetyCheckDemoDialog by remember { mutableStateOf(false) }
    var safetyCheckDemoCountdown by remember { mutableIntStateOf(10) }
    var safetyCheckDemoSuccess by remember { mutableStateOf(false) }

    // ─── Safety Check Demo Dialog ────────────────────────────
    if (showSafetyCheckDemoDialog) {
        LaunchedEffect(showSafetyCheckDemoDialog, safetyCheckDemoSuccess) {
            safetyCheckDemoCountdown = 10
            while (safetyCheckDemoCountdown > 0 && !safetyCheckDemoSuccess) {
                delay(1000)
                safetyCheckDemoCountdown -= 1
            }
        }

        AlertDialog(
            onDismissRequest = {
                showSafetyCheckDemoDialog = false
                safetyCheckDemoSuccess = false
            },
            containerColor = SafetyCardBackground,
            icon = {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = SafetyPrimaryBlue,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "Safety Check Demo",
                    color = SafetyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (safetyCheckDemoSuccess) {
                        Text(
                            "✓ Safe Check-in Confirmed!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusApproved
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "In an actual scenario, confirming safety prevents any emergency alerts from firing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    } else if (safetyCheckDemoCountdown > 0) {
                        Text(
                            "Checking in... $safetyCheckDemoCountdown s",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SafetyPrimaryBlue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Simulation Mode: Tap 'I'm Safe' before time expires to complete this check-in demo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            "⚠️ Check-in Expired!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusDenied
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No response received. In real-world operation, SMS broadcasts and live location tracking will now notify guardians.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                if (!safetyCheckDemoSuccess && safetyCheckDemoCountdown > 0) {
                    SafetyPillButton(
                        text = "I'm Safe",
                        onClick = { safetyCheckDemoSuccess = true }
                    )
                } else {
                    SafetyPillButton(
                        text = "Done",
                        onClick = {
                            showSafetyCheckDemoDialog = false
                            safetyCheckDemoSuccess = false
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSafetyCheckDemoDialog = false
                    safetyCheckDemoSuccess = false
                }) {
                    Text("Close", color = SafetyTextMuted)
                }
            }
        )
    }

    var showSOSDemoDialog by remember { mutableStateOf(false) }
    var sosDemoCountdown by remember { mutableIntStateOf(10) }
    var sosDemoTriggered by remember { mutableStateOf(false) }
    var demoCallMade by remember { mutableStateOf(false) }
    var demoSmsSent by remember { mutableIntStateOf(0) }
    var isExecutingDemo by remember { mutableStateOf(false) }

    // ─── Real Emergency SOS Confirmation Dialog ───────────────
    if (showConfirmDialog) {
        LaunchedEffect(showConfirmDialog) {
            confirmCountdown = 3
            while (confirmCountdown > 0) {
                delay(1000)
                confirmCountdown -= 1
            }
            showConfirmDialog = false
            sosViewModel.triggerSOS(context, SOSTriggerMethod.BUTTON) { eventId ->
                onNavigateToAlert(eventId)
            }
        }

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = SafetyCardBackground,
            icon = {
                Icon(
                    Icons.Filled.CrisisAlert,
                    contentDescription = null,
                    tint = StatusDenied,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "Trigger Emergency SOS?",
                    color = SafetyTextPrimary,
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
                        "This will immediately auto-call your primary contact, send emergency location SMS, sound siren alarms, and broadcast GPS coordinates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                SafetyPillButton(
                    text = "TRIGGER NOW",
                    onClick = {
                        showConfirmDialog = false
                        sosViewModel.triggerSOS(context, SOSTriggerMethod.BUTTON) { eventId ->
                            onNavigateToAlert(eventId)
                        }
                    },
                    containerColor = StatusDenied,
                    contentColor = Color.White
                )
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("CANCEL", color = SafetyTextMuted)
                }
            }
        )
    }

    // ─── Emergency SOS Demo Dialog (10s with auto-call & location SMS [DEMO TRY]) ───
    if (showSOSDemoDialog) {
        LaunchedEffect(showSOSDemoDialog, sosDemoTriggered) {
            if (!sosDemoTriggered) {
                sosDemoCountdown = 10
                while (sosDemoCountdown > 0 && !sosDemoTriggered) {
                    delay(1000)
                    sosDemoCountdown -= 1
                }
                if (sosDemoCountdown == 0 && !sosDemoTriggered) {
                    isExecutingDemo = true
                    sosViewModel.triggerDemoSOS(context) { call, count ->
                        demoCallMade = call
                        demoSmsSent = count
                        sosDemoTriggered = true
                        isExecutingDemo = false
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                SoundManager.stopSiren()
                showSOSDemoDialog = false
                sosDemoTriggered = false
                isExecutingDemo = false
            },
            containerColor = SafetyCardBackground,
            icon = {
                Icon(
                    Icons.Filled.CrisisAlert,
                    contentDescription = null,
                    tint = StatusDenied,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "Emergency SOS Demo",
                    color = SafetyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (sosDemoTriggered) {
                        Text(
                            "✓ Demo Successfully Dispatched!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusApproved
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Demo Test Completed:\n• Auto-call: ${if (demoCallMade) "Call initiated to primary contact" else "Completed (check phone call permission)"}\n• SMS alerts: Sent with [DEMO TRY] tag to emergency contacts.\n\nYour contacts know this was only a safety demo test.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    } else if (isExecutingDemo) {
                        CircularProgressIndicator(color = StatusDenied)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Dispatching demo auto-call & test location SMS...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SafetyTextPrimary
                        )
                    } else {
                        Text(
                            "Testing in $sosDemoCountdown seconds...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = StatusDenied
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This demo waits 10 seconds, then initiates an auto-call and dispatches a test location SMS with [DEMO TRY] tag to your trusted contacts so you can verify reception without causing panic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                if (!sosDemoTriggered && !isExecutingDemo) {
                    SafetyPillButton(
                        text = "Trigger Demo Now",
                        onClick = {
                            isExecutingDemo = true
                            sosViewModel.triggerDemoSOS(context) { call, count ->
                                demoCallMade = call
                                demoSmsSent = count
                                sosDemoTriggered = true
                                isExecutingDemo = false
                            }
                        },
                        containerColor = StatusDenied,
                        contentColor = Color.White
                    )
                } else if (sosDemoTriggered) {
                    SafetyPillButton(
                        text = "Finish Demo",
                        onClick = {
                            SoundManager.stopSiren()
                            showSOSDemoDialog = false
                            sosDemoTriggered = false
                            isExecutingDemo = false
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    SoundManager.stopSiren()
                    showSOSDemoDialog = false
                    sosDemoTriggered = false
                    isExecutingDemo = false
                }) {
                    Text(if (sosDemoTriggered) "Close" else "Cancel", color = SafetyTextMuted)
                }
            }
        )
    }

    val targetUid = profileState.userProfile?.currentWeeklyUid
        ?: profileState.userProfile?.authUserId ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SafetyDarkBackground)
    ) {
        // Top App Bar matching Google Personal Safety Image 3
        SafetyTopAppBar(
            title = "Features",
            onNotificationClick = { /* Notifications */ },
            onProfileClick = { /* Profile */ }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ─── 1. Safety Check Card (Image 3) ──────────────────────
            SafetyFeatureCard(
                title = "Safety Check",
                description = "Set a check-in timer for your phone to confirm you're safe. If you don't respond, alerts are triggered.",
                icon = Icons.Filled.Shield,
                onClick = onNavigateToProactivePrompt,
                secondaryButtonText = "Try a demo",
                onSecondaryButtonClick = {
                    safetyCheckDemoCountdown = 10
                    safetyCheckDemoSuccess = false
                    showSafetyCheckDemoDialog = true
                },
                primaryButtonText = "Start check",
                onPrimaryButtonClick = onNavigateToProactivePrompt
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 2. Emergency Sharing Card (Image 3) ─────────────────
            SafetyFeatureCard(
                title = "Emergency Sharing",
                description = "Share your real-time location and critical info with your emergency contacts.",
                icon = Icons.Filled.ShareLocation,
                onClick = {
                    if (targetUid.isNotBlank()) onNavigateToLiveMap(targetUid)
                    else onNavigateToTravelMode()
                },
                secondaryButtonText = "Travel Mode",
                onSecondaryButtonClick = onNavigateToTravelMode,
                primaryButtonText = "Open live map",
                onPrimaryButtonClick = {
                    if (targetUid.isNotBlank()) onNavigateToLiveMap(targetUid)
                    else onNavigateToTravelMode()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 3. Emergency SOS Card (Image 3) ─────────────────────
            SafetyFeatureCard(
                title = "Emergency SOS",
                description = "Press the Power button quickly 3 times or shake your device to call for help, send SMS, and share location.",
                icon = Icons.Filled.CrisisAlert,
                onClick = {
                    confirmCountdown = 3
                    showConfirmDialog = true
                },
                secondaryButtonText = "Try a demo",
                onSecondaryButtonClick = {
                    sosDemoCountdown = 10
                    sosDemoTriggered = false
                    showSOSDemoDialog = true
                },
                primaryButtonText = "Trigger SOS",
                onPrimaryButtonClick = {
                    confirmCountdown = 3
                    showConfirmDialog = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 4. Safety ID & Radar Sync Card (Trinetra) ───────────
            SafetyFeatureCard(
                title = "Safety ID & Sync",
                description = "Coordinates sync to cloud radar every 10 seconds. Weekly Safety UID rotates automatically for privacy.",
                icon = Icons.Filled.Key,
                onClick = {
                    if (targetUid.isNotBlank()) onNavigateToLiveMap(targetUid)
                },
                primaryButtonText = "Open Radar Map",
                onPrimaryButtonClick = {
                    if (targetUid.isNotBlank()) onNavigateToLiveMap(targetUid)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Recent SOS Incidents Section ────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent incidents",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = SafetyTextPrimary
                )

                if (sosHistory.isNotEmpty()) {
                    TextButton(onClick = onNavigateToHistory) {
                        Text(
                            "Full history",
                            color = SafetyPrimaryBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (sosHistory.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SafetyCardBackground)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No emergency incidents recorded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SafetyTextMuted
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SafetyCardBackground)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        sosHistory.take(3).forEachIndexed { index, event ->
                            if (index > 0) {
                                HorizontalDivider(color = SafetyCardDivider)
                            }
                            SafetyListRow(
                                title = DateUtils.formatDateTime(event.triggeredAt),
                                subtitle = "Trigger: ${event.triggerMethod.name} • Status: ${event.status.name}",
                                icon = when (event.status) {
                                    SOSStatus.ACTIVE -> Icons.Filled.Warning
                                    SOSStatus.RESOLVED -> Icons.Filled.CheckCircle
                                    else -> Icons.Filled.Info
                                },
                                iconTint = when (event.status) {
                                    SOSStatus.ACTIVE -> StatusDenied
                                    SOSStatus.RESOLVED -> StatusApproved
                                    else -> StatusPending
                                },
                                onClick = { onNavigateToHistoryDetail(event.id) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
