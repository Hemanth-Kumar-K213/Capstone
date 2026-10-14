package com.womensafety.system.ui.safety

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.*
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.RoutineMonitoringViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineMonitoringScreen(
    viewModel: RoutineMonitoringViewModel,
    onBack: () -> Unit,
    onTriggerSOS: () -> Unit = {}
) {
    val routine by viewModel.todayRoutine.collectAsState()
    val anomalies by viewModel.detectedAnomalies.collectAsState()
    val nextExpected by viewModel.nextExpectedLocation.collectAsState()
    val activeAlert by viewModel.activeAlert.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Routine Patterns, 1: Anomaly History, 2: Capstone Demo Test

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
            // Tabs: Patterns | Anomaly History | Capstone Demo
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Patterns (${routine?.typicalLocations?.size ?: 0})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Anomalies (${anomalies.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Capstone Demo", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
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
                        // ─── TAB 0: ROUTINE PATTERNS ────────────────────────
                        item {
                            RoutineOverviewHeader(routine = routine, nextExpected = nextExpected)
                        }

                        if (routine != null && routine!!.typicalLocations.isNotEmpty()) {
                            item {
                                Text(
                                    "TYPICAL LOCATIONS (AUTO-LEARNED)",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            items(routine!!.typicalLocations) { pattern ->
                                LocationPatternItem(pattern)
                            }
                        } else {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Outlined.Route,
                                    title = "Analyzing Daily Habits",
                                    subtitle = "Trinetra builds your routine automatically as you travel throughout the week."
                                )
                            }
                        }
                    }

                    1 -> {
                        // ─── TAB 1: ANOMALY HISTORY ─────────────────────────
                        item {
                            Text(
                                "RECENT BEHAVIORAL ANOMALIES",
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
                                EmptyStateCard(
                                    icon = Icons.Filled.CheckCircle,
                                    title = "No Anomalies Detected",
                                    subtitle = "All recent movements and timings align with your historical routines."
                                )
                            }
                        }
                    }

                    2 -> {
                        // ─── TAB 2: CAPSTONE TEST HARNESS ───────────────────
                        item {
                            CapstoneDemoSimulatorCard(
                                onSimulateAnomaly = { severity -> viewModel.simulateDemoAnomaly(severity) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineOverviewHeader(
    routine: DailyRoutine?,
    nextExpected: LocationPattern?
) {
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
                        "${routine?.dayOfWeek?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Today"}'s Learned Routine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Confidence Score: ${((routine?.confidenceScore ?: 0.85f) * 100).toInt()}% • Auto-Learning Active",
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
                                "${nextExpected.name} (${nextExpected.typicalTimeRange.displayString()})",
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
private fun LocationPatternItem(pattern: LocationPattern) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        pattern.name.contains("Home", ignoreCase = true) -> Icons.Filled.Home
                        pattern.name.contains("Work", ignoreCase = true) || pattern.name.contains("Office", ignoreCase = true) -> Icons.Filled.Work
                        else -> Icons.Filled.Place
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pattern.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${pattern.typicalTimeRange.displayString()} • Avg ${pattern.averageTimeSpentMinutes} mins",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Frequency: ${pattern.frequency}x/wk • Radius: ${pattern.radiusMeters.toInt()}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Surface(
                color = StatusApproved.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "${(pattern.confidenceScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = StatusApproved,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AnomalyHistoryItem(
    anomaly: AnomalyDetected,
    onRespond: (String) -> Unit
) {
    val dateStr = remember(anomaly.timestamp) {
        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(anomaly.timestamp))
    }

    AppCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = when (anomaly.severity) {
                        AnomalySeverity.HIGH -> CoralRed
                        AnomalySeverity.MEDIUM -> AmberOrange
                        AnomalySeverity.LOW -> StatusApproved
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        anomaly.severity.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    anomaly.anomalyType.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                anomaly.currentAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!anomaly.expectedLocation.isNullOrBlank()) {
                Text(
                    "Expected: ${anomaly.expectedLocation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (anomaly.userResponded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = StatusApproved, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Resolved: Marked as \"${anomaly.userResponse}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusApproved,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onRespond("Safe") },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Text("I'm Safe", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onRespond("Regular Place") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Text("Regular Place", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
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
            colors = listOf(CoralRed.copy(alpha = 0.25f), AmberOrange.copy(alpha = 0.15f))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = CoralRed)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "ACTIVE ROUTINE ANOMALY",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CoralRed
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(alert.currentAddress, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onRespond("Safe") },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("I'm Safe")
                }
                Button(
                    onClick = onSOS,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Text("SOS")
                }
            }
        }
    }
}

@Composable
private fun CapstoneDemoSimulatorCard(
    onSimulateAnomaly: (AnomalySeverity) -> Unit
) {
    AppCard(
        accentGradient = Brush.horizontalGradient(
            colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), DeepCyan.copy(alpha = 0.1f))
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Capstone Evaluator Test Harness",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Trigger live routine anomaly scenarios to test user feedback, auto-learning, and risk score escalations instantly without waiting 30 days:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSimulateAnomaly(AnomalySeverity.HIGH) },
                colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulate HIGH Risk Anomaly (Off-Route 2 AM)", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { onSimulateAnomaly(AnomalySeverity.MEDIUM) },
                colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulate MODERATE Anomaly (Unusual Time)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    AppCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
