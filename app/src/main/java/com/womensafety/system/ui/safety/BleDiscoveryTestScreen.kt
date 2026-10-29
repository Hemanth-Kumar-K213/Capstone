package com.womensafety.system.ui.safety

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.manager.WorkplaceBeaconManager
import com.womensafety.system.manager.WorkplaceColleagueScannerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Requirement 2D: BLE Discovery Diagnostic Test Screen.
 * Allows interactive verification of BLE broadcasting, scanning, RSSI calculation,
 * and colleague discovery across multiple Android test devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleDiscoveryTestScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val authManager = remember { FirebaseAuthManager() }
    val firestoreManager = remember { FirestoreManager() }

    var scannedColleagues by remember { mutableStateOf<List<Colleague>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var isAdvertising by remember { mutableStateOf(false) }
    val logMessages = remember { mutableStateListOf<String>() }

    fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        logMessages.add(0, "[$time] $msg")
        Log.d("BLE_DIAGNOSTIC", msg)
    }

    val beaconManager = remember { WorkplaceBeaconManager(context) }
    val scannerManager = remember {
        WorkplaceColleagueScannerManager(
            context = context,
            firestoreManager = firestoreManager,
            authManager = authManager,
            onColleagueDiscovered = { colleague ->
                val distStr = colleague.distanceMeters?.let { "${it.toInt()}m" } ?: "unknown distance"
                appendLog("DISCOVERED: ${colleague.name} (${colleague.userId}) at $distStr (RSSI: ${colleague.rssi})")
            },
            onColleaguesUpdate = { list ->
                scannedColleagues = list
            }
        )
    }

    val handler = remember { Handler(Looper.getMainLooper()) }

    fun startBleTest() {
        appendLog("Initiating BLE test...")
        val uid = authManager.currentUserId ?: "test-user-${System.currentTimeMillis() % 1000}"

        beaconManager.onAdvertisingStatusChanged = { active, errorReason ->
            isAdvertising = active
            if (active) {
                appendLog("BLE Advertiser: SUCCESS (Broadcasting on air)")
            } else {
                appendLog("BLE Advertiser: FAILED - ${errorReason ?: "Stopped"}")
            }
        }

        // 1. Start Advertising
        val advStarted = beaconManager.startBeaconAdvertising(uid, "Test Device (${uid.take(4)})")
        isAdvertising = advStarted
        appendLog("BLE Advertising request: $advStarted")

        // 2. Start Scanning
        scannerManager.startScanning(uid)
        isScanning = true
        appendLog("BLE Scanning started with 30s auto-stop watchdog")

        // Auto stop after 30 seconds
        handler.postDelayed({
            if (isScanning) {
                appendLog("30s test duration expired. Stopping scan.")
                scannerManager.stopScanning()
                isScanning = false
            }
        }, 30000L)
    }

    fun stopBleTest() {
        appendLog("Stopping BLE test...")
        beaconManager.stopBeaconAdvertising()
        scannerManager.stopScanning()
        isScanning = false
        isAdvertising = false
        appendLog("BLE test stopped.")
    }

    DisposableEffect(Unit) {
        appendLog("Diagnostic screen opened. Bluetooth enabled: ${beaconManager.isBluetoothEnabled()}")
        onDispose {
            stopBleTest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Discovery Diagnostic", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (isScanning) stopBleTest() else startBleTest()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isScanning) "Stop Scan" else "Start BLE Scan (30s)")
                }

                OutlinedButton(
                    onClick = { logMessages.clear() }
                ) {
                    Text("Clear Logs")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "Discovered Colleagues (${scannedColleagues.size}):",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            if (scannedColleagues.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        if (isScanning) "Scanning nearby BLE beacons on UUID 0000180A... Bring peer device close." else "Scan idle. Tap 'Start BLE Scan' to discover nearby devices.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scannedColleagues) { colleague ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "${colleague.name} (${colleague.userId.take(8)})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val distText = colleague.distanceMeters?.let { String.format(Locale.US, "%.1f", it) + "m" } ?: "N/A"
                                    Text("Dist: $distText", fontSize = 12.sp)
                                    Text("RSSI: ${colleague.rssi} dBm", fontSize = 12.sp)
                                    Text("Method: ${colleague.detectionMethod}", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Diagnostic Logcat Output:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(logMessages.size) { index ->
                        Text(
                            text = logMessages[index],
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
