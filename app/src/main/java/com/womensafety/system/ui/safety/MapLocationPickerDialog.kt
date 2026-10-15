package com.womensafety.system.ui.safety

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.womensafety.system.manager.TrinetraLocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/**
 * Requirement 3A: Interactive Map Location Picker Dialog using OpenStreetMap (OSMDroid).
 *
 * Allows visual location selection via:
 * - Address Search Geocoding
 * - Tap / Long-press on map
 * - "Select Map Center" crosshair button
 * - Real-time Reverse Geocoding
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLocationPickerDialog(
    isOpen: Boolean,
    initialLat: Double? = null,
    initialLon: Double? = null,
    onLocationSelected: (latitude: Double, longitude: Double, address: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val defaultPoint = remember {
        if (initialLat != null && initialLon != null && initialLat != 0.0 && initialLon != 0.0) {
            GeoPoint(initialLat, initialLon)
        } else {
            GeoPoint(12.9716, 77.5946) // Default fallback: Bangalore Central
        }
    }

    var selectedLocation by remember { mutableStateOf<GeoPoint?>(defaultPoint) }
    var addressText by remember { mutableStateOf("Fetching address...") }
    var isReverseGeocoding by remember { mutableStateOf(false) }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var markerInstance by remember { mutableStateOf<Marker?>(null) }

    // Reverse geocode helper
    fun reverseGeocodePoint(geoPoint: GeoPoint) {
        selectedLocation = geoPoint
        isReverseGeocoding = true
        coroutineScope.launch {
            val addr = withContext(Dispatchers.IO) {
                TrinetraLocationManager.reverseGeocode(context, geoPoint.latitude, geoPoint.longitude)
            }
            addressText = addr ?: "Lat: ${String.format(Locale.US, "%.6f", geoPoint.latitude)}, Lon: ${String.format(Locale.US, "%.6f", geoPoint.longitude)}"
            isReverseGeocoding = false
            Log.d("MapPicker", "Selected location: ${geoPoint.latitude}, ${geoPoint.longitude} -> $addressText")
        }
    }

    // Update marker on map
    fun updateMapMarker(map: MapView, geoPoint: GeoPoint) {
        if (markerInstance == null) {
            val marker = Marker(map).apply {
                position = geoPoint
                title = "Selected Location"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
            markerInstance = marker
        } else {
            markerInstance?.position = geoPoint
        }
        map.invalidate()
    }

    // Initialize address on open
    LaunchedEffect(defaultPoint) {
        reverseGeocodePoint(defaultPoint)
    }

    // Configure OSMDroid
    DisposableEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid_picker", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose {
            mapViewInstance?.onDetach()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddLocationAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Pick Location on Map",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search address or landmark...") },
                    placeholder = { Text("e.g. MG Road, Tech Park, City Center") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        if (searchQuery.isNotBlank()) {
                            isSearching = true
                            coroutineScope.launch {
                                val found = TrinetraLocationManager.getLocationFromAddress(context, searchQuery.trim())
                                isSearching = false
                                if (found != null) {
                                    mapViewInstance?.controller?.animateTo(found)
                                    mapViewInstance?.controller?.setZoom(16.0)
                                    mapViewInstance?.let { updateMapMarker(it, found) }
                                    reverseGeocodePoint(found)
                                    Toast.makeText(context, "Location found", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Address not found. Tap directly on map.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }),
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = {
                                keyboardController?.hide()
                                if (searchQuery.isNotBlank()) {
                                    isSearching = true
                                    coroutineScope.launch {
                                        val found = TrinetraLocationManager.getLocationFromAddress(context, searchQuery.trim())
                                        isSearching = false
                                        if (found != null) {
                                            mapViewInstance?.controller?.animateTo(found)
                                            mapViewInstance?.controller?.setZoom(16.0)
                                            mapViewInstance?.let { updateMapMarker(it, found) }
                                            reverseGeocodePoint(found)
                                        } else {
                                            Toast.makeText(context, "Address not found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive OSMDroid Map Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)
                                controller.setCenter(defaultPoint)

                                // Event receiver for single tap / long press
                                val eventsReceiver = object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                        reverseGeocodePoint(p)
                                        updateMapMarker(this@apply, p)
                                        return true
                                    }

                                    override fun longPressHelper(p: GeoPoint): Boolean {
                                        reverseGeocodePoint(p)
                                        updateMapMarker(this@apply, p)
                                        return true
                                    }
                                }
                                overlays.add(MapEventsOverlay(eventsReceiver))

                                updateMapMarker(this, defaultPoint)
                                mapViewInstance = this
                            }
                        }
                    )

                    // Helper pill on top of map
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 4.dp
                    ) {
                        Text(
                            "👆 Tap or Long-press anywhere to drop pin",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Floating Button: Pin Map Center
                    SmallFloatingActionButton(
                        onClick = {
                            mapViewInstance?.let { map ->
                                val center = map.mapCenter as? GeoPoint ?: GeoPoint(map.mapCenter.latitude, map.mapCenter.longitude)
                                reverseGeocodePoint(center)
                                updateMapMarker(map, center)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Center Pin", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selected Location Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Selected Coordinates",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        selectedLocation?.let { pt ->
                            Text(
                                "Lat: ${String.format(Locale.US, "%.6f", pt.latitude)}, Lon: ${String.format(Locale.US, "%.6f", pt.longitude)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (isReverseGeocoding) "Resolving address..." else addressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        } ?: run {
                            Text("No location selected. Tap map to select.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions Footer: Cancel | Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            selectedLocation?.let { loc ->
                                onLocationSelected(loc.latitude, loc.longitude, addressText)
                                onDismiss()
                            }
                        },
                        enabled = selectedLocation != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm")
                    }
                }
            }
        }
    }
}
