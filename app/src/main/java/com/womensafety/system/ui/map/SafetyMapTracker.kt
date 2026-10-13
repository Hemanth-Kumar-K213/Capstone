package com.womensafety.system.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

/**
 * Lively radar pulse beacon overlay for OpenStreetMap (OSMDroid).
 * Renders expanding sonar radar waves, vibrant emerald aura, white halo border,
 * specular reflection dot, and directional orientation cone.
 */
class LivelyPulseOverlay(
    var geoPoint: GeoPoint,
    var bearing: Float? = null,
    var isLive: Boolean = true
) : Overlay() {

    private val screenPoint = Point()
    private var animFraction = 0f
    private var lastFrameTime = System.currentTimeMillis()

    private val haloStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#10B981") // Lively Emerald Green
    }

    private val haloFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#10B981")
    }

    private val secondaryWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#34D399")
    }

    private val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }

    private val coreBeaconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#059669")
    }

    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#10B981")
    }

    private val headingBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection ?: return
        projection.toPixels(geoPoint, screenPoint)

        val cx = screenPoint.x.toFloat()
        val cy = screenPoint.y.toFloat()

        val now = System.currentTimeMillis()
        val dt = (now - lastFrameTime).coerceIn(0, 100)
        lastFrameTime = now
        animFraction = (animFraction + dt / 1300f) % 1f

        // 1. Primary Expanding Sonar Radar Wave
        val maxRadius = 65f
        val minRadius = 18f
        val r1 = minRadius + (maxRadius - minRadius) * animFraction
        val alpha1 = ((1f - animFraction) * 220).toInt().coerceIn(0, 255)
        haloStrokePaint.alpha = alpha1
        haloFillPaint.alpha = (alpha1 * 0.28f).toInt().coerceIn(0, 255)

        canvas.drawCircle(cx, cy, r1, haloFillPaint)
        canvas.drawCircle(cx, cy, r1, haloStrokePaint)

        // 2. Secondary Harmonic Wave (offset by half phase)
        val animFraction2 = (animFraction + 0.5f) % 1f
        val r2 = minRadius + (maxRadius - minRadius) * animFraction2
        val alpha2 = ((1f - animFraction2) * 160).toInt().coerceIn(0, 255)
        secondaryWavePaint.alpha = alpha2
        canvas.drawCircle(cx, cy, r2, secondaryWavePaint)

        // 3. Direction Heading Pointer (if bearing is present)
        bearing?.let { b ->
            canvas.save()
            canvas.rotate(b, cx, cy)
            val conePath = Path().apply {
                moveTo(cx, cy - 36f)
                lineTo(cx - 13f, cy - 8f)
                lineTo(cx + 13f, cy - 8f)
                close()
            }
            headingPaint.alpha = 230
            canvas.drawPath(conePath, headingPaint)
            canvas.drawPath(conePath, headingBorderPaint)
            canvas.restore()
        }

        // 4. Central Solid Beacon Core with White Ring
        canvas.drawCircle(cx, cy, 18f, whiteBorderPaint)
        canvas.drawCircle(cx, cy, 16f, coreBeaconPaint)

        // 5. Specular highlight center point
        canvas.drawCircle(cx, cy, 5f, glintPaint)
    }
}

/**
 * Production-ready Composable wrapping OSMDroid MapView for OpenStreetMap rendering.
 * Features:
 * - Lively pulsing radar beacon marker with smooth animation
 * - Guaranteed map initialization and tile streaming with onResume/onPause lifecycle
 * - Safe fallback coordinates (India/Bangalore) if GPS fix is pending
 * - Live 10-second stream status badge with dynamic pulsing dot
 * - Smooth camera navigation, recenter FAB, and quick zoom controls
 * 100% Free & Open-Source (No Google Maps API keys or billing).
 */
@Composable
fun SafetyMapTracker(
    userLatitude: Double,
    userLongitude: Double,
    modifier: Modifier = Modifier.fillMaxSize(),
    zoomLevel: Double = 17.0,
    markerTitle: String = "User Location",
    uid: String? = null,
    bearing: Float? = null,
    accuracy: Float = 0f,
    isLive: Boolean = true,
    enableZoomControls: Boolean = true
) {
    val context = LocalContext.current
    val fallbackCenter = remember { GeoPoint(12.9716, 77.5946) }

    // Initialize OSMDroid configuration with application context and package User-Agent
    DisposableEffect(Unit) {
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = context.packageName
        onDispose { }
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var trackingMarker by remember { mutableStateOf<Marker?>(null) }
    var pulseOverlay by remember { mutableStateOf<LivelyPulseOverlay?>(null) }

    // Ensure OSMDroid tile downloader threads are resumed and paused with Compose lifecycle
    DisposableEffect(mapViewInstance) {
        mapViewInstance?.onResume()
        onDispose {
            mapViewInstance?.onPause()
        }
    }

    // Smooth ~25fps ticker for pulsing radar animation that doesn't starve tile loaders
    LaunchedEffect(isLive, mapViewInstance) {
        if (isLive) {
            while (isActive) {
                delay(40)
                mapViewInstance?.postInvalidate()
            }
        }
    }

    // Pulsing animation for the Compose Live Badge
    val infiniteTransition = rememberInfiniteTransition(label = "live_badge_pulse")
    val badgeDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_alpha"
    )

    // Animate map camera and update lively marker position when coordinates change
    LaunchedEffect(userLatitude, userLongitude, bearing, mapViewInstance) {
        if (userLatitude != 0.0 || userLongitude != 0.0) {
            val nextPosition = GeoPoint(userLatitude, userLongitude)
            mapViewInstance?.let { map ->
                map.controller.animateTo(nextPosition)

                // 1. Lively Radar Pulse Overlay
                if (pulseOverlay == null) {
                    val overlay = LivelyPulseOverlay(
                        geoPoint = nextPosition,
                        bearing = bearing,
                        isLive = isLive
                    )
                    map.overlays.add(overlay)
                    pulseOverlay = overlay
                } else {
                    pulseOverlay?.geoPoint = nextPosition
                    pulseOverlay?.bearing = bearing
                    pulseOverlay?.isLive = isLive
                }

                // 2. Standard Clickable Marker with Info Window
                if (trackingMarker == null) {
                    trackingMarker = Marker(map).apply {
                        position = nextPosition
                        title = markerTitle
                        snippet = if (!uid.isNullOrBlank()) "UID: $uid (10s live GPS)" else "Live Tracking Active"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        map.overlays.add(this)
                    }
                } else {
                    trackingMarker?.position = nextPosition
                    trackingMarker?.title = markerTitle
                    trackingMarker?.snippet = if (!uid.isNullOrBlank()) "UID: $uid (10s live GPS)" else "Live Tracking Active"
                }

                map.invalidate()
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(zoomLevel)

                    val startPoint = if (userLatitude != 0.0 || userLongitude != 0.0) {
                        GeoPoint(userLatitude, userLongitude)
                    } else {
                        fallbackCenter
                    }
                    controller.setCenter(startPoint)

                    if (userLatitude != 0.0 || userLongitude != 0.0) {
                        val overlay = LivelyPulseOverlay(
                            geoPoint = startPoint,
                            bearing = bearing,
                            isLive = isLive
                        )
                        overlays.add(overlay)
                        pulseOverlay = overlay

                        val marker = Marker(this).apply {
                            position = startPoint
                            title = markerTitle
                            snippet = if (!uid.isNullOrBlank()) "UID: $uid (10s live GPS)" else "Live Tracking Active"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        overlays.add(marker)
                        trackingMarker = marker
                    }

                    onResume()
                    mapViewInstance = this
                }
            },
            update = { map ->
                if (userLatitude != 0.0 || userLongitude != 0.0) {
                    val point = GeoPoint(userLatitude, userLongitude)

                    if (pulseOverlay == null) {
                        val overlay = LivelyPulseOverlay(
                            geoPoint = point,
                            bearing = bearing,
                            isLive = isLive
                        )
                        map.overlays.add(overlay)
                        pulseOverlay = overlay
                    } else {
                        pulseOverlay?.geoPoint = point
                        pulseOverlay?.bearing = bearing
                        pulseOverlay?.isLive = isLive
                    }

                    if (trackingMarker == null) {
                        val marker = Marker(map).apply {
                            position = point
                            title = markerTitle
                            snippet = if (!uid.isNullOrBlank()) "UID: $uid (10s live GPS)" else "Live Tracking Active"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(marker)
                        trackingMarker = marker
                    } else {
                        trackingMarker?.position = point
                        trackingMarker?.title = markerTitle
                        trackingMarker?.snippet = if (!uid.isNullOrBlank()) "UID: $uid (10s live GPS)" else "Live Tracking Active"
                    }
                    map.invalidate()
                }
            }
        )

        // Top-Start Lively Status Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    androidx.compose.ui.graphics.Color(0xFF10B981).copy(alpha = 0.4f)
                ),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Color(0xFF10B981).copy(alpha = badgeDotAlpha)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "LIVE RADAR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color(0xFF10B981),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• 10s SYNC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!uid.isNullOrBlank()) {
                            Text(
                                text = "UID: $uid",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Floating Control Buttons (Recenter & Zoom)
        if (enableZoomControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Recenter / Lock on user button
                FloatingActionButton(
                    onClick = {
                        val targetPoint = if (userLatitude != 0.0 || userLongitude != 0.0) {
                            GeoPoint(userLatitude, userLongitude)
                        } else {
                            fallbackCenter
                        }
                        mapViewInstance?.controller?.animateTo(targetPoint)
                    },
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = "Recenter",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Zoom In
                SmallFloatingActionButton(
                    onClick = { mapViewInstance?.controller?.zoomIn() },
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                }

                // Zoom Out
                SmallFloatingActionButton(
                    onClick = { mapViewInstance?.controller?.zoomOut() },
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
