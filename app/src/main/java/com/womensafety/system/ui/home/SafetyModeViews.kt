package com.womensafety.system.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.womensafety.system.data.model.UserProfile
import com.womensafety.system.manager.TrinetraLocationManager
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.UserHomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.system.ui.safety.ColleagueAwarenessCard
import com.womensafety.system.ui.safety.SleepModeCard
import com.womensafety.system.viewmodel.ColleagueAwarenessViewModel
import com.womensafety.system.viewmodel.SleepModeViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WorkingModeView(
    userProfile: UserProfile?,
    viewModel: UserHomeViewModel,
    onNavigateToSafety: () -> Unit,
    onNavigateToBleDiagnostic: () -> Unit = {},
    colleagueViewModel: ColleagueAwarenessViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    if (!permissionState.allPermissionsGranted) {
        AppCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Location Access Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Working Mode requires precise location access to monitor your safety at work/college.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionState.launchMultiplePermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val curLocation by LocationTrackingService.currentLocation.collectAsState()
    var lastKnownLoc by remember { mutableStateOf<com.womensafety.system.data.model.UserLocation?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }

    val isGpsEnabled = remember { TrinetraLocationManager.isGpsEnabled(context) }

    LaunchedEffect(Unit) {
        val lat = userProfile?.savedWorkplaceLatitude ?: 0.0
        val lon = userProfile?.savedWorkplaceLongitude ?: 0.0
        android.util.Log.d("MODE", "WORKING mode activated (lat=$lat, lon=$lon)")
        colleagueViewModel.startMonitoring(lat, lon)
    }

    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.d("MODE", "WORKING mode deactivated")
            colleagueViewModel.stopMonitoring()
        }
    }

    LaunchedEffect(curLocation, isGpsEnabled) {
        if (!isGpsEnabled) {
            locationError = "GPS is disabled. Please turn on location services."
            val userId = userProfile?.authUserId ?: ""
            if (userId.isNotBlank()) {
                val fallback = TrinetraLocationManager.getImmediateLocationFix(context, userId)
                if (fallback != null) {
                    lastKnownLoc = fallback
                }
            }
        } else if (curLocation == null) {
            locationError = "Awaiting GPS fix..."
            val userId = userProfile?.authUserId ?: ""
            if (userId.isNotBlank()) {
                val fallback = TrinetraLocationManager.getImmediateLocationFix(context, userId)
                if (fallback != null) {
                    lastKnownLoc = fallback
                }
            }
        } else {
            locationError = null
            lastKnownLoc = curLocation
        }
    }

    val lastUpdatedTime = remember(lastKnownLoc) {
        lastKnownLoc?.timestamp?.let {
            SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(it))
        } ?: "Never"
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f)
                )
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Work,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Workplace / Campus Mode Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Interval: 15s • Updated: $lastUpdatedTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        LocationTrackingService.startTracking(context, 15000L)
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Location", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "DETECTED LOCATION:",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lastKnownLoc?.address ?: (locationError ?: "Resolving current address…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Saved Workplace status
                val workLat = userProfile?.savedWorkplaceLatitude
                val workLng = userProfile?.savedWorkplaceLongitude
                val workAddr = userProfile?.savedWorkplaceAddress

                if (workLat != null && workLng != null) {
                    val distance = if (lastKnownLoc != null) {
                        haversineDistance(lastKnownLoc!!.latitude, lastKnownLoc!!.longitude, workLat, workLng)
                    } else {
                        -1.0
                    }
                    val isAtWork = distance >= 0 && distance <= 200.0

                    Text(
                        text = if (isAtWork) "✓ INSIDE WORKPLACE SAFE ZONE" else "⚠️ OUTSIDE WORKPLACE SAFE ZONE",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAtWork) StatusApproved else StatusPending
                    )
                    if (distance >= 0 && !isAtWork) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", distance / 1000.0)} km away from designated safe zone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (locationError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Showing Last Known ($locationError)",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusPending
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Designated Workplace: ${workAddr ?: "($workLat, $workLng)"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "No saved workplace location configured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusPending
                    )
                }

                if (lastKnownLoc != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.saveWorkplaceLocation(
                                lastKnownLoc!!.latitude,
                                lastKnownLoc!!.longitude,
                                lastKnownLoc!!.address ?: "Workplace Location"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("SAVE CURRENT LOCATION AS WORKPLACE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        ColleagueAwarenessCard(
            viewModel = colleagueViewModel,
            onNavigateToBleDiagnostic = onNavigateToBleDiagnostic
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SleepingModeView(
    userProfile: UserProfile?,
    viewModel: UserHomeViewModel,
    onNavigateToSafety: () -> Unit,
    onNavigateToDiagnostic: () -> Unit = {},
    sleepViewModel: SleepModeViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val isSleeping by sleepViewModel.isSleeping.collectAsState()
    LaunchedEffect(isSleeping) {
        com.womensafety.system.manager.HybridFallDetectionManager.setSleepModeActive(isSleeping)
    }

    if (!permissionState.allPermissionsGranted) {
        AppCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = SecondaryViolet,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Location Access Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Sleeping Mode requires location access to monitor safety status periodically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionState.launchMultiplePermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val curLocation by LocationTrackingService.currentLocation.collectAsState()
    var lastKnownLoc by remember { mutableStateOf<com.womensafety.system.data.model.UserLocation?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }

    val isGpsEnabled = remember { TrinetraLocationManager.isGpsEnabled(context) }

    LaunchedEffect(curLocation, isGpsEnabled) {
        if (!isGpsEnabled) {
            locationError = "GPS is disabled. Please turn on location services."
            val userId = userProfile?.authUserId ?: ""
            if (userId.isNotBlank()) {
                val fallback = TrinetraLocationManager.getImmediateLocationFix(context, userId)
                if (fallback != null) {
                    lastKnownLoc = fallback
                }
            }
        } else if (curLocation == null) {
            locationError = "Awaiting GPS fix..."
            val userId = userProfile?.authUserId ?: ""
            if (userId.isNotBlank()) {
                val fallback = TrinetraLocationManager.getImmediateLocationFix(context, userId)
                if (fallback != null) {
                    lastKnownLoc = fallback
                }
            }
        } else {
            locationError = null
            lastKnownLoc = curLocation
        }
    }

    val lastUpdatedTime = remember(lastKnownLoc) {
        lastKnownLoc?.timestamp?.let {
            SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(it))
        } ?: "Never"
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    SecondaryViolet.copy(alpha = 0.12f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                )
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SecondaryViolet.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.NightsStay,
                            contentDescription = null,
                            tint = SecondaryViolet,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Nighttime / Sleeping Mode Standby",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Interval: 60s • Updated: $lastUpdatedTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        LocationTrackingService.startTracking(context, 60000L)
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Location", tint = SecondaryViolet)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "DETECTED SLEEPING LOCATION:",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = SecondaryViolet
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lastKnownLoc?.address ?: (locationError ?: "Resolving current address…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Saved Home status
                val homeLat = userProfile?.savedHomeLatitude
                val homeLng = userProfile?.savedHomeLongitude
                val homeAddr = userProfile?.savedHomeAddress

                if (homeLat != null && homeLng != null) {
                    val distance = if (lastKnownLoc != null) {
                        haversineDistance(lastKnownLoc!!.latitude, lastKnownLoc!!.longitude, homeLat, homeLng)
                    } else {
                        -1.0
                    }
                    val isAtHome = distance >= 0 && distance <= 200.0

                    Text(
                        text = if (isAtHome) "✓ INSIDE HOME SAFE ZONE" else "⚠️ OUTSIDE HOME SAFE ZONE",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAtHome) StatusApproved else StatusPending
                    )
                    if (distance >= 0 && !isAtHome) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", distance / 1000.0)} km away from saved home address",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (locationError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Showing Last Known ($locationError)",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusPending
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Designated Home: ${homeAddr ?: "($homeLat, $homeLng)"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "No saved home/sleeping location configured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusPending
                    )
                }

                if (lastKnownLoc != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.saveHomeLocation(
                                lastKnownLoc!!.latitude,
                                lastKnownLoc!!.longitude,
                                lastKnownLoc!!.address ?: "Home Location"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryViolet),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("SAVE CURRENT LOCATION AS HOME", fontWeight = FontWeight.Bold, color = SecondaryViolet, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        SleepModeCard(
            viewModel = sleepViewModel,
            onTriggerSOS = onNavigateToSafety,
            onNavigateToDiagnostic = onNavigateToDiagnostic
        )
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
