package com.womensafety.system.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.womensafety.system.manager.TrinetraLocationManager
import com.womensafety.system.ui.theme.BackgroundDark
import com.womensafety.system.ui.theme.OnSurfaceDark
import com.womensafety.system.ui.theme.StatusDenied

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTestScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bangalore = remember { LatLng(12.9716, 77.5946) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bangalore, 12f)
    }

    val isMapAvailable = remember {
        TrinetraLocationManager.isPlayServicesAvailable(context) &&
                TrinetraLocationManager.isMapsApiKeyConfigured(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Map Diagnostics", color = OnSurfaceDark, fontWeight = FontWeight.Bold) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isMapAvailable) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = true)
                ) {
                    Marker(
                        state = MarkerState(position = bangalore),
                        title = "Test Marker (Bangalore)",
                        snippet = "Coordinates: 12.9716, 77.5946"
                    )
                }

                // Camera Position Overlay
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BackgroundDark.copy(alpha = 0.85f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CAMERA POSITION",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceDark.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val target = cameraPositionState.position.target
                        Text(
                            text = "Lat: ${String.format("%.5f", target.latitude)} • Lng: ${String.format("%.5f", target.longitude)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceDark
                        )
                        Text(
                            text = "Zoom: ${String.format("%.1f", cameraPositionState.position.zoom)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDark.copy(alpha = 0.8f)
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
        }
    }
}
