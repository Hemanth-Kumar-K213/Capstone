package com.womensafety.system.ui.sos

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.repository.SOSRepository
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.PrimaryButton
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmergencyRed
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EmergencyRed, strokeWidth = 3.dp)
                    }
                }
                sosEvent == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "SOS Event details not found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
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
                                        uid = event.userId,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No GPS fix was available at time of alert",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        // Event Summary & Delivery Details Card
                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                AppCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(EmergencyRed.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.Warning,
                                                contentDescription = null,
                                                tint = EmergencyRed,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                text = "Incident #${event.id.take(8).uppercase()}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Surface(
                                                color = if (event.status.name == "ACTIVE") EmergencyRed.copy(alpha = 0.15f) else StatusApproved.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = event.status.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (event.status.name == "ACTIVE") EmergencyRed else StatusApproved,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 14.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
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
                                        modifier = Modifier.padding(vertical = 14.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    )

                                    Text(
                                        text = "EMERGENCY BROADCAST STATS",
                                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        StatBadge(label = "Twilio SMS", count = event.smsSent, modifier = Modifier.weight(1f))
                                        StatBadge(label = "Auto Call", count = if (event.callMade) 1 else 0, modifier = Modifier.weight(1f))
                                    }

                                    if (hasLocation) {
                                        Spacer(modifier = Modifier.height(18.dp))
                                        PrimaryButton(
                                            text = "Open in External Maps",
                                            onClick = {
                                                MapUtils.openExternalMap(
                                                    context = context,
                                                    latitude = event.latitude!!,
                                                    longitude = event.longitude!!,
                                                    label = "SOS Emergency"
                                                )
                                            },
                                            icon = Icons.Filled.Navigation
                                        )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatBadge(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$count",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
