package com.womensafety.system.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.data.model.SafetyMode
import com.womensafety.system.manager.BleHeartRateManager
import com.womensafety.system.manager.JourneyState
import com.womensafety.system.manager.SafetyMonitoringManager
import com.womensafety.system.manager.TravelModeManager
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.TelemetryPill
import com.womensafety.system.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.system.ui.safety.RoutineMonitoringCard
import com.womensafety.system.viewmodel.RoutineMonitoringViewModel
import com.womensafety.system.utils.Constants
import com.womensafety.system.viewmodel.UserHomeViewModel
import java.util.Calendar

/**
 * Backward-compatible GlassCard utilizing theme-adaptive elevated surface colors.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentGradient: Brush? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(
        modifier = modifier,
        accentGradient = accentGradient,
        content = content
    )
}

@Composable
fun UserHomeScreen(
    viewModel: UserHomeViewModel,
    onNavigateToLocationSettings: () -> Unit = {},
    onNavigateToShakeSettings: () -> Unit = {},
    onNavigateToSafety: () -> Unit = {},
    onNavigateToTravelMode: () -> Unit = {},
    onNavigateToLiveMap: (String) -> Unit = {},
    onNavigateToHeartRate: () -> Unit = {},
    onNavigateToContacts: () -> Unit = {},
    onNavigateToRoutine: () -> Unit = {},
    routineViewModel: RoutineMonitoringViewModel = viewModel()
) {
    val state by viewModel.homeState.collectAsState()
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current

    val bleStatus by BleHeartRateManager.connectionStatus.collectAsState()
    val bleBpm by BleHeartRateManager.heartRateBpm.collectAsState()
    val hcMeasurement by com.womensafety.system.data.health.HealthConnectManager.latestMeasurement.collectAsState()
    val isBleConnected = bleStatus.startsWith("Connected") || bleStatus.startsWith("Reading")
    val effectiveBpm = bleBpm ?: hcMeasurement?.bpm

    LaunchedEffect(Unit) {
        viewModel.loadHomeData()
        BleHeartRateManager.init(context)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.womensafety.system.data.health.HealthConnectManager.queryHeartRateRecords(context)
        }
    }

    LaunchedEffect(state.userProfile?.locationTrackingEnabled, state.currentUid?.uid) {
        val uid = state.currentUid?.uid
        if (!uid.isNullOrBlank()) {
            LocationTrackingService.setTargetUid(uid, state.currentUid?.userName ?: "")
        }
        if (state.userProfile != null && state.userProfile?.locationTrackingEnabled != false && !LocationTrackingService.isRunning) {
            LocationTrackingService.startTracking(context)
        }
    }

    if (state.isLoading && state.userProfile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
        return
    }

    // Time-based warm greeting
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour in 5..11 -> "Good morning"
        hour in 12..16 -> "Good afternoon"
        hour in 17..21 -> "Good evening"
        else -> "Stay safe tonight"
    }

    // Breathing pulse animation for live hero shield
    val infiniteTransition = rememberInfiniteTransition(label = "ShieldPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF131627), BackgroundDark)
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // ─── 1. Modern Top Header ──────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (state.userProfile?.name?.firstOrNull()?.uppercase() ?: "U"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.userProfile?.name ?: "User",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Trinetra Shield",
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ─── 2. Hero Safety Radar & Live Telemetry Hub ─────────────
        val isTrackingActive = state.userProfile?.locationTrackingEnabled != false
        val isShakeActive = state.userProfile?.shakeDetectionEnabled == true
        val hasAdequateGuardians = state.trustedContactCount >= Constants.MIN_TRUSTED_CONTACTS
        val isFullyProtected = isTrackingActive && isShakeActive && hasAdequateGuardians

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            accentGradient = Brush.verticalGradient(
                colors = if (isFullyProtected) {
                    listOf(StatusApproved.copy(alpha = 0.12f), Color.Transparent)
                } else {
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), Color.Transparent)
                }
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Breathing Shield Ring
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                if (isFullyProtected) StatusApproved.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFullyProtected) StatusApproved.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFullyProtected) Icons.Filled.VerifiedUser else Icons.Filled.Shield,
                            contentDescription = null,
                            tint = if (isFullyProtected) StatusApproved else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isFullyProtected) StatusApproved else AmberWarning)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFullyProtected) "ACTIVE & PROTECTED" else "ATTENTION RECOMMENDED",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isFullyProtected) StatusApproved else AmberWarning
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isFullyProtected) "Real-time sensor telemetry & automated SOS armed"
                    else "Pair at least ${Constants.MIN_TRUSTED_CONTACTS} guardians and enable sensors for full shield",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Telemetry Status 2x2 Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryPill(
                            text = if (isTrackingActive) "GPS Synced" else "GPS Off",
                            dotColor = if (isTrackingActive) StatusApproved else EmergencyRed,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryPill(
                            text = if (isShakeActive) "Shake ${state.userProfile?.shakeSensitivity ?: "MED"}" else "Shake Off",
                            dotColor = if (isShakeActive) SecondaryViolet else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryPill(
                            text = if (effectiveBpm != null) "$effectiveBpm BPM" else "Watch BLE",
                            dotColor = if (effectiveBpm != null || isBleConnected) StatusApproved else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryPill(
                            text = "${state.trustedContactCount} Guardians",
                            dotColor = if (hasAdequateGuardians) PrimaryIndigo else AmberWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ─── 3. PRIMARY EMERGENCY SOS COMMAND CENTER ───────────────
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            accentGradient = Brush.horizontalGradient(
                colors = listOf(EmergencyRed.copy(alpha = 0.20f), EmergencyRed.copy(alpha = 0.05f))
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sos,
                            contentDescription = "SOS",
                            tint = EmergencyRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Safety Center & SOS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Instant cellular SMS alerts, siren alarms, and GPS broadcasting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onNavigateToSafety,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sos,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "OPEN SAFETY CENTER",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ─── 4. Tactile Situational Safety Mode Selector ───────────
        val activeSafetyModeStr = state.userProfile?.activeSafetyMode ?: "REGULAR"
        val activeMode = SafetyMode.fromString(activeSafetyModeStr)
        val modeSuggestion by SafetyMonitoringManager.modeSuggestion.collectAsState()

        // Mode suggestion banner if smart detector triggers
        if (modeSuggestion != null && state.userProfile?.autoModeSuggestionsEnabled == true) {
            val suggestion = modeSuggestion!!
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                    )
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mode Suggestion: ${suggestion.displayName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Detected movement patterns suggest switching mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { SafetyMonitoringManager.dismissSuggestion() }) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.updateActiveSafetyMode(suggestion) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Switch Mode", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        Text(
            text = "ACTIVE SAFETY MODE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Segmented Hardware-style Mode Switcher
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${activeMode.iconEmoji} ${activeMode.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = activeMode.config.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Selector Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SafetyMode.values().forEach { modeOption ->
                        val isSelected = activeMode == modeOption
                        val chipBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(chipBg)
                                .clickable { viewModel.updateActiveSafetyMode(modeOption) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = modeOption.iconEmoji,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = modeOption.displayName.split("/")[0],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ─── 5. Contextual Safety Features (Travel / Work / Sleep) ─
        when (activeMode) {
            SafetyMode.TRAVELLING -> {
                ProactiveSafeTripCard(onNavigateToTravelMode)
                Spacer(modifier = Modifier.height(16.dp))
            }
            SafetyMode.WORKING -> {
                WorkingModeView(
                    userProfile = state.userProfile,
                    viewModel = viewModel,
                    onNavigateToSafety = onNavigateToSafety
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            SafetyMode.SLEEPING -> {
                SleepingModeView(
                    userProfile = state.userProfile,
                    viewModel = viewModel,
                    onNavigateToSafety = onNavigateToSafety
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            SafetyMode.REGULAR -> {
                RoutineMonitoringCard(
                    viewModel = routineViewModel,
                    onNavigateToDetails = onNavigateToRoutine,
                    onTriggerSOS = onNavigateToSafety
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ─── 6. Hardware & Telemetry Grid (Location & Shake) ────────
        Text(
            text = "HARDWARE & PROTECTION SENSORS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Location Sensor Card
            AppCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PrimaryIndigo.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "GPS Stream",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (state.userProfile?.locationTrackingEnabled == true) "Active sync" else "Inactive",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.userProfile?.locationTrackingEnabled == true) StatusApproved else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            val target = state.currentUid?.uid?.ifBlank { state.userProfile?.currentWeeklyUid ?: state.userProfile?.authUserId ?: "" } ?: ""
                            onNavigateToLiveMap(target)
                        }
                    ) {
                        Text("Map", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(
                        contentPadding = PaddingValues(0.dp),
                        onClick = onNavigateToLocationSettings
                    ) {
                        Text("Config", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Shake Sensor Card
            AppCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SecondaryViolet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Vibration,
                        contentDescription = null,
                        tint = SecondaryViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Shake Sensor",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (state.userProfile?.shakeDetectionEnabled == true) "${state.userProfile?.shakeSensitivity} level" else "Inactive",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.userProfile?.shakeDetectionEnabled == true) StatusApproved else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    contentPadding = PaddingValues(0.dp),
                    onClick = onNavigateToShakeSettings
                ) {
                    Text("Configure", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SecondaryViolet)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Smartwatch / BLE Heart Rate Telemetry Card
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToHeartRate() },
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    EmergencyRed.copy(alpha = 0.12f),
                    SecondaryViolet.copy(alpha = 0.06f)
                )
            ),
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = EmergencyRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smartwatch & Vital Pulse",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isBleConnected && bleBpm != null) {
                            "$bleBpm BPM (Live GATT stream)"
                        } else if (hcMeasurement?.bpm != null) {
                            "${hcMeasurement!!.bpm} BPM (Health Connect / Fit)"
                        } else if (isBleConnected) {
                            bleStatus
                        } else {
                            "Direct BLE 0x180D & Health Connect"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (effectiveBpm != null || isBleConnected) StatusApproved else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onNavigateToHeartRate,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Vitals",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ─── 7. Unified Guardian Circle & Safety UID Panel ─────────
        Text(
            text = "GUARDIAN MESH & SAFETY IDENTITY",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PrimaryIndigo.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Guardian Circle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (state.trustedContactCount < Constants.MIN_TRUSTED_CONTACTS) "Requires at least ${Constants.MIN_TRUSTED_CONTACTS} guardians"
                        else "Protected by active guardian circle",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.trustedContactCount < Constants.MIN_TRUSTED_CONTACTS) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = PrimaryIndigo.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${state.trustedContactCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryIndigo,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (state.pendingRequestCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    onClick = onNavigateToContacts,
                    shape = RoundedCornerShape(10.dp),
                    color = AmberWarning.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AmberWarning
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${state.pendingRequestCount} pending pairing request(s) • Tap to review",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberWarning,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AmberWarning
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            // Current 12-Digit Safety UID
            val currentUidStr = state.currentUid?.uid ?: ""
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CURRENT SAFETY UID",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (currentUidStr.isNotBlank()) currentUidStr else "Generating…",
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Rotates weekly for privacy • Syncs location live",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val target = currentUidStr.ifBlank { state.userProfile?.currentWeeklyUid ?: state.userProfile?.authUserId ?: "" }
                        onNavigateToLiveMap(target)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OPEN LIVE RADAR MAP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Generous bottom clearance to prevent bottom nav bar from covering anything
        Spacer(modifier = Modifier.height(84.dp))
    }
}

@Composable
fun ProactiveSafeTripCard(onNavigateToTravelMode: () -> Unit) {
    val riskScore by SafetyMonitoringManager.riskScore.collectAsState()
    val journeyState by TravelModeManager.journeyState.collectAsState()

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        accentGradient = Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
            )
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Safe Voyage Route Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (journeyState != JourneyState.NOT_STARTED) "Trip Active • Risk Level: $riskScore"
                    else "Real-time deviation tracking & route hazard alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (journeyState != JourneyState.NOT_STARTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onNavigateToTravelMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (journeyState != JourneyState.NOT_STARTED) "OPEN ACTIVE JOURNEY MAP"
                else "ENTER TRAVEL ROUTE MODE",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
