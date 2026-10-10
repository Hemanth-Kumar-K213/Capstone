package com.womensafety.system.ui.travel

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.TravelViewModel
import java.util.Locale

/**
 * Trip planning screen for Proactive Safe Travel Mode powered by OpenStreetMap (OSMDroid).
 * Provides interactive map pin selection, address geocoding search, and preview route generation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TravelModeScreen(
    viewModel: TravelViewModel = viewModel(),
    onNavigateToActiveJourney: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit
) {
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    if (!permissionState.allPermissionsGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = StatusDenied,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Location Permission Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Travel Mode requires location access to track your journey and monitor deviations.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantDark,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { permissionState.launchMultiplePermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("GRANT PERMISSION", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onBack) {
                Text("CANCEL", color = OnSurfaceVariantDark)
            }
        }
        return
    }

    val context = LocalContext.current
    val curLoc by LocationTrackingService.currentLocation.collectAsState()

    DisposableEffect(Unit) {
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = context.packageName
        onDispose { }
    }

    val sourceLatLng by viewModel.sourceLatLng.collectAsState()
    val sourceAddressName by viewModel.sourceAddressName.collectAsState()

    val destinationLatLng by viewModel.destinationLatLng.collectAsState()
    val destinationAddressName by viewModel.destinationAddressName.collectAsState()

    val isGeocoding by viewModel.isGeocoding.collectAsState()
    val geocodeError by viewModel.geocodeError.collectAsState()

    var sourceSearchQuery by remember { mutableStateOf("") }
    var destinationSearchQuery by remember { mutableStateOf("") }

    val activeSourceLatLng = remember(sourceLatLng, curLoc) {
        sourceLatLng ?: curLoc?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(12.9716, 77.5946)
    }

    val activeDestinationLatLng = remember(destinationLatLng) {
        destinationLatLng ?: GeoPoint(12.9816, 77.6046)
    }

    var isSelectingSourceOnMap by remember { mutableStateOf(false) }
    var isSelectingDestOnMap by remember { mutableStateOf(false) }

    // Preview expected route polyline
    val routePoints = remember(activeSourceLatLng, destinationLatLng) {
        val points = mutableListOf<GeoPoint>()
        points.add(activeSourceLatLng)
        if (destinationLatLng != null) {
            val dest = destinationLatLng!!
            val latDiff = dest.latitude - activeSourceLatLng.latitude
            val lngDiff = dest.longitude - activeSourceLatLng.longitude
            points.add(GeoPoint(activeSourceLatLng.latitude + latDiff * 0.4, activeSourceLatLng.longitude + lngDiff * 0.1))
            points.add(GeoPoint(activeSourceLatLng.latitude + latDiff * 0.7, activeSourceLatLng.longitude + lngDiff * 0.8))
            points.add(dest)
        } else {
            points.add(activeDestinationLatLng)
        }
        points
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Update overlays when coordinates change
    LaunchedEffect(activeSourceLatLng, destinationLatLng, routePoints, isSelectingSourceOnMap, isSelectingDestOnMap, mapViewInstance) {
        mapViewInstance?.let { map ->
            map.overlays.clear()

            // Map click listener overlay
            val clickReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    p?.let { tapPoint ->
                        if (isSelectingSourceOnMap) {
                            viewModel.updateSourceLatLng(tapPoint, "Map Point (${String.format(Locale.US, "%.4f", tapPoint.latitude)}, ${String.format(Locale.US, "%.4f", tapPoint.longitude)})")
                            isSelectingSourceOnMap = false
                            Toast.makeText(context, "Start location set!", Toast.LENGTH_SHORT).show()
                        } else if (isSelectingDestOnMap) {
                            viewModel.updateDestinationLatLng(tapPoint, "Map Point (${String.format(Locale.US, "%.4f", tapPoint.latitude)}, ${String.format(Locale.US, "%.4f", tapPoint.longitude)})")
                            isSelectingDestOnMap = false
                            Toast.makeText(context, "Destination set!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return true
                }
                override fun longPressHelper(p: GeoPoint?): Boolean = false
            }
            map.overlays.add(MapEventsOverlay(clickReceiver))

            // Start marker
            val startMarker = Marker(map).apply {
                position = activeSourceLatLng
                title = "Start: ${sourceAddressName.ifBlank { "Current Location" }}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(startMarker)

            // Destination marker
            destinationLatLng?.let { dest ->
                val destMarker = Marker(map).apply {
                    position = dest
                    title = "Destination: ${destinationAddressName.ifBlank { "Selected Place" }}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(destMarker)
            }

            // Route Polyline
            if (destinationLatLng != null && routePoints.isNotEmpty()) {
                val polyline = Polyline(map).apply {
                    setPoints(routePoints)
                    outlinePaint.color = android.graphics.Color.parseColor("#FFA726")
                    outlinePaint.strokeWidth = 8f
                }
                map.overlays.add(polyline)
            }

            map.invalidate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Travel Mode Navigation",
                        color = OnSurfaceDark,
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
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Travel History",
                            tint = OnSurfaceDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark.copy(alpha = 0.9f))
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
                        colors = listOf(BackgroundDark, Color(0xFF130F00))
                    )
                )
        ) {
            // Preview Map (Half Screen)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                            controller.setZoom(14.0)
                            controller.setCenter(activeSourceLatLng)
                            mapViewInstance = this
                        }
                    }
                )

                // Map Selection Guide Banner
                if (isSelectingSourceOnMap || isSelectingDestOnMap) {
                    Surface(
                        color = GradientAmber.copy(alpha = 0.9f),
                        contentColor = BackgroundDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Text(
                            text = if (isSelectingSourceOnMap) "👉 TAP ON MAP TO SET START LOCATION" else "👉 TAP ON MAP TO SET DESTINATION",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Route Settings & Action Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                GlassCard(
                    accentGradient = Brush.horizontalGradient(
                        colors = listOf(GradientAmber.copy(alpha = 0.1f), GradientGold.copy(alpha = 0.05f))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "JOURNEY CONFIGURATION",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = GradientAmber,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isGeocoding) {
                            LinearProgressIndicator(color = GradientAmber, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (geocodeError != null) {
                            Text(geocodeError!!, color = StatusDenied, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // SOURCE INPUT GROUP
                        Text(
                            "1. STARTING LOCATION",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = sourceSearchQuery,
                                onValueChange = { sourceSearchQuery = it },
                                label = { Text("Search Start Address") },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.geocodeAddressString(context, sourceSearchQuery, true) }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search Source", tint = GradientAmber)
                                    }
                                },
                                placeholder = { Text(sourceAddressName.ifBlank { "Current GPS location" }) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GradientAmber,
                                    unfocusedBorderColor = OutlineDark
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    curLoc?.let {
                                        viewModel.updateSourceLatLng(GeoPoint(it.latitude, it.longitude), it.address ?: "Current Location")
                                        sourceSearchQuery = it.address ?: ""
                                        Toast.makeText(context, "Using current location!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1.1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                            ) {
                                Text("USE CURRENT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    isSelectingSourceOnMap = true
                                    isSelectingDestOnMap = false
                                },
                                modifier = Modifier.weight(0.9f),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSelectingSourceOnMap) GradientAmber else SurfaceVariantDark)
                            ) {
                                Text("SELECT ON MAP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // DESTINATION INPUT GROUP
                        Text(
                            "2. DESTINATION",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = destinationSearchQuery,
                                onValueChange = { destinationSearchQuery = it },
                                label = { Text("Search Destination") },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.geocodeAddressString(context, destinationSearchQuery, false) }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search Destination", tint = GradientAmber)
                                    }
                                },
                                placeholder = { Text(destinationAddressName.ifBlank { "Enter place name" }) },
                                modifier = Modifier.weight(1.1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GradientAmber,
                                    unfocusedBorderColor = OutlineDark
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                isSelectingDestOnMap = true
                                isSelectingSourceOnMap = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSelectingDestOnMap) GradientAmber else SurfaceVariantDark)
                        ) {
                            Text("SELECT DESTINATION ON MAP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        val canStart = destinationLatLng != null
                        Button(
                            onClick = {
                                viewModel.startJourney(activeSourceLatLng, activeDestinationLatLng)
                                onNavigateToActiveJourney()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = canStart,
                            colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, tint = OnPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "START JOURNEY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = OnPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
