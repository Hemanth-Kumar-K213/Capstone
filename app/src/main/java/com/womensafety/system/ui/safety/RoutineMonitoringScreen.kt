package com.womensafety.system.ui.safety

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.*
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.RoutineMonitoringViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Requirement 1C: Routine Monitoring Screen.
 * Three tabs:
 * TAB 1: Your Routine (Today) from Firestore routines/{userId}/{today-dayOfWeek}
 * TAB 2: Anomaly History (real deviations)
 * TAB 3: Auto-Learn Status (frequency, discovery count, manual refresh)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineMonitoringScreen(
    viewModel: RoutineMonitoringViewModel,
    onBack: () -> Unit,
    onTriggerSOS: () -> Unit = {}
) {
    val context = LocalContext.current
    val locations by viewModel.todayLocations.collectAsState()
    val routine by viewModel.todayRoutine.collectAsState()
    val anomalies by viewModel.detectedAnomalies.collectAsState()
    val nextExpected by viewModel.nextExpectedLocation.collectAsState()
    val activeAlert by viewModel.activeAlert.collectAsState()
    val autoLearnStatus by viewModel.autoLearnStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Your Routine, 1: Anomaly History, 2: Auto-Learn Status
    var showManualEntryDialog by remember { mutableStateOf(false) }
    var locationToEdit by remember { mutableStateOf<RoutineLocation?>(null) }

    // Manual Entry / Edit Dialog
    if (showManualEntryDialog) {
        RoutineManualEntryDialog(
            initialLocation = locationToEdit,
            initialDayOfWeek = LocalDate.now().dayOfWeek,
            onDismiss = {
                showManualEntryDialog = false
                locationToEdit = null
            },
            onSaveLocation = { days, newLoc ->
                viewModel.saveLocation(days, newLoc)
                showManualEntryDialog = false
                locationToEdit = null
            },
            onDeleteLocation = { day, locId ->
                viewModel.deleteLocation(day, locId)
                showManualEntryDialog = false
                locationToEdit = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Routine-Based Safety",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Three Tabs: Your Routine | Anomaly History | Auto-Learn Status
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Your Routine (${locations.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Anomalies (${anomalies.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Auto-Learn", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Active Alert Banner if exists
                if (activeAlert != null) {
                    item {
                        ActiveAnomalyBanner(
                            alert = activeAlert!!,
                            onRespond = { resp -> viewModel.respondToAnomaly(activeAlert!!.id, resp) },
                            onSOS = onTriggerSOS
                        )
                    }
                }

                when (activeTab) {
                    0 -> {
                        // ─── TAB 1: YOUR ROUTINE (Today) ─────────────────────
                        item {
                            RoutineTodayHeader(
                                routine = routine,
                                nextExpected = nextExpected,
                                locationsCount = locations.size
                            )
                        }

                        // Add Custom Location & Refresh Buttons
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        locationToEdit = null
                                        showManualEntryDialog = true
                                    },
                                    modifier = Modifier.weight(1.7f).height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+ ADD LOCATION", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.refreshRoutine(LocalDate.now().dayOfWeek)
                                        Toast.makeText(context, "Routine list refreshed", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1.3f).height(46.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }

                        if (locations.isNotEmpty()) {
                            item {
                                Text(
                                    "TODAY'S LEARNED LOCATIONS",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            items(locations) { loc ->
                                RoutineLocationCard(
                                    location = loc,
                                    onEdit = {
                                        locationToEdit = loc
                                        showManualEntryDialog = true
                                    },
                                    onDelete = {
                                        viewModel.deleteLocation(LocalDate.now().dayOfWeek, loc.locationId)
                                        Toast.makeText(context, "Location deleted", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            item {
                                RoutineTabFooter(autoLearnStatus = autoLearnStatus)
                            }
                        } else {
                            item {
                                AppCard {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Outlined.Route,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "No Routine Locations for Today",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Auto-learning generates your routine from GPS history, or tap '+ ADD CUSTOM LOCATION' above to set a routine anchor manually.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // ─── TAB 2: ANOMALY HISTORY ──────────────────────────
                        item {
                            Text(
                                "DETECTED ROUTINE DEVIATIONS",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (anomalies.isNotEmpty()) {
                            items(anomalies) { anomaly ->
                                AnomalyHistoryItem(
                                    anomaly = anomaly,
                                    onRespond = { resp -> viewModel.respondToAnomaly(anomaly.id, resp) }
                                )
                            }
                        } else {
                            item {
                                AppCard {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = StatusApproved,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "No Anomalies Detected",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "All recent movements and timings align with your learned routine habits.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // ─── TAB 3: AUTO-LEARN STATUS ────────────────────────
                        item {
                            AutoLearnStatusCard(
                                status = autoLearnStatus,
                                onManualRefresh = {
                                    viewModel.refreshAutoLearn { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
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
private fun RoutineTodayHeader(
    routine: DailyRoutine?,
    nextExpected: RoutineLocation?,
    locationsCount: Int
) {
    val dayName = LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

    AppCard(
        accentGradient = Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                DeepCyan.copy(alpha = 0.05f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$dayName's Learned Routine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$locationsCount active location anchors • Hybrid Protection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (nextExpected != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "NEXT EXPECTED DESTINATION",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${nextExpected.name} (≈ ${nextExpected.typicalArrivalTime})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineLocationCard(
    location: RoutineLocation,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        location.name.contains("Home", ignoreCase = true) -> Icons.Filled.Home
                        location.name.contains("Work", ignoreCase = true) || location.name.contains("Office", ignoreCase = true) -> Icons.Filled.Work
                        location.name.contains("Gym", ignoreCase = true) || location.name.contains("Fitness", ignoreCase = true) -> Icons.Filled.FitnessCenter
                        else -> Icons.Filled.Place
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        location.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = if (location.source == "manual") AmberOrange.copy(alpha = 0.15f) else StatusApproved.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (location.source == "manual") "Manual" else "Learned",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (location.source == "manual") AmberOrange else StatusApproved,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "Typical Arrival ≈ ${location.typicalArrivalTime}  •  Departure ≈ ${location.typicalDepartureTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Confidence: ${(location.confidence * 100).toInt()}% • Frequency: ${location.visitFrequency} visits (30 days)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = CoralRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RoutineTabFooter(autoLearnStatus: AutoLearnStatus) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Auto-learned from 30 days GPS history",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Text(
            "Last updated: ${if (autoLearnStatus.lastAutoLearnTime > 0) formatTimeAgo(autoLearnStatus.lastAutoLearnTime) else "Initial setup"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun AutoLearnStatusCard(
    status: AutoLearnStatus,
    onManualRefresh: () -> Unit
) {
    AppCard {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Learning Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("30-Day GPS Clusterer & Time Window Modeler", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (status.isCollecting) {
                Surface(
                    color = AmberOrange.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HourglassTop, contentDescription = null, tint = AmberOrange)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Data Collection Active (Day ${status.collectionDay} of 3)", fontWeight = FontWeight.Bold, color = AmberOrange)
                            Text("Collecting raw GPS coordinates. Auto-learning activates on Day 4.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Metrics table
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRow(
                    label = "Last Auto-Learn",
                    value = if (status.lastAutoLearnTime > 0) formatTimeAgo(status.lastAutoLearnTime) else "Never"
                )
                StatusRow(
                    label = "Locations Discovered",
                    value = "${status.locationsDiscovered} Anchor Locations"
                )
                StatusRow(
                    label = "Next Scheduled Update",
                    value = formatNextScheduled(status.nextScheduledUpdate)
                )
                StatusRow(
                    label = "Clustering Radius",
                    value = "< 100 meters (Rule-based)"
                )
                StatusRow(
                    label = "Engine Status",
                    value = status.message
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onManualRefresh,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manual Refresh Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnomalyHistoryItem(
    anomaly: AnomalyDetected,
    onRespond: (String) -> Unit
) {
    AppCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeColor = when (anomaly.severity) {
                    AnomalySeverity.HIGH -> EmergencyRed
                    AnomalySeverity.MEDIUM -> AmberOrange
                    AnomalySeverity.LOW -> StatusApproved
                }
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        anomaly.severity.displayName,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    formatTimestamp(anomaly.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(anomaly.anomalyType.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Detected: ${anomaly.currentAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!anomaly.expectedLocation.isNullOrBlank()) {
                Text(
                    "Expected: ${anomaly.expectedLocation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "Confidence: ${(anomaly.confidenceScore * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ActiveAnomalyBanner(
    alert: AnomalyDetected,
    onRespond: (String) -> Unit,
    onSOS: () -> Unit
) {
    AppCard(
        accentGradient = Brush.horizontalGradient(
            colors = listOf(EmergencyRed.copy(alpha = 0.25f), CoralRed.copy(alpha = 0.15f))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Routine Deviation Detected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EmergencyRed)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You are at unexpected location: ${alert.currentAddress}. Are you safe?",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onRespond("Safe") },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("I am Safe", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSOS,
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("Trigger SOS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatTimestamp(time: Long): String {
    return SimpleDateFormat("EEE, MMM dd • hh:mm a", Locale.getDefault()).format(Date(time))
}

private fun formatNextScheduled(time: Long): String {
    if (time <= 0) return "Tomorrow 06:00 AM"
    return SimpleDateFormat("EEE hh:mm a", Locale.getDefault()).format(Date(time))
}

private fun formatTimeAgo(time: Long): String {
    val diff = System.currentTimeMillis() - time
    val mins = diff / (60 * 1000)
    val hours = mins / 60
    val days = hours / 24
    return when {
        mins < 5 -> "Just now"
        mins < 60 -> "$mins minutes ago"
        hours < 24 -> "$hours hours ago"
        else -> "$days days ago"
    }
}
