package com.womensafety.system.ui.travel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import androidx.compose.foundation.BorderStroke
import com.womensafety.system.manager.TravelModeManager
import com.womensafety.system.viewmodel.TravelViewModel
import com.womensafety.system.manager.JourneyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveJourneyScreen(
    viewModel: TravelViewModel,
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

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation ?: LatLng(12.9716, 77.5946), 16f)
    }

    // Auto-pan camera to current location
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 16f)
        }
    }

    // Perpendicular deviation status text
    val isDeviated = deviationDistance > 300.0 // 300 meters

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
            val isMapAvailable = remember {
                com.womensafety.system.manager.TrinetraLocationManager.isPlayServicesAvailable(context) &&
                        com.womensafety.system.manager.TrinetraLocationManager.isMapsApiKeyConfigured(context)
            }

            if (isMapAvailable) {
                // Google Map
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
                ) {
                    currentLocation?.let {
                        Marker(
                            state = MarkerState(position = it),
                            title = "Your Location",
                            snippet = "Speed: ${speedKmh.toInt()} km/h"
                        )
                    }

                    destinationLocation?.let {
                        Marker(
                            state = MarkerState(position = it),
                            title = "Destination"
                        )
                    }

                    if (routePoints.isNotEmpty()) {
                        Polyline(
                            points = routePoints,
                            color = if (isDeviated) StatusDenied else GradientAmber,
                            width = 8f
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = null,
                            tint = StatusDenied,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Map service is unavailable. Please check Google Maps configuration.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceDark,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Stats Overlay (Top Center)
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BackgroundDark.copy(alpha = 0.85f)),
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

                        // Simulation helper buttons for Travel Route Deviation
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
                                    // Trigger normal location update on path
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
                                    // Trigger deviated coordinate
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
                    containerColor = Color(0xFF291414), // Warn layout color
                    textContentColor = OnSurfaceDark
                )
            }
        }
    }
}
