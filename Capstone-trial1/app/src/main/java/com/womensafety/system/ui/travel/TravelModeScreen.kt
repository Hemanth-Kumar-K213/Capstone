package com.womensafety.system.ui.travel

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.ui.text.style.TextAlign
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.viewmodel.TravelViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TravelModeScreen(
    viewModel: TravelViewModel,
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

    val sourceLatLng by viewModel.sourceLatLng.collectAsState()
    val sourceAddressName by viewModel.sourceAddressName.collectAsState()

    val destinationLatLng by viewModel.destinationLatLng.collectAsState()
    val destinationAddressName by viewModel.destinationAddressName.collectAsState()

    val isGeocoding by viewModel.isGeocoding.collectAsState()
    val geocodeError by viewModel.geocodeError.collectAsState()

    var sourceSearchQuery by remember { mutableStateOf("") }
    var destinationSearchQuery by remember { mutableStateOf("") }

    val activeSourceLatLng = remember(sourceLatLng, curLoc) {
        sourceLatLng ?: curLoc?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(12.9716, 77.5946)
    }

    val activeDestinationLatLng = remember(destinationLatLng) {
        destinationLatLng ?: LatLng(12.9816, 77.6046)
    }

    // Determine map tap modes
    var isSelectingSourceOnMap by remember { mutableStateOf(false) }
    var isSelectingDestOnMap by remember { mutableStateOf(false) }

    // Preview expected route polyline
    val routePoints = remember(activeSourceLatLng, destinationLatLng) {
        val points = mutableListOf<LatLng>()
        points.add(activeSourceLatLng)
        if (destinationLatLng != null) {
            val dest = destinationLatLng!!
            val latDiff = dest.latitude - activeSourceLatLng.latitude
            val lngDiff = dest.longitude - activeSourceLatLng.longitude
            // Generate grid path for mock preview before API loads
            points.add(LatLng(activeSourceLatLng.latitude + latDiff * 0.4, activeSourceLatLng.longitude + lngDiff * 0.1))
            points.add(LatLng(activeSourceLatLng.latitude + latDiff * 0.7, activeSourceLatLng.longitude + lngDiff * 0.8))
            points.add(dest)
        } else {
            points.add(activeDestinationLatLng)
        }
        points
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(activeSourceLatLng, 13f)
    }

    // Auto-center camera on current location initially
    LaunchedEffect(curLoc) {
        if (curLoc != null && sourceLatLng == null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(curLoc!!.latitude, curLoc!!.longitude), 13f)
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
                val isMapAvailable = remember {
                    com.womensafety.system.manager.TrinetraLocationManager.isPlayServicesAvailable(context) &&
                            com.womensafety.system.manager.TrinetraLocationManager.isMapsApiKeyConfigured(context)
                }

                if (isMapAvailable) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false),
                        onMapClick = { tapLatLng ->
                            if (isSelectingSourceOnMap) {
                                viewModel.updateSourceLatLng(tapLatLng, "Map Tap Point (${String.format(Locale.US, "%.4f", tapLatLng.latitude)}, ${String.format(Locale.US, "%.4f", tapLatLng.longitude)})")
                                isSelectingSourceOnMap = false
                                Toast.makeText(context, "Source set!", Toast.LENGTH_SHORT).show()
                            } else if (isSelectingDestOnMap) {
                                viewModel.updateDestinationLatLng(tapLatLng, "Map Tap Point (${String.format(Locale.US, "%.4f", tapLatLng.latitude)}, ${String.format(Locale.US, "%.4f", tapLatLng.longitude)})")
                                isSelectingDestOnMap = false
                                Toast.makeText(context, "Destination set!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Marker(
                            state = MarkerState(position = activeSourceLatLng),
                            title = "Source: ${sourceAddressName.ifBlank { "Your Location" }}",
                            snippet = "Trip Start Coordinate"
                        )
                        destinationLatLng?.let { dest ->
                            Marker(
                                state = MarkerState(position = dest),
                                title = "Destination: ${destinationAddressName.ifBlank { "Selected Place" }}",
                                snippet = "Trip End Coordinate"
                            )
                        }
                        if (destinationLatLng != null && routePoints.isNotEmpty()) {
                            Polyline(
                                points = routePoints,
                                color = GradientAmber,
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
                            text = if (isSelectingSourceOnMap) "👉 TAP ON THE MAP TO SET SOURCE LOCATION" else "👉 TAP ON THE MAP TO SET DESTINATION LOCATION",
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

                        // Loader Indicator
                        if (isGeocoding) {
                            LinearProgressIndicator(color = GradientAmber, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Geocoding error
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
                                        viewModel.updateSourceLatLng(LatLng(it.latitude, it.longitude), it.address ?: "Current Location")
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
