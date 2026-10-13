package com.womensafety.system.ui.travel

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.SecondaryButton
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
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Location Permission Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Travel Mode requires location access to track your journey and monitor deviations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = "Grant Permission",
                onClick = { permissionState.launchMultiplePermissionRequest() }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                text = "Cancel",
                onClick = onBack
            )
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
                    outlinePaint.color = android.graphics.Color.parseColor("#4F46E5")
                    outlinePaint.strokeWidth = 9f
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
                        "Travel Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Travel History",
                            tint = MaterialTheme.colorScheme.primary
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
        ) {
            // Preview Map (Top Half)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f)
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
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .align(Alignment.TopCenter),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = if (isSelectingSourceOnMap) "👉 Tap anywhere on map to set start location" else "👉 Tap anywhere on map to set destination",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Route Settings & Action Card (Bottom Half)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "JOURNEY CONFIGURATION",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isGeocoding) {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (geocodeError != null) {
                        Text(
                            geocodeError!!,
                            color = EmergencyRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // SOURCE INPUT GROUP
                    Text(
                        "1. Starting Location",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = sourceSearchQuery,
                        onValueChange = { sourceSearchQuery = it },
                        label = { Text("Search Start Address") },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.geocodeAddressString(context, sourceSearchQuery, true) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search Source", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        placeholder = { Text(sourceAddressName.ifBlank { "Current GPS location" }) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                curLoc?.let {
                                    viewModel.updateSourceLatLng(GeoPoint(it.latitude, it.longitude), it.address ?: "Current Location")
                                    sourceSearchQuery = it.address ?: ""
                                    Toast.makeText(context, "Using current location!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Current GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = {
                                isSelectingSourceOnMap = true
                                isSelectingDestOnMap = false
                            },
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSelectingSourceOnMap) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("Pick on Map", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // DESTINATION INPUT GROUP
                    Text(
                        "2. Destination",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = destinationSearchQuery,
                        onValueChange = { destinationSearchQuery = it },
                        label = { Text("Search Destination") },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.geocodeAddressString(context, destinationSearchQuery, false) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search Destination", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        placeholder = { Text(destinationAddressName.ifBlank { "Enter place name or landmark" }) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            isSelectingDestOnMap = true
                            isSelectingSourceOnMap = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSelectingDestOnMap) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Pick Destination on Map", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val canStart = destinationLatLng != null
                    PrimaryButton(
                        text = "Start Safe Journey",
                        onClick = {
                            viewModel.startJourney(activeSourceLatLng, activeDestinationLatLng)
                            onNavigateToActiveJourney()
                        },
                        enabled = canStart,
                        icon = Icons.Filled.Navigation
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
