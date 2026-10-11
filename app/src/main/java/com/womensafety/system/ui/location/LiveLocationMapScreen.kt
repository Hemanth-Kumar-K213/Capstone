package com.womensafety.system.ui.location

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.map.SafetyMapTracker
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.utils.MapUtils
import com.womensafety.system.viewmodel.LocationViewModel

/**
 * Live Location Map Screen using OpenStreetMap (OSMDroid).
 * 100% Free & Open-Source (No Google Maps API keys or billing needed).
 * Renders the live map immediately and streams user/child coordinates every 10 seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationMapScreen(
    userId: String,
    viewModel: LocationViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val liveLocation by viewModel.observeLocationByUserIdOrUid(userId).collectAsState(initial = null)
    val localServiceLocation by LocationTrackingService.currentLocation.collectAsState()

    // Ensure background tracking is active if tracking local user
    LaunchedEffect(Unit) {
        if (!LocationTrackingService.isRunning) {
            LocationTrackingService.startTracking(context)
        }
    }

    // Determine the most accurate available coordinate fix
    val effectiveLocation = liveLocation ?: localServiceLocation
    val hasFix = effectiveLocation != null && (effectiveLocation.latitude != 0.0 || effectiveLocation.longitude != 0.0)

    val lat = if (hasFix) effectiveLocation!!.latitude else 12.9716
    val lng = if (hasFix) effectiveLocation!!.longitude else 77.5946

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = effectiveLocation?.userName?.ifBlank { "Live Radar Map" } ?: "Live Radar Map",
                            color = OnSurfaceDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (hasFix) "10s GPS Sync Active" else "Acquiring GPS fix...",
                            color = if (hasFix) Color(0xFF10B981) else GradientAmber,
                            fontSize = 11.sp
                        )
                    }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark.copy(alpha = 0.92f))
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // OpenStreetMap (OSMDroid) Live Map Tracker is ALWAYS rendered immediately
            SafetyMapTracker(
                userLatitude = lat,
                userLongitude = lng,
                markerTitle = "${effectiveLocation?.userName?.ifBlank { "User" } ?: "Child"} (Live)",
                uid = effectiveLocation?.uid?.ifBlank { userId } ?: userId,
                bearing = effectiveLocation?.bearing,
                accuracy = effectiveLocation?.accuracy ?: 0f,
                isLive = true,
                modifier = Modifier.fillMaxSize()
            )

            // Top Status Pill if still awaiting first GPS fix
            if (!hasFix) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1B2E).copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GradientAmber.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = GradientAmber,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Connecting to 10s live GPS stream...",
                            fontSize = 12.sp,
                            color = GradientAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Floating Bottom Details Card
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                GlassCard(
                    accentGradient = Brush.horizontalGradient(
                        colors = listOf(GradientAmber.copy(alpha = 0.2f), GradientRose.copy(alpha = 0.15f))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(GradientAmber.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.PersonPinCircle,
                                    contentDescription = null,
                                    tint = GradientAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = effectiveLocation?.userName?.ifBlank { "Live Tracker" } ?: "Live Tracker",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceDark
                                )
                                Text(
                                    text = "UID: ${effectiveLocation?.uid?.ifBlank { userId } ?: userId} • Every 10s",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = GradientGold
                                )
                            }
                        }

                        if (!effectiveLocation?.address.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = OnSurfaceVariantDark,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = effectiveLocation?.address ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDark
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (hasFix) {
                                "Coordinates: ${String.format("%.5f", lat)}, ${String.format("%.5f", lng)} (±${effectiveLocation?.accuracy?.toInt() ?: 0}m)"
                            } else {
                                "Waiting for GPS satellite coordinate fix..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariantDark
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    MapUtils.openExternalMap(
                                        context = context,
                                        latitude = lat,
                                        longitude = lng,
                                        label = effectiveLocation?.userName?.ifBlank { "User Location" } ?: "User Location"
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("NAVIGATE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            OutlinedButton(
                                onClick = {
                                    val shareText = "Live location of ${effectiveLocation?.userName ?: "Child"}: https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng"
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Location"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceDark)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SHARE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
