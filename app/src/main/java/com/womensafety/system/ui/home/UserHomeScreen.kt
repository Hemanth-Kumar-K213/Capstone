package com.womensafety.system.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.Constants
import com.womensafety.system.viewmodel.UserHomeViewModel
import androidx.compose.ui.platform.LocalContext
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.model.SafetyMode
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.manager.SafetyMonitoringManager
import com.womensafety.system.manager.TravelModeManager
import com.womensafety.system.manager.JourneyState

@Composable
fun UserHomeScreen(
    viewModel: UserHomeViewModel,
    onNavigateToLocationSettings: () -> Unit = {},
    onNavigateToShakeSettings: () -> Unit = {},
    onNavigateToSafety: () -> Unit = {},
    onNavigateToTravelMode: () -> Unit = {},
    onNavigateToLiveMap: (String) -> Unit = {}
) {
    val state by viewModel.homeState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    if (state.isLoading) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = GradientAmber)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientDeepPurple.copy(alpha = 0.6f), BackgroundDark)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Header with logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Welcome back,",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariantDark
                )
                Text(
                    state.userProfile?.name ?: "User",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        letterSpacing = 1.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = GradientGold
                )
            }
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Account Status — Glass card
        GlassCard {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(StatusApproved.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = StatusApproved,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Account Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariantDark
                    )
                    Text(
                        "✓ Verified & Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusApproved
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Safety Mode Selector
        val activeSafetyModeStr = state.userProfile?.activeSafetyMode ?: "REGULAR"
        val activeMode = SafetyMode.fromString(activeSafetyModeStr)
        val modeSuggestion by SafetyMonitoringManager.modeSuggestion.collectAsState()

        // Suggestion Card (if active and suggestions enabled)
        if (modeSuggestion != null && state.userProfile?.autoModeSuggestionsEnabled == true) {
            val suggestion = modeSuggestion!!
            GlassCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(
                        GradientAmber.copy(alpha = 0.15f),
                        GradientGold.copy(alpha = 0.05f)
                    )
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GradientAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = GradientAmber
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Safety Mode Suggestion",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceDark
                            )
                            Text(
                                "You appear to be ${if (suggestion == SafetyMode.TRAVELLING) "travelling" else "at your saved location"}. Switch to ${suggestion.displayName}?",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantDark
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { SafetyMonitoringManager.dismissSuggestion() }) {
                            Text("Dismiss", color = OnSurfaceVariantDark)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.updateActiveSafetyMode(suggestion) },
                            colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Switch", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Active Mode Selector Card
        GlassCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    GradientDeepPurple.copy(alpha = 0.1f),
                    BackgroundDark.copy(alpha = 0.05f)
                )
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CURRENT OPERATING MODE",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariantDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${activeMode.iconEmoji} ${activeMode.displayName}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = GradientGold
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    activeMode.config.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Selector Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SafetyMode.values().forEach { modeOption ->
                        val isSelected = activeMode == modeOption
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) GradientDeepPurple.copy(alpha = 0.4f)
                                    else SurfaceVariantDark.copy(alpha = 0.2f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) GradientAmber else GlassBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.updateActiveSafetyMode(modeOption) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    modeOption.iconEmoji,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    modeOption.displayName.split("/")[0],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GradientGold else OnSurfaceVariantDark
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (activeMode) {
            SafetyMode.REGULAR -> {
                // Location Tracking Status Card
                GlassCard(
                    accentGradient = Brush.horizontalGradient(
                        colors = listOf(GradientAmber.copy(alpha = 0.08f), GradientGold.copy(alpha = 0.04f))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(GradientAmber.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = GradientAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Location Tracking",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceDark
                                )
                                Text(
                                    if (state.userProfile?.locationTrackingEnabled == true) "Active • Sharing coordinates"
                                    else "Inactive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.userProfile?.locationTrackingEnabled == true) StatusApproved else OnSurfaceVariantDark
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        val target = state.currentUid?.uid?.ifBlank { state.userProfile?.currentWeeklyUid ?: state.userProfile?.authUserId ?: "" } ?: ""
                                        onNavigateToLiveMap(target)
                                    }
                                ) {
                                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp), tint = GradientAmber)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Map", color = GradientAmber, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = onNavigateToLocationSettings) {
                                    Text("Configure", color = OnSurfaceVariantDark, fontWeight = FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Shake Detection Status Card
                GlassCard(
                    accentGradient = Brush.horizontalGradient(
                        colors = listOf(GradientRose.copy(alpha = 0.08f), GradientMagenta.copy(alpha = 0.04f))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(GradientRose.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Vibration,
                                    contentDescription = null,
                                    tint = GradientRose,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Shake Detection",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceDark
                                )
                                Text(
                                    if (state.userProfile?.shakeDetectionEnabled == true) "Active • Sensitivity ${state.userProfile?.shakeSensitivity}"
                                    else "Inactive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.userProfile?.shakeDetectionEnabled == true) StatusApproved else OnSurfaceVariantDark
                                )
                            }
                            TextButton(onClick = onNavigateToShakeSettings) {
                                Text("Configure", color = GradientRose, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TrustedContactsCard(state)

                Spacer(modifier = Modifier.height(16.dp))

                SafetyUidCard(state, onNavigateToLiveMap)

                Spacer(modifier = Modifier.height(16.dp))

                SafetyCenterCard(onNavigateToSafety)
            }
            SafetyMode.TRAVELLING -> {
                ProactiveSafeTripCard(onNavigateToTravelMode)

                Spacer(modifier = Modifier.height(16.dp))

                TrustedContactsCard(state)

                Spacer(modifier = Modifier.height(16.dp))

                SafetyUidCard(state, onNavigateToLiveMap)

                Spacer(modifier = Modifier.height(16.dp))

                SafetyCenterCard(onNavigateToSafety)
            }
            SafetyMode.WORKING -> {
                WorkingModeView(
                    userProfile = state.userProfile,
                    viewModel = viewModel,
                    onNavigateToSafety = onNavigateToSafety
                )

                Spacer(modifier = Modifier.height(16.dp))

                TrustedContactsCard(state)

                Spacer(modifier = Modifier.height(16.dp))

                SafetyCenterCard(onNavigateToSafety)
            }
            SafetyMode.SLEEPING -> {
                SleepingModeView(
                    userProfile = state.userProfile,
                    viewModel = viewModel,
                    onNavigateToSafety = onNavigateToSafety
                )

                Spacer(modifier = Modifier.height(16.dp))

                TrustedContactsCard(state)

                Spacer(modifier = Modifier.height(16.dp))

                SafetyCenterCard(onNavigateToSafety)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Reusable glass-morphism card component with optional gradient accent.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentGradient: Brush? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(GlassBorder, GlassBorder.copy(alpha = 0.1f))
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariantDark
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (accentGradient != null) Modifier.background(accentGradient)
                    else Modifier
                )
        ) {
            content()
        }
    }
}

@Composable
fun ProactiveSafeTripCard(onNavigateToTravelMode: () -> Unit) {
    val riskScore by SafetyMonitoringManager.riskScore.collectAsState()
    val journeyState by TravelModeManager.journeyState.collectAsState()

    GlassCard(
        accentGradient = Brush.horizontalGradient(
            colors = listOf(
                GradientAmber.copy(alpha = 0.1f),
                GradientGold.copy(alpha = 0.05f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GradientAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = GradientAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Proactive Travel Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )
                    Text(
                        if (journeyState != JourneyState.NOT_STARTED) "Trip State: $journeyState • Risk: $riskScore"
                        else "Plan and monitor your route dynamically",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (journeyState != JourneyState.NOT_STARTED) GradientAmber else OnSurfaceVariantDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onNavigateToTravelMode,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    if (journeyState != JourneyState.NOT_STARTED) "OPEN ACTIVE JOURNEY MAP"
                    else "ENTER TRAVEL MODE",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Earth's radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

@Composable
private fun TrustedContactsCard(state: UserHomeViewModel.HomeState) {
    GlassCard(
        accentGradient = Brush.horizontalGradient(
            colors = listOf(
                GradientRose.copy(alpha = 0.1f),
                GradientMagenta.copy(alpha = 0.05f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GradientRose.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Contacts,
                        contentDescription = null,
                        tint = GradientRose
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Trusted Contacts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )
                    if (state.trustedContactCount < Constants.MIN_TRUSTED_CONTACTS) {
                        Text(
                            "Add at least ${Constants.MIN_TRUSTED_CONTACTS} contacts",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusPending
                        )
                    }
                }
                Text(
                    "${state.trustedContactCount}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = GradientRose
                )
            }
            if (state.pendingRequestCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = StatusPending
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${state.pendingRequestCount} pending request(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusPending
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyUidCard(
    state: UserHomeViewModel.HomeState,
    onNavigateToLiveMap: (String) -> Unit = {}
) {
    val currentUidStr = state.currentUid?.uid ?: ""
    GlassCard(
        accentGradient = Brush.horizontalGradient(
            colors = listOf(
                GradientAmber.copy(alpha = 0.1f),
                GradientGold.copy(alpha = 0.05f)
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "CURRENT SAFETY ID",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 3.sp
                ),
                color = OnSurfaceVariantDark
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                if (currentUidStr.isNotBlank()) currentUidStr else "Generating…",
                style = MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = 4.sp
                ),
                fontWeight = FontWeight.Bold,
                color = GradientGold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Syncs to child_locations every 10 seconds",
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color(0xFF10B981),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val target = currentUidStr.ifBlank { state.userProfile?.currentWeeklyUid ?: state.userProfile?.authUserId ?: "" }
                    onNavigateToLiveMap(target)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GradientAmber
                )
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = androidx.compose.ui.graphics.Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "OPEN LIVE RADAR MAP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Black
                )
            }
        }
    }
}

@Composable
private fun SafetyCenterCard(onNavigateToSafety: () -> Unit) {
    GlassCard(
        accentGradient = Brush.horizontalGradient(
            colors = listOf(StatusDenied.copy(alpha = 0.15f), StatusDenied.copy(alpha = 0.04f))
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(StatusDenied.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Sos,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = StatusDenied
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Safety Center & SOS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Instant cellular SMS alerts, siren alarms, and GPS broadcasting",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onNavigateToSafety,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatusDenied),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("OPEN SAFETY CENTER", fontWeight = FontWeight.Bold)
            }
        }
    }
}
