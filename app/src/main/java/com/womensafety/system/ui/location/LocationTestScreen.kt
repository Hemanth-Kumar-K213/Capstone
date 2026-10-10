package com.womensafety.system.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.manager.TrinetraLocationManager
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.map.SafetyMapTracker
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import kotlinx.coroutines.launch

/**
 * Diagnostic and verification screen for GPS hardware, permissions, and OpenStreetMap (OSMDroid) readiness.
 * Demonstrates 100% free, zero-billing map operations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTestScreen(
    currentUserId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var locationResult by remember { mutableStateOf<UserLocation?>(null) }
    var errorReason by remember { mutableStateOf<String?>(null) }

    // Read initial status
    val isPermissionGranted = TrinetraLocationManager.isLocationPermissionGranted(context)
    val isGpsEnabled = TrinetraLocationManager.isGpsEnabled(context)
    val isPlayServicesAvailable = TrinetraLocationManager.isPlayServicesAvailable(context)
    val isOsmAvailable = TrinetraLocationManager.isOsmAvailable(context)

    fun performRefresh() {
        if (isRefreshing) return
        coroutineScope.launch {
            isRefreshing = true
            errorReason = null
            try {
                if (!isPermissionGranted) {
                    errorReason = "Location permission is not granted. Please enable it in settings."
                    locationResult = null
                } else if (!isGpsEnabled) {
                    errorReason = "GPS/Location provider is disabled on this device. Please turn on GPS."
                    locationResult = null
                } else {
                    val result = TrinetraLocationManager.getImmediateLocationFix(context, currentUserId)
                    if (result != null) {
                        locationResult = result
                    } else {
                        errorReason = "Failed to acquire location fix. Location returned null or timeout occurred."
                    }
                }
            } catch (e: Exception) {
                errorReason = "Exception occurred: ${e.localizedMessage}"
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        performRefresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location & OSM Diagnostics", color = OnSurfaceDark, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurfaceDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Diagnostics Summary
            GlassCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "SYSTEM & MAP DIAGNOSTICS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        fontWeight = FontWeight.Bold,
                        color = GradientAmber
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    StatusItem(
                        label = "Location Permissions",
                        status = if (isPermissionGranted) "Granted" else "Missing",
                        isGood = isPermissionGranted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StatusItem(
                        label = "GPS Hardware Status",
                        status = if (isGpsEnabled) "Available / Enabled" else "Unavailable / Disabled",
                        isGood = isGpsEnabled
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StatusItem(
                        label = "GPS Provider (Play Services)",
                        status = if (isPlayServicesAvailable) "Available" else "Unavailable",
                        isGood = isPlayServicesAvailable
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StatusItem(
                        label = "Map Engine (OSMDroid)",
                        status = if (isOsmAvailable) "Active & Open Source" else "Disabled",
                        isGood = isOsmAvailable
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StatusItem(
                        label = "Map API Key & Billing",
                        status = "100% Free (No Key Needed)",
                        isGood = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Coordinates Output Card
            GlassCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Active GPS Coordinate Fix",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isRefreshing) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GradientAmber)
                        }
                    } else if (errorReason != null) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = StatusDenied.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.GpsFixed, contentDescription = null, tint = StatusDenied)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Location Acquisition Failed",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = StatusDenied
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = errorReason!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceDark
                                    )
                                }
                            }
                        }
                    } else if (locationResult != null) {
                        val loc = locationResult!!
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CoordinateItem(label = "Latitude", value = "${loc.latitude}")
                            CoordinateItem(label = "Longitude", value = "${loc.longitude}")
                            CoordinateItem(label = "Accuracy", value = "±${loc.accuracy.toInt()} meters")
                            CoordinateItem(label = "Speed", value = loc.speed?.let { "${(it * 3.6f).toInt()} km/h" } ?: "Not moving")
                            CoordinateItem(label = "Timestamp", value = DateUtils.formatDateTime(loc.timestamp))
                            CoordinateItem(label = "Resolved Address", value = loc.address ?: "Reverse geocoding returned null")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Compact OpenStreetMap preview for active location fix
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            SafetyMapTracker(
                                userLatitude = loc.latitude,
                                userLongitude = loc.longitude,
                                zoomLevel = 16.0,
                                enableZoomControls = false,
                                markerTitle = "Current Location Fix"
                            )
                        }
                    } else {
                        Text(
                            text = "No location data queried yet. Tap Refresh below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariantDark,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { performRefresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isRefreshing,
                colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("REFRESH LOCATION", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, status: String, isGood: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantDark
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isGood) StatusApproved else StatusDenied
        )
    }
}

@Composable
private fun CoordinateItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantDark
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceDark
        )
    }
}
