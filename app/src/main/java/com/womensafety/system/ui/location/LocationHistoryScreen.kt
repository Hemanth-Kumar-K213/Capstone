package com.womensafety.system.ui.location

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.ui.components.EmptyStateCard
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.LocationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationHistoryScreen(
    viewModel: LocationViewModel,
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GradientDeepPurple.copy(alpha = 0.5f), BackgroundDark)
                    )
                )
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chip Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${filteredLocations.size} Points Recorded",
                    style = MaterialTheme.typography.labelMedium,
                    color = GradientGold
                )

                FilterChip(
                    selected = sosOnlyFilter,
                    onClick = { sosOnlyFilter = !sosOnlyFilter },
                    label = { Text("SOS Only") },
                    leadingIcon = if (sosOnlyFilter) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusDenied.copy(alpha = 0.2f),
                        selectedLabelColor = StatusDenied
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GradientAmber)
                }
            } else if (filteredLocations.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.LocationOff,
                    title = "No Location History",
                    description = if (sosOnlyFilter) "No SOS-flagged location coordinates found."
                    else "Location history points will appear here once tracking is active."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredLocations, key = { it.id }) { loc ->
                        LocationHistoryItem(
                            location = loc,
                            onOpenMap = {
                                val mapIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(Location)")
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(mapIntent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationHistoryItem(
    location: UserLocation,
    onOpenMap: () -> Unit
) {
    GlassCard(
        accentGradient = if (location.isSOSLocation) {
            Brush.horizontalGradient(
                colors = listOf(StatusDenied.copy(alpha = 0.15f), StatusDenied.copy(alpha = 0.05f))
            )
        } else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (location.isSOSLocation) StatusDenied.copy(alpha = 0.15f)
                            else GradientAmber.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (location.isSOSLocation) Icons.Filled.Warning else Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = if (location.isSOSLocation) StatusDenied else GradientAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        DateUtils.formatDateTime(location.timestamp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )
                    Text(
                        "Lat: ${String.format("%.4f", location.latitude)}, Lng: ${String.format("%.4f", location.longitude)} (±${location.accuracy.toInt()}m)",
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
                            "SOS",
                            color = StatusDenied,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (!location.address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    location.address!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onOpenMap,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp), tint = GradientGold)
                Spacer(modifier = Modifier.width(6.dp))
                Text("View on Google Maps", style = MaterialTheme.typography.labelMedium, color = GradientGold, fontWeight = FontWeight.Bold)
            }
        }
    }
}
