package com.womensafety.system.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.womensafety.system.data.health.HealthConnectManager
import com.womensafety.system.data.health.model.HealthConnectStatus
import com.womensafety.system.data.health.model.HealthDataFreshness
import com.womensafety.system.data.health.model.HeartRateMeasurement
import com.womensafety.system.ui.components.SafetyTopAppBar
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.HeartRateMonitoringViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateMonitoringScreen(
    viewModel: HeartRateMonitoringViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Activity Result Launcher for Health Connect permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        val isGranted = grantedPermissions.contains(HealthConnectManager.HEART_RATE_PERMISSION)
        viewModel.onPermissionResult(isGranted, context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkStatusAndPermissions(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SafetyDarkBackground)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Heart Rate Monitoring",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SafetyTextPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SafetyTextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = SafetyDarkBackground
            )
        )

        // Navigation Tabs (Diagnostics vs Test Harness)
        TabRow(
            selectedTabIndex = uiState.activeTab,
            containerColor = SafetyCardBackground,
            contentColor = SafetyPrimaryBlue,
            divider = { HorizontalDivider(color = SafetyCardDivider) }
        ) {
            Tab(
                selected = uiState.activeTab == 0,
                onClick = { viewModel.setActiveTab(0) },
                text = {
                    Text(
                        "Live Diagnostics",
                        fontWeight = if (uiState.activeTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = uiState.activeTab == 1,
                onClick = { viewModel.setActiveTab(1) },
                text = {
                    Text(
                        "Risk Test Harness",
                        fontWeight = if (uiState.activeTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        if (uiState.activeTab == 0) {
            LiveDiagnosticsTab(
                uiState = uiState,
                onRequestPermission = {
                    permissionLauncher.launch(setOf(HealthConnectManager.HEART_RATE_PERMISSION))
                },
                onRefreshNow = { viewModel.refreshNow(context) },
                onToggleMonitoring = {
                    if (uiState.isMonitoring) {
                        viewModel.stopMonitoring()
                    } else {
                        viewModel.startMonitoring(context)
                    }
                }
            )
        } else {
            RiskScenarioTestHarnessTab(
                uiState = uiState,
                onSelectScenario = { viewModel.selectScenario(it) },
                onRunScenario = { viewModel.runActiveTestScenario(context) }
            )
        }
    }
}

@Composable
private fun LiveDiagnosticsTab(
    uiState: com.womensafety.system.viewmodel.HeartRateUiState,
    onRequestPermission: () -> Unit,
    onRefreshNow: () -> Unit,
    onToggleMonitoring: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Health Connect Status & Permission Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafetyCardBackground)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "HEALTH CONNECT INTEGRATION",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = SafetyTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Provider Availability Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Provider Status", style = MaterialTheme.typography.bodyMedium, color = SafetyTextSecondary)
                        val (statusText, statusColor) = when (uiState.healthConnectStatus) {
                            is HealthConnectStatus.Available -> "Available" to StatusApproved
                            is HealthConnectStatus.UpdateRequired -> "Update Required" to GradientAmber
                            is HealthConnectStatus.Unavailable -> "Unavailable" to StatusDenied
                            is HealthConnectStatus.Error -> "Error" to StatusDenied
                        }
                        Text(statusText, fontWeight = FontWeight.Bold, color = statusColor)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Permission Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Heart-Rate Permission", style = MaterialTheme.typography.bodyMedium, color = SafetyTextSecondary)
                        Text(
                            text = if (uiState.hasPermission) "Granted (Read-Only)" else "Denied / Not Granted",
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.hasPermission) StatusApproved else StatusDenied
                        )
                    }

                    if (!uiState.hasPermission) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SafetyPrimaryBlue)
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Health Connect Permission")
                        }
                    }
                }
            }
        }

        // 2. Data Origin & Device Sync Trace
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafetyCardBackground)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "HARDWARE & SYNC PATHWAY",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = SafetyTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "boAt Storm Plus ⌚ → boAt Crest 📱 → Google Fit 🏃 → Health Connect 🔗 → Trinetra 🛡️",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SyncTraceBadge("Trinetra ← Health Connect SDK", "Observed", StatusApproved)
                    SyncTraceBadge(
                        "Health Connect Data Origin",
                        uiState.latestMeasurement?.dataOriginPackage ?: "Waiting for Record",
                        if (uiState.latestMeasurement != null) StatusApproved else SafetyTextMuted
                    )
                    SyncTraceBadge("boAt Crest → Google Fit Sync", "External (Unverified)", GradientAmber)
                    SyncTraceBadge("boAt Watch BLE Sensor Sync", "Hardware (Unverified)", GradientAmber)
                    SyncTraceBadge("Physical Wearer Identity", "Device-Local (Unverified)", GradientAmber)
                }
            }
        }

        // 3. Latest Measurement Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafetyCardBackground)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LATEST MEASUREMENT",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                            color = SafetyTextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        val freshness = uiState.latestMeasurement?.freshness() ?: HealthDataFreshness.UNAVAILABLE
                        FreshnessBadge(freshness)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val latest = uiState.latestMeasurement
                    if (latest != null) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${latest.bpm}",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = StatusDenied
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "BPM",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = SafetyTextMuted,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Recorded: ${DateUtils.formatDateTime(latest.timestamp)} (${formatAge(latest.timestamp)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextSecondary
                        )
                        Text(
                            text = "Source Origin: ${latest.dataOriginPackage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextMuted
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = SafetyTextMuted, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No heart rate records available", style = MaterialTheme.typography.bodyMedium, color = SafetyTextMuted)
                            Text("Ensure boAt Crest / Google Fit has written records to Health Connect.", style = MaterialTheme.typography.bodySmall, color = SafetyTextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SafetyCardDivider)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (uiState.lastQueryTimestamp != null)
                                "Last queried: ${DateUtils.formatDateTime(uiState.lastQueryTimestamp)}"
                            else "Not queried yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafetyTextMuted
                        )
                        if (uiState.isWaitingForNewerRecord) {
                            Text(
                                text = "Waiting for newer record…",
                                style = MaterialTheme.typography.labelSmall,
                                color = GradientAmber
                            )
                        }
                    }
                }
            }
        }

        // 4. Monitoring Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRefreshNow,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isQuerying
                ) {
                    if (uiState.isQuerying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SafetyPrimaryBlue)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh Now")
                }

                Button(
                    onClick = onToggleMonitoring,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isMonitoring) StatusDenied else SafetyPrimaryBlue
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.isMonitoring) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (uiState.isMonitoring) "Stop (15s)" else "Start (15s)")
                }
            }
        }

        // 5. Recent Measurements List
        item {
            Text(
                text = "RECENT MEASUREMENTS (${uiState.recentMeasurements.size})",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = SafetyTextMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (uiState.recentMeasurements.isEmpty()) {
            item {
                Text(
                    text = "No recorded heart rate samples found in bounded time window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafetyTextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(uiState.recentMeasurements) { sample ->
                SampleItemRow(sample)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SampleItemRow(sample: HeartRateMeasurement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SafetyCardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${sample.bpm} BPM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafetyTextPrimary
                )
                Text(
                    text = DateUtils.formatDateTime(sample.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = SafetyTextSecondary
                )
            }
            Text(
                text = formatAge(sample.timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = SafetyTextMuted
            )
        }
    }
}

@Composable
private fun RiskScenarioTestHarnessTab(
    uiState: com.womensafety.system.viewmodel.HeartRateUiState,
    onSelectScenario: (Int) -> Unit,
    onRunScenario: () -> Unit
) {
    val scenarios = listOf(
        "1. Normal Monitoring (Score 0)",
        "2. Confirmation Threshold Crossed (Score 5)",
        "3. Capstone Critical Risk (+3 Route, +2 Night, +4 HR = 9)",
        "4. Safe Response Simulation",
        "5. Help Response Simulation",
        "6. No Response 60s Timeout Simulation",
        "7. Stale Health Data Simulation",
        "8. Missing Health Data Simulation",
        "9. Health Connect Permission Denial",
        "10. Notification Permission Denial"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Test Mode Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF382300))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = GradientAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "ISOLATED TEST MODE ACTIVE",
                            fontWeight = FontWeight.Black,
                            color = GradientAmber,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Simulations do not inject fake data into Health Connect. Real cellular SMS, 2Factor SMS, and police/contact notifications are strictly SUPPRESSED.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextSecondary
                    )
                }
            }
        }

        // Capstone Scenario Breakdown Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafetyCardBackground)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "CAPSTONE DOCUMENTED SCENARIO",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = SafetyTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val result = uiState.scenarioResult
                    ScoreRow("Significant Route Deviation", "+3", result?.routeDeviationContribution ?: 0 > 0)
                    ScoreRow("Late-Night Travel Context", "+2", result?.lateNightContribution ?: 0 > 0)
                    ScoreRow("Abnormal Heart-Rate Spike", "+4", result?.heartRateSpikeContribution ?: 0 > 0)

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = SafetyCardDivider)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Composite Risk Score", fontWeight = FontWeight.Bold, color = SafetyTextPrimary)
                        Text(
                            "${result?.totalRiskScore ?: 0} / 9",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if ((result?.totalRiskScore ?: 0) >= 9) StatusDenied else GradientAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Classification", style = MaterialTheme.typography.labelSmall, color = SafetyTextMuted)
                        Text(
                            if ((result?.totalRiskScore ?: 0) >= 9) "CRITICAL EMERGENCY" else "ELEVATED CONTEXT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if ((result?.totalRiskScore ?: 0) >= 9) StatusDenied else GradientAmber
                        )
                    }
                }
            }
        }

        // Scenario Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SafetyCardBackground)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "SELECT TEST SCENARIO",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = SafetyTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    scenarios.forEachIndexed { index, title ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectScenario(index) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedScenarioIndex == index,
                                onClick = { onSelectScenario(index) },
                                colors = RadioButtonDefaults.colors(selectedColor = SafetyPrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title, style = MaterialTheme.typography.bodyMedium, color = SafetyTextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRunScenario,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyPrimaryBlue)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Execute Test Scenario")
                    }

                    if (uiState.testStatusMessage != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E2838))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = uiState.testStatusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF81D4FA)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun ScoreRow(title: String, scoreStr: String, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) SafetyTextPrimary else SafetyTextMuted
        )
        Text(
            text = scoreStr,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isActive) GradientAmber else SafetyTextMuted
        )
    }
}

@Composable
private fun SyncTraceBadge(label: String, state: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SafetyTextSecondary)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(state, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun FreshnessBadge(freshness: HealthDataFreshness) {
    val (text, color) = when (freshness) {
        HealthDataFreshness.FRESH -> "FRESH (<10m)" to StatusApproved
        HealthDataFreshness.STALE -> "STALE (≥10m)" to GradientAmber
        HealthDataFreshness.UNAVAILABLE -> "UNAVAILABLE" to SafetyTextMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun formatAge(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    return when {
        seconds < 60 -> "${seconds}s ago"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${hours / 24}d ago"
    }
}
