package com.womensafety.system.ui.health

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.womensafety.system.data.health.HealthConnectManager
import com.womensafety.system.data.health.model.HealthConnectStatus
import com.womensafety.system.data.health.model.HealthDataFreshness
import com.womensafety.system.data.health.model.HeartRateMeasurement
import com.womensafety.system.manager.BleHeartRateManager
import com.womensafety.system.ui.components.AppCard
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
    val isDark = LocalDarkTheme.current

    // Launcher for Health Connect permission
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        val isGranted = grantedPermissions.contains(HealthConnectManager.HEART_RATE_PERMISSION)
        viewModel.onPermissionResult(isGranted, context)
    }

    // BLE direct manager initialization
    LaunchedEffect(Unit) {
        BleHeartRateManager.init(context)
        viewModel.checkStatusAndPermissions(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Smartwatch & Heart Rate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Navigation Tabs (Diagnostics, Direct BLE Watch, Capstone Test Harness)
            TabRow(
                selectedTabIndex = uiState.activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    text = { Text("Health Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    text = { Text("Direct BLE Watch", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.activeTab == 2,
                    onClick = { viewModel.setActiveTab(2) },
                    text = { Text("Test Harness", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            when (uiState.activeTab) {
                0 -> LiveDiagnosticsTab(
                    uiState = uiState,
                    onRequestPermission = {
                        healthConnectPermissionLauncher.launch(setOf(HealthConnectManager.HEART_RATE_PERMISSION))
                    },
                    onRefreshNow = { viewModel.refreshNow(context) },
                    onToggleMonitoring = {
                        if (uiState.isMonitoring) viewModel.stopMonitoring()
                        else viewModel.startMonitoring(context)
                    }
                )
                1 -> DirectBleWatchTab()
                2 -> RiskScenarioTestHarnessTab(
                    uiState = uiState,
                    onSelectScenario = { viewModel.selectScenario(it) },
                    onRunScenario = { viewModel.runActiveTestScenario(context) }
                )
            }
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Health Connect Status & Permission Card
        item {
            AppCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "HEALTH CONNECT INTEGRATION",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Provider Availability Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Provider Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        val (statusText, statusColor) = when (uiState.healthConnectStatus) {
                            is HealthConnectStatus.Available -> "Available" to StatusApproved
                            is HealthConnectStatus.UpdateRequired -> "Update Required" to AmberWarning
                            is HealthConnectStatus.Unavailable -> "Unavailable" to EmergencyRed
                            is HealthConnectStatus.Error -> "Error" to EmergencyRed
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
                        Text("Heart-Rate Permission", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = if (uiState.hasPermission) "Granted (Read-Only)" else "Denied / Not Granted",
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.hasPermission) StatusApproved else EmergencyRed
                        )
                    }

                    if (!uiState.hasPermission) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Health Connect Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Data Origin & Device Sync Trace
        item {
            AppCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "HARDWARE & SYNC PATHWAY",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Smartwatch ⌚ → Companion App 📱 → Google Fit / Health Connect 🔗 → Trinetra 🛡️",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SyncTraceBadge("Trinetra ← Health Connect SDK", "Active", StatusApproved)
                    SyncTraceBadge(
                        "Health Connect Data Origin",
                        uiState.latestMeasurement?.dataOriginPackage ?: "Waiting for Record",
                        if (uiState.latestMeasurement != null) StatusApproved else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SyncTraceBadge("Companion App Sync", "External Pathway", AmberWarning)
                    SyncTraceBadge("Smartwatch BLE Sensor", "Bluetooth Sensor", AmberWarning)
                }
            }
        }

        // 3. Latest Measurement Card
        item {
            AppCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LATEST MEASUREMENT",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                color = EmergencyRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BPM",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sample Timestamp: ${DateUtils.formatDateTime(latest.timestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Source Origin: ${latest.dataOriginPackage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No heart rate records available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Ensure your watch companion app / Google Fit writes records to Health Connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.isWaitingForNewerRecord) {
                            Text(
                                text = "Waiting for newer record…",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberWarning
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
                    enabled = !uiState.isQuerying,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isQuerying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
                        containerColor = if (uiState.isMonitoring) EmergencyRed else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (uiState.recentMeasurements.isEmpty()) {
            item {
                Text(
                    text = "No recorded heart rate samples found in bounded time window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun DirectBleWatchTab() {
    val context = LocalContext.current
    val isScanning by BleHeartRateManager.isScanning.collectAsState()
    val scanResults by BleHeartRateManager.scanResults.collectAsState()
    val connectionStatus by BleHeartRateManager.connectionStatus.collectAsState()
    val serviceStatus by BleHeartRateManager.serviceStatus.collectAsState()
    val heartRateBpm by BleHeartRateManager.heartRateBpm.collectAsState()
    val lastReadTime by BleHeartRateManager.lastReadTime.collectAsState()

    var hasBlePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasBlePermission = perms.values.all { it }
        if (hasBlePermission) {
            BleHeartRateManager.startScan()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Heart Rate Card
        item {
            AppCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "LIVE SMARTWATCH TELEMETRY (BLE 0x180D)",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (heartRateBpm != null) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$heartRateBpm",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                color = EmergencyRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BPM",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Last Packet: ${lastReadTime?.let { DateUtils.formatDateTime(it) } ?: "Just now"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusApproved,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "-- BPM",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Connect a BLE smartwatch to view real-time pulse",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Status: $connectionStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    if (serviceStatus.isNotBlank()) {
                        Text(
                            text = serviceStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // BLE Scan Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (!hasBlePermission) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                blePermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    )
                                )
                            } else {
                                blePermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                        } else {
                            if (isScanning) BleHeartRateManager.stopScan()
                            else BleHeartRateManager.startScan()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) EmergencyRed else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Filled.Stop else Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isScanning) "Stop Scan" else "Scan Watches")
                }

                OutlinedButton(
                    onClick = { BleHeartRateManager.disconnect() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Disconnect")
                }
            }
        }

        // Discovered Watches List
        item {
            Text(
                text = "NEARBY SMARTWATCHES & SENSORS (${scanResults.size})",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (scanResults.isEmpty()) {
            item {
                Text(
                    text = if (isScanning) "Searching for smartwatches nearby..." else "Tap 'Scan Watches' to discover BLE fitness bands and watches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(scanResults) { result ->
                val deviceName = result.device.name ?: result.scanRecord?.deviceName ?: "Smartwatch Device"
                val deviceAddress = result.device.address
                AppCard(
                    modifier = Modifier.clickable {
                        BleHeartRateManager.connectToDevice(context, deviceAddress, deviceName)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = deviceName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = deviceAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                BleHeartRateManager.connectToDevice(context, deviceAddress, deviceName)
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SampleItemRow(sample: HeartRateMeasurement) {
    AppCard {
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = DateUtils.formatDateTime(sample.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatAge(sample.timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Test Mode Banner
        item {
            Surface(
                color = AmberWarning.copy(alpha = 0.15f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = AmberWarning)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "ISOLATED TEST MODE ACTIVE",
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning,
                            fontSize = 14.sp
                        )
                        Text(
                            "External SMS dispatch and phone calls are strictly suppressed during harness simulations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Active Scenario Evaluation Card
        item {
            AppCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "CAPSTONE SPECIFICATION SCENARIO EVALUATION",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val result = uiState.scenarioResult
                    ScoreRow("Significant Route Deviation", "+3", (result?.routeDeviationContribution ?: 0) > 0)
                    ScoreRow("Late-Night Travel Context", "+2", (result?.lateNightContribution ?: 0) > 0)
                    ScoreRow("Abnormal Heart-Rate Spike", "+4", (result?.heartRateSpikeContribution ?: 0) > 0)

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Composite Risk Score", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${result?.totalRiskScore ?: 0} / 9",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if ((result?.totalRiskScore ?: 0) >= 9) EmergencyRed else AmberWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Classification", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if ((result?.totalRiskScore ?: 0) >= 9) "CRITICAL EMERGENCY" else "ELEVATED CONTEXT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if ((result?.totalRiskScore ?: 0) >= 9) EmergencyRed else AmberWarning
                        )
                    }
                }
            }
        }

        // Scenario Selector
        item {
            AppCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "SELECT TEST SCENARIO",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    scenarios.forEachIndexed { index, title ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectScenario(index) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedScenarioIndex == index,
                                onClick = { onSelectScenario(index) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRunScenario,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Execute Test Scenario", fontWeight = FontWeight.Bold)
                    }

                    if (uiState.testStatusMessage != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.testStatusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Medium
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
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = scoreStr,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isActive) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SyncTraceBadge(label: String, state: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
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
        HealthDataFreshness.STALE -> "STALE (≥10m)" to AmberWarning
        HealthDataFreshness.UNAVAILABLE -> "UNAVAILABLE" to MaterialTheme.colorScheme.onSurfaceVariant
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
