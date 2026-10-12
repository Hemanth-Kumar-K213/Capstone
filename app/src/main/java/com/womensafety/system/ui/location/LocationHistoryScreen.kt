package com.womensafety.system.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.ui.components.EmptyStateCard
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.map.SafetyMapTracker
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.utils.MapUtils
import com.womensafety.system.viewmodel.LocationViewModel

/**
 * Displays recorded GPS breadcrumbs with compact OpenStreetMap previews.
 * Uses OSMDroid (100% Free & Open-Source, no Google Maps API keys).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationHistoryScreen(
    viewModel: LocationViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val history by viewModel.locationHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userId = viewModel.currentUserId

    var sosOnlyFilter by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        if (userId != null) {
            viewModel.loadLocationHistory(userId)
        }
    }

    val filteredLocations = remember(history, sosOnlyFilter) {
        if (sosOnlyFilter) history.filter { it.isSOSLocation } else history
    }

    if (showClearDialog && userId != null) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = SurfaceVariantDark,
            title = { Text("Clear Location History", color = OnSurfaceDark, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete all stored location coordinates older than 7 days?",
                    color = OnSurfaceVariantDark
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                    viewModel.clearHistory(userId, sevenDaysAgo)
                }) {
                    Text("Clear", color = StatusDenied, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Location History",
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
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear History", tint = StatusDenied)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !sosOnlyFilter,
                    onClick = { sosOnlyFilter = false },
                    label = { Text("All Records (${history.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GradientAmber,
                        selectedLabelColor = BackgroundDark
                    )
                )

                val sosCount = history.count { it.isSOSLocation }
                FilterChip(
                    selected = sosOnlyFilter,
                    onClick = { sosOnlyFilter = true },
                    label = { Text("SOS Incidents ($sosCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusDenied,
                        selectedLabelColor = OnSurfaceDark
                    )
                )
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GradientAmber)
                    }
                }
                filteredLocations.isEmpty() -> {
                        EmptyStateCard(
                            icon = Icons.Filled.LocationOff,
                            title = if (sosOnlyFilter) "No SOS Locations" else "No Location History",
                            description = if (sosOnlyFilter) "No emergency incidents have been recorded." else "Background location logs will appear here as you move."
                        )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredLocations, key = { it.id }) { loc ->
                            LocationHistoryItemCard(
                                location = loc,
                                onOpenMap = {
                                    MapUtils.openExternalMap(
                                        context = context,
                                        latitude = loc.latitude,
                                        longitude = loc.longitude,
                                        label = if (loc.isSOSLocation) "SOS Incident" else "Saved Location"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationHistoryItemCard(
    location: UserLocation,
    onOpenMap: () -> Unit
) {
    var isMapExpanded by remember { mutableStateOf(false) }

    GlassCard(
        accentGradient = if (location.isSOSLocation) {
            Brush.horizontalGradient(listOf(StatusDenied.copy(alpha = 0.35f), StatusDenied.copy(alpha = 0.1f)))
        } else {
            Brush.horizontalGradient(listOf(GradientAmber.copy(alpha = 0.15f), GradientGold.copy(alpha = 0.05f)))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (location.isSOSLocation) StatusDenied.copy(alpha = 0.25f) else GradientAmber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (location.isSOSLocation) Icons.Filled.Warning else Icons.Filled.Place,
                        contentDescription = null,
                        tint = if (location.isSOSLocation) StatusDenied else GradientAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DateUtils.formatDateTime(location.timestamp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )
                    Text(
                        text = "Lat: ${String.format("%.4f", location.latitude)}, Lng: ${String.format("%.4f", location.longitude)} (±${location.accuracy.toInt()}m)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = OnSurfaceVariantDark
                    )
                }

                if (location.isSOSLocation) {
                    Surface(
                        color = StatusDenied.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "SOS",
                            color = StatusDenied,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (!location.address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = location.address ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark.copy(alpha = 0.9f)
                )
            }

            // Compact OpenStreetMap preview when toggled or SOS
            if (isMapExpanded || location.isSOSLocation) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    SafetyMapTracker(
                        userLatitude = location.latitude,
                        userLongitude = location.longitude,
                        zoomLevel = 16.0,
                        enableZoomControls = false,
                        markerTitle = if (location.isSOSLocation) "SOS Incident" else "Recorded Location"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { isMapExpanded = !isMapExpanded },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isMapExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = GradientGold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMapExpanded) "Hide Map" else "Preview Map",
                        style = MaterialTheme.typography.labelMedium,
                        color = GradientGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onOpenMap,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(14.dp), tint = OnSurfaceDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("External Nav", style = MaterialTheme.typography.labelMedium, color = OnSurfaceDark)
                }
            }
        }
    }
}
