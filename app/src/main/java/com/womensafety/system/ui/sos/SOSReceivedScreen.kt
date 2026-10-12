package com.womensafety.system.ui.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.map.SafetyMapTracker
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.utils.MapUtils
import com.womensafety.system.viewmodel.LocationViewModel
import com.womensafety.system.viewmodel.SOSViewModel

/**
 * Screen displayed to Trusted Contacts when an SOS alert is received.
 * Renders real-time GPS coordinates using OpenStreetMap (OSMDroid).
 * 100% Free & Open-Source (No Google Maps API Key required).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSReceivedScreen(
    eventId: String,
    userId: String,
    locationViewModel: LocationViewModel,
    sosViewModel: SOSViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val liveLocation by locationViewModel.observeUserLocation(userId).collectAsState(initial = null)
    val activeSOSEvent by sosViewModel.observeActiveSOS(userId).collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Emergency Alert Received",
                        color = StatusDenied,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OnSurfaceDark
                        )
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            StatusDenied.copy(alpha = 0.2f),
                            BackgroundDark
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Urgent Alert Card
            GlassCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(StatusDenied.copy(alpha = 0.3f), StatusDenied.copy(alpha = 0.1f))
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(StatusDenied.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusDenied, modifier = Modifier.size(26.dp))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🚨 ${liveLocation?.userName?.ifBlank { "Connected User" } ?: "Connected User"} NEEDS HELP!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = StatusDenied
                            )

                            if (activeSOSEvent != null) {
                                Text(
                                    "Triggered: ${DateUtils.formatDateTime(activeSOSEvent!!.triggeredAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDark
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OpenStreetMap (OSMDroid) Live Map Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                if (liveLocation != null && (liveLocation!!.latitude != 0.0 || liveLocation!!.longitude != 0.0)) {
                    SafetyMapTracker(
                        userLatitude = liveLocation!!.latitude,
                        userLongitude = liveLocation!!.longitude,
                        zoomLevel = 16.5,
                        markerTitle = "🚨 ${liveLocation!!.userName.ifBlank { "User" }} (SOS)",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceVariantDark),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = StatusDenied)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Address & Accuracy Info
            if (liveLocation != null) {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "CURRENT LOCATION",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = GradientGold,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (!liveLocation!!.address.isNullOrBlank()) {
                            Text(
                                liveLocation!!.address!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Text(
                            "Lat: ${String.format("%.5f", liveLocation!!.latitude)}, Lng: ${String.format("%.5f", liveLocation!!.longitude)} (±${liveLocation!!.accuracy.toInt()}m)",
                            style = MaterialTheme.typography.bodySmall,
                            color = GradientAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Button(
                onClick = {
                    if (liveLocation != null) {
                        MapUtils.openExternalMap(
                            context = context,
                            latitude = liveLocation!!.latitude,
                            longitude = liveLocation!!.longitude,
                            label = "${liveLocation!!.userName} Emergency Location"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = liveLocation != null,
                colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("START TURN-BY-TURN NAVIGATION", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceDark)
            ) {
                Text("DISMISS ALERT", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
