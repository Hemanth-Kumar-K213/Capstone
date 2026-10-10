package com.womensafety.system.ui.map

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Production-ready Composable wrapping OSMDroid MapView for OpenStreetMap rendering.
 * Provides real-time user location marker, smooth animated camera panning, and touch gestures.
 * 100% Free & Open-Source (No API Key or Billing Required).
 */
@Composable
fun SafetyMapTracker(
    userLatitude: Double,
    userLongitude: Double,
    modifier: Modifier = Modifier.fillMaxSize(),
    zoomLevel: Double = 17.5,
    markerTitle: String = "User Location",
    enableZoomControls: Boolean = true
) {
    val context = LocalContext.current

    // Initialize OSMDroid configuration with application context and package User-Agent
    DisposableEffect(Unit) {
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = context.packageName
        onDispose { }
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var trackingMarker by remember { mutableStateOf<Marker?>(null) }

    // Animate map camera and update marker position when coordinates change
    LaunchedEffect(userLatitude, userLongitude, mapViewInstance) {
        if (userLatitude != 0.0 || userLongitude != 0.0) {
            val nextPosition = GeoPoint(userLatitude, userLongitude)
            mapViewInstance?.let { map ->
                map.controller.animateTo(nextPosition)

                if (trackingMarker == null) {
                    trackingMarker = Marker(map).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        map.overlays.add(this)
                    }
                }

                trackingMarker?.let { marker ->
                    marker.position = nextPosition
                    marker.title = markerTitle
                }

                map.invalidate()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                if (enableZoomControls) {
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                } else {
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                }
                controller.setZoom(zoomLevel)
                if (userLatitude != 0.0 || userLongitude != 0.0) {
                    controller.setCenter(GeoPoint(userLatitude, userLongitude))
                }
                mapViewInstance = this
            }
        },
        update = { map ->
            if (userLatitude != 0.0 || userLongitude != 0.0) {
                val point = GeoPoint(userLatitude, userLongitude)
                if (trackingMarker == null) {
                    val marker = Marker(map).apply {
                        position = point
                        title = markerTitle
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(marker)
                    trackingMarker = marker
                } else {
                    trackingMarker?.position = point
                    trackingMarker?.title = markerTitle
                }
                map.invalidate()
            }
        }
    )
}
