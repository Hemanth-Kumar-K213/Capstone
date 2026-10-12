package com.womensafety.system.ui.location

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.womensafety.system.ui.theme.BackgroundDark
import com.womensafety.system.ui.theme.OnSurfaceDark

/**
 * Diagnostic testing screen for OpenStreetMap (OSMDroid) tile rendering and camera controls.
 * Replaces the old Google Maps test screen with 100% free, open-source verification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTestScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bangalore = remember { GeoPoint(12.9716, 77.5946) }

    var centerLat by remember { mutableStateOf(bangalore.latitude) }
    var centerLon by remember { mutableStateOf(bangalore.longitude) }
    var zoomLevel by remember { mutableStateOf(13.0) }

    DisposableEffect(Unit) {
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = context.packageName
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenStreetMap Diagnostics", color = OnSurfaceDark, fontWeight = FontWeight.Bold) },
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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.ALWAYS)
                        controller.setZoom(13.0)
                        controller.setCenter(bangalore)

                        val marker = Marker(this).apply {
                            position = bangalore
                            title = "OSMDroid Test Marker (Bangalore)"
                            snippet = "Coordinates: 12.9716, 77.5946"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        overlays.add(marker)

                        addMapListener(object : org.osmdroid.events.MapListener {
                            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                mapCenter?.let {
                                    centerLat = it.latitude
                                    centerLon = it.longitude
                                }
                                return true
                            }

                            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                zoomLevel = zoomLevelDouble
                                return true
                            }
                        })
                    }
                }
            )

            // Camera Position Info Card
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BackgroundDark.copy(alpha = 0.9f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "OSMDROID CAMERA POSITION",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceDark.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lat: ${String.format("%.5f", centerLat)} • Lng: ${String.format("%.5f", centerLon)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                    Text(
                        text = "Zoom: ${String.format("%.1f", zoomLevel)} • FOSS OpenStreetMap (No API Key)",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDark.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
