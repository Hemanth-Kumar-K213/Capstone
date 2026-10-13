package com.womensafety.system.ui.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.SecondaryButton
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
    val liveLocation by locationViewModel.observeLocationByUserIdOrUid(userId).collectAsState(initial = null)
    val activeSOSEvent by sosViewModel.observeActiveSOS(userId).collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Emergency Alert Received",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmergencyRed
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            EmergencyRed.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Urgent Alert Card
            Surface(
                color = EmergencyRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "🚨 ${liveLocation?.userName?.ifBlank { "Connected User" } ?: "Connected User"} NEEDS HELP!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = EmergencyRed
                        )

                        if (activeSOSEvent != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Triggered: ${DateUtils.formatDateTime(activeSOSEvent!!.triggeredAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // OpenStreetMap (OSMDroid) Live Map Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (liveLocation != null && (liveLocation!!.latitude != 0.0 || liveLocation!!.longitude != 0.0)) {
                    SafetyMapTracker(
                        userLatitude = liveLocation!!.latitude,
                        userLongitude = liveLocation!!.longitude,
                        zoomLevel = 16.5,
                        markerTitle = "🚨 ${liveLocation!!.userName.ifBlank { "User" }} (SOS)",
                        uid = liveLocation?.uid?.ifBlank { userId } ?: userId,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = EmergencyRed, strokeWidth = 3.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Address & Accuracy Info
            if (liveLocation != null) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "CURRENT LOCATION",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!liveLocation!!.address.isNullOrBlank()) {
                        Text(
                            liveLocation!!.address!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        "Lat: ${String.format("%.5f", liveLocation!!.latitude)}, Lng: ${String.format("%.5f", liveLocation!!.longitude)} (±${liveLocation!!.accuracy.toInt()}m)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            PrimaryButton(
                text = "Start Turn-by-Turn Navigation",
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
                enabled = liveLocation != null,
                icon = Icons.Filled.Navigation
            )

            Spacer(modifier = Modifier.height(12.dp))

            SecondaryButton(
                text = "Dismiss Alert",
                onClick = onBack
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
