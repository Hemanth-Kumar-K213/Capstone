package com.womensafety.system.ui.travel

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.manager.JourneyState
import com.womensafety.system.manager.TravelModeManager
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.TravelViewModel

/**
 * Active Safe Journey navigation HUD screen powered by OpenStreetMap (OSMDroid).
 * Renders real-time route polylines, current location tracking, speedometer,
 * deviation detection, and emergency escalation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveJourneyScreen(
    viewModel: TravelViewModel = viewModel(),
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val journeyState by viewModel.journeyState.collectAsState()
    val routePoints by viewModel.expectedRoute.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val destinationLocation by viewModel.destinationLocation.collectAsState()
    val deviationDistance by viewModel.currentDeviationDistance.collectAsState()
    val speedKmh by viewModel.speedKmh.collectAsState()
    val stopCount by viewModel.stopCount.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()

    val isPromptActive by viewModel.isDeviationPromptActive.collectAsState()
    val countdown by viewModel.deviationCountdownSeconds.collectAsState()

    // Initialize OSMDroid config
    DisposableEffect(Unit) {
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = context.packageName
        onDispose { }
    }

    // Prevent backing out of active journey without ending it
    BackHandler {
        // Must tap End Journey
    }

    // Auto-navigate to home if journey is not active
    LaunchedEffect(journeyState) {
        if (journeyState == JourneyState.NOT_STARTED || journeyState == JourneyState.COMPLETED) {
            onNavigateToHome()
        }
    }

    val isDeviated = deviationDistance > 300.0 // 300 meters

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }
    var destMarker by remember { mutableStateOf<Marker?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }

    // Re-draw map overlays whenever location, route, or deviation status changes
    LaunchedEffect(currentLocation, destinationLocation, routePoints, isDeviated, mapViewInstance) {
        mapViewInstance?.let { map ->
            map.overlays.clear()

            // Draw route polyline
            if (routePoints.isNotEmpty()) {
                val polyline = Polyline(map).apply {
                    setPoints(routePoints)
                    outlinePaint.color = if (isDeviated) android.graphics.Color.RED else android.graphics.Color.parseColor("#4F46E5")
                    outlinePaint.strokeWidth = 10f
                }
                map.overlays.add(polyline)
                routePolyline = polyline
            }

            // Draw destination marker
            destinationLocation?.let { dest ->
                val marker = Marker(map).apply {
                    position = dest
                    title = "Destination"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(marker)
                destMarker = marker
            }

            // Draw current location marker
            currentLocation?.let { current ->
                val marker = Marker(map).apply {
                    position = current
                    title = "Your Location (${speedKmh.toInt()} km/h)"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(marker)
                currentMarker = marker

                map.controller.animateTo(current)
            }

            map.invalidate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDeviated) EmergencyRed.copy(alpha = 0.15f) else StatusApproved.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isDeviated) "DEVIATED" else "ON ROUTE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDeviated) EmergencyRed else StatusApproved,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Active Safe Journey",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // OpenStreetMap MapView wrapper
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(16.5)
                        currentLocation?.let {
                            controller.setCenter(it)
                        } ?: controller.setCenter(GeoPoint(12.9716, 77.5946))
                        mapViewInstance = this
                    }
                }
            )

            // Stats Overlay (Top Center)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SPEED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${speedKmh.toInt()} km/h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("STOPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$stopCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DEVIATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${deviationDistance.toInt()}m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isDeviated) EmergencyRed else StatusApproved)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DURATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val mins = durationSeconds / 60
                        val secs = durationSeconds % 60
                        Text(String.format("%02d:%02d", mins, secs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Bottom Actions Card
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "JOURNEY CONTROLS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.completeJourney()
                                onNavigateToHome()
                            },
                            modifier = Modifier
                                .weight(1.4f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = OnPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("COMPLETE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OnPrimary)
                        }

                        Button(
                            onClick = {
                                viewModel.cancelJourney()
                                onNavigateToHome()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, tint = OnPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("END", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OnPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulation helper buttons for Travel Route Deviation testing
                    Text(
                        "ROUTE SIMULATION TESTING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val loc = UserLocation(
                                    latitude = 12.9736,
                                    longitude = 77.5966,
                                    speed = 6.0f,
                                    timestamp = System.currentTimeMillis()
                                )
                                TravelModeManager.onLocationUpdated(loc)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Simulate On Path", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        FilledTonalButton(
                            onClick = {
                                val loc = UserLocation(
                                    latitude = 12.9856,
                                    longitude = 77.5800,
                                    speed = 5.0f,
                                    timestamp = System.currentTimeMillis()
                                )
                                TravelModeManager.onLocationUpdated(loc)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Simulate Deviate (>300m)", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Route Deviation Confirmation Dialog
            if (isPromptActive) {
                AlertDialog(
                    onDismissRequest = { /* Force confirmation response */ },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = EmergencyRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Route Deviation Detected!",
                                color = EmergencyRed,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                "Your journey path has deviated significantly from the safe expected route. Are you safe?",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "Auto-reporting emergency in $countdown seconds",
                                color = EmergencyRed,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmSafety() },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("I'M SAFE (RE-ROUTE)", color = OnPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.ignoreDeviationPrompt()
                            }
                        ) {
                            Text("I NEED HELP", color = EmergencyRed, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp)
                )
            }
        }
    }
}
