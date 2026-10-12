package com.womensafety.system.ui.travel

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.womensafety.system.ui.home.GlassCard
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
                    outlinePaint.color = if (isDeviated) android.graphics.Color.RED else android.graphics.Color.parseColor("#FFA726")
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
                    Text(
                        "Active Safe Journey",
                        color = OnSurfaceDark,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark.copy(alpha = 0.9f))
            )
        },
        containerColor = BackgroundDark
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
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BackgroundDark.copy(alpha = 0.9f)),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SPEED", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
                        Text("${speedKmh.toInt()} km/h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GradientAmber)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("STOPS", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
                        Text("$stopCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GradientGold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DEVIATION", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
                        Text("${deviationDistance.toInt()}m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isDeviated) StatusDenied else StatusApproved)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DURATION", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
                        val mins = durationSeconds / 60
                        val secs = durationSeconds % 60
                        Text(String.format("%02d:%02d", mins, secs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnSurfaceDark)
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
                GlassCard(
                    accentGradient = Brush.horizontalGradient(
                        colors = listOf(GradientAmber.copy(alpha = 0.1f), GradientRose.copy(alpha = 0.05f))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "JOURNEY ACTIONS",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = GradientAmber
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
                                    .weight(1.5f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("COMPLETE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.cancelJourney()
                                    onNavigateToHome()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDenied),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("END", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Simulation helper buttons for Travel Route Deviation testing
                        Text(
                            "SIMULATION PRESETS",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
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
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                            ) {
                                Text("On Path", fontSize = 10.sp)
                            }
                            Button(
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
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                            ) {
                                Text("Deviate (>300m)", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Route Deviation Confirmation Dialog
            if (isPromptActive) {
                AlertDialog(
                    onDismissRequest = { /* Force confirmation response */ },
                    title = {
                        Text(
                            "Route Changed!",
                            color = StatusDenied,
                            fontWeight = FontWeight.Black
                        )
                    },
                    text = {
                        Column {
                            Text(
                                "Your route has changed from the expected polyline. Are you safe?",
                                color = OnSurfaceDark
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Auto-reporting alert in $countdown seconds",
                                color = StatusDenied,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmSafety() },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusApproved)
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
                            Text("I NEED HELP", color = StatusDenied, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = Color(0xFF291414),
                    textContentColor = OnSurfaceDark
                )
            }
        }
    }
}
