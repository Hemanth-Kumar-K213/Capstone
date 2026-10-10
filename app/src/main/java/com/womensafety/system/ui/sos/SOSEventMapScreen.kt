package com.womensafety.system.ui.sos

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.repository.SOSRepository
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.map.SafetyMapTracker
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.utils.MapUtils
import com.womensafety.system.viewmodel.SOSViewModel

/**
 * Visualizes emergency SOS incident locations on OpenStreetMap using OSMDroid.
 * Displays real-time event status, hardware trigger methods, and notification dispatch logs.
 * 100% Free & Open Source (No API Key or billing needed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSEventMapScreen(
    eventId: String,
    viewModel: SOSViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sosRepository = remember { SOSRepository() }
    var sosEvent by remember { mutableStateOf<SOSEvent?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(eventId) {
        val result = sosRepository.getSOSEvent(eventId)
        if (result.isSuccess) {
            sosEvent = result.getOrNull()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SOS Incident Map",
                        color = StatusDenied,
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
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = StatusDenied)
                    }
                }
                sosEvent == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("SOS Event details not found", color = OnSurfaceVariantDark)
                    }
                }
                else -> {
                    val event = sosEvent!!
                    val hasLocation = event.latitude != null && event.longitude != null &&
                            (event.latitude != 0.0 || event.longitude != 0.0)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // OpenStreetMap View
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            ) {
                                if (hasLocation) {
                                    SafetyMapTracker(
                                        userLatitude = event.latitude!!,
                                        userLongitude = event.longitude!!,
                                        zoomLevel = 16.5,
                                        markerTitle = "🚨 SOS Incident (${event.userName.ifBlank { "User" }})",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(SurfaceVariantDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No GPS fix was available at time of alert", color = OnSurfaceVariantDark)
                                    }
                                }
                            }
                        }

                        // Event Summary & Delivery Details Card
                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                GlassCard(
                                    accentGradient = Brush.horizontalGradient(
                                        listOf(StatusDenied.copy(alpha = 0.3f), StatusDenied.copy(alpha = 0.08f))
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(StatusDenied.copy(alpha = 0.25f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusDenied)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Incident #${event.id.take(8).uppercase()}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OnSurfaceDark
                                                )
                                                Text(
                                                    text = "Status: ${event.status.name}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (event.status.name == "ACTIVE") StatusDenied else StatusApproved,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = GlassBorder
                                        )

                                        DetailRow(label = "Trigger Method", value = event.triggerMethod.name, icon = Icons.Filled.TouchApp)
                                        DetailRow(label = "Triggered At", value = DateUtils.formatDateTime(event.triggeredAt), icon = Icons.Filled.Schedule)
                                        if (event.resolvedAt != null) {
                                            DetailRow(label = "Resolved At", value = DateUtils.formatDateTime(event.resolvedAt!!), icon = Icons.Filled.CheckCircle)
                                        }
                                        if (!event.address.isNullOrBlank()) {
                                            DetailRow(label = "Location", value = event.address!!, icon = Icons.Filled.Place)
                                        }
                                        if (hasLocation) {
                                            DetailRow(
                                                label = "GPS Fix",
                                                value = "${String.format("%.5f", event.latitude)}, ${String.format("%.5f", event.longitude)} (±${event.accuracy?.toInt() ?: 0}m)",
                                                icon = Icons.Filled.MyLocation
                                            )
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = GlassBorder
                                        )

                                        Text(
                                            text = "EMERGENCY BROADCAST STATS",
                                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                            color = GradientAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            StatBadge(label = "Twilio SMS", count = event.smsSent)
                                            StatBadge(label = "Auto Call", count = if (event.callMade) 1 else 0)
                                        }

                                        if (hasLocation) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = {
                                                    MapUtils.openExternalMap(
                                                        context = context,
                                                        latitude = event.latitude!!,
                                                        longitude = event.longitude!!,
                                                        label = "SOS Emergency"
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                                                shape = MaterialTheme.shapes.medium
                                            ) {
                                                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("OPEN IN EXTERNAL MAPS", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariantDark, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
    }
}

@Composable
private fun StatBadge(label: String, count: Int) {
    Surface(
        color = SurfaceVariantDark,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$label: ", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariantDark)
            Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GradientGold)
        }
    }
}
