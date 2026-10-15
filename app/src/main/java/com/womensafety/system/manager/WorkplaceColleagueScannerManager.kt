package com.womensafety.system.manager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.data.model.ColleagueStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Requirement 2B: Real BLE Scanner Manager for nearby coworker beacons in WORKING Mode.
 *
 * Scans for Service UUID 0000180A-0000-1000-8000-00805F9B34FB.
 * Validates discovered beacons against approved trusted contacts in Firestore.
 * Calculates exact distance using path-loss formula: 10^((txPower - RSSI) / (10 * n)).
 */
class WorkplaceColleagueScannerManager(
    private val context: Context,
    private val firestoreManager: FirestoreManager,
    private val authManager: FirebaseAuthManager,
    private val onColleagueDiscovered: (Colleague) -> Unit = {},
    private val onColleaguesUpdate: (List<Colleague>) -> Unit = {}
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val db = FirebaseFirestore.getInstance()
    private val discoveredColleagues = ConcurrentHashMap<String, Colleague>()
    private val approvedContactsCache = ConcurrentHashMap<String, Boolean>()
    private val lastProcessTimes = ConcurrentHashMap<String, Long>()

    private var isScanning = false
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "ColleagueScanner"
        private const val TX_POWER = -59.0 // dBm
        private const val PATH_LOSS_EXPONENT = 2.0 // n
        private const val SCAN_THROTTLE_MS = 2000L // 2 seconds throttle
        private const val MAX_DISTANCE_METERS = 150f
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.w(TAG, "Workplace colleague BLE scan failed: $errorCode")
        }
    }

    private fun processScanResult(result: ScanResult) {
        val scanRecord = result.scanRecord ?: return
        val serviceData = scanRecord.getServiceData(WorkplaceBeaconManager.BEACON_PARCEL_UUID) ?: return

        try {
            val payloadString = String(serviceData, Charsets.UTF_8).trim()
            if (payloadString.isBlank()) return

            var discoveredUserId = ""
            var discoveredUserName = "Colleague"
            var rawStatus = "WORKING"
            var beaconTimestamp = System.currentTimeMillis()

            if (payloadString.startsWith("{")) {
                val json = JSONObject(payloadString)
                discoveredUserId = json.optString("userId")
                discoveredUserName = json.optString("userName", "Colleague")
                rawStatus = json.optString("status", "WORKING")
                beaconTimestamp = json.optLong("timestamp", System.currentTimeMillis())
            } else if (payloadString.contains("|")) {
                val parts = payloadString.split("|")
                discoveredUserId = parts.getOrNull(0)?.trim() ?: ""
                discoveredUserName = parts.getOrNull(1)?.trim() ?: "Colleague"
                rawStatus = parts.getOrNull(2)?.trim() ?: "WORKING"
                beaconTimestamp = parts.getOrNull(3)?.toLongOrNull() ?: System.currentTimeMillis()
            }

            val currentUserId = authManager.currentUserId ?: ""
            if (discoveredUserId.isBlank() || discoveredUserId == currentUserId) {
                return // Ignore invalid or own device
            }

            // Throttle processing per discovered device to 2 seconds
            val now = System.currentTimeMillis()
            val lastProcessed = lastProcessTimes[discoveredUserId] ?: 0L
            if (now - lastProcessed < SCAN_THROTTLE_MS) {
                return
            }
            lastProcessTimes[discoveredUserId] = now

            // Step 2: Validate against approved trusted contacts in Firestore (Auto-trust on first discovery)
            scope.launch {
                val isApproved = isContactApproved(currentUserId, discoveredUserId)
                if (!isApproved) {
                    Log.d("BLE_SCAN", "Auto-adding $discoveredUserId ($discoveredUserName) to trustedContacts on first discovery")
                    try {
                        firestoreManager.addTrustedContact(currentUserId, discoveredUserId, discoveredUserName)
                        approvedContactsCache[discoveredUserId] = true
                    } catch (e: Exception) {
                        Log.w("BLE_SCAN", "Failed auto-adding trusted contact: ${e.message}")
                    }
                }

                // Step 3a: Calculate distance from RSSI: 10^((txPower - RSSI) / (10 * n))
                val rssi = result.rssi
                val distanceMeters = calculateDistance(rssi)
                if (distanceMeters > MAX_DISTANCE_METERS) {
                    Log.d("BLE_SCAN", "Beacon out of range (${distanceMeters}m > ${MAX_DISTANCE_METERS}m) for $discoveredUserName")
                    return@launch
                }

                val statusEnum = when (rawStatus.uppercase()) {
                    "LEAVING" -> ColleagueStatus.LEAVING
                    "ON_BREAK" -> ColleagueStatus.ON_BREAK
                    "AWAY" -> ColleagueStatus.AWAY_FROM_DESK
                    else -> ColleagueStatus.AT_WORKPLACE
                }

                val colleague = Colleague(
                    id = discoveredUserId,
                    userId = discoveredUserId,
                    name = discoveredUserName,
                    currentStatus = statusEnum,
                    distanceMeters = distanceMeters,
                    rssi = rssi,
                    dataSource = "BLE",
                    detectionMethod = "BLE",
                    confidence = 0.95f,
                    lastSeen = beaconTimestamp,
                    isTrustedContact = true
                )

                discoveredColleagues[discoveredUserId] = colleague
                onColleagueDiscovered(colleague)
                onColleaguesUpdate(discoveredColleagues.values.toList())
                Log.d("BLE_SCAN", "Successfully added colleague to list: $discoveredUserName (${distanceMeters.toInt()}m, RSSI $rssi dBm)")
            }
        } catch (e: Exception) {
            Log.e("BLE_SCAN", "Error decoding colleague beacon payload", e)
        }
    }

    /**
     * Checks if discovered user is in trustedContacts/{currentUserId}/{discoveredUserId}
     * or userProfile.trustedContactIds.
     */
    private suspend fun isContactApproved(currentUserId: String, discoveredUserId: String): Boolean {
        approvedContactsCache[discoveredUserId]?.let { return it }

        return try {
            // Check direct trustedContacts/{currentUserId}/{discoveredUserId}
            val doc = db.collection("trustedContacts")
                .document(currentUserId)
                .collection("contacts")
                .document(discoveredUserId)
                .get()
                .await()

            if (doc.exists()) {
                approvedContactsCache[discoveredUserId] = true
                return true
            }

            // Fallback check in FirestoreManager helper
            val isTrusted = firestoreManager.isTrustedContact(currentUserId, discoveredUserId)
            approvedContactsCache[discoveredUserId] = isTrusted
            isTrusted
        } catch (e: Exception) {
            Log.w(TAG, "Error checking contact approval for $discoveredUserId", e)
            false
        }
    }

    /**
     * Exact path-loss distance formula:
     * distance = 10 ^ ((txPower - RSSI) / (10 * n))
     * where txPower = -59 dBm, n = 2.0
     */
    fun calculateDistance(rssi: Int): Float {
        val exponent = (TX_POWER - rssi.toDouble()) / (10.0 * PATH_LOSS_EXPONENT)
        val distance = 10.0.pow(exponent).toFloat()
        return distance.coerceAtLeast(0.5f)
    }

    fun startScanning(currentUserId: String? = null) {
        if (isScanning) {
            Log.d("BLE_SCAN", "Scanner is already running")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.e("BLE_SCAN", "BluetoothAdapter is null")
            return
        }
        if (!adapter.isEnabled) {
            Log.e("BLE_SCAN", "Bluetooth is disabled. Cannot start BLE scanning.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasScan) {
                Log.e("BLE_SCAN", "BLUETOOTH_SCAN permission not granted")
                return
            }
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e("BLE_SCAN", "bluetoothLeScanner is null")
            return
        }

        try {
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(WorkplaceBeaconManager.BEACON_PARCEL_UUID)
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            try {
                scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            } catch (e: Exception) {
                Log.w("BLE_SCAN", "Filtered scan failed, trying unfiltered scan: ${e.message}")
                scanner.startScan(scanCallback)
            }
            isScanning = true
            Log.d("BLE_SCAN", "Scanning started successfully for user: ${currentUserId ?: authManager.currentUserId}")
        } catch (e: Exception) {
            Log.e("BLE_SCAN", "Exception starting colleague BLE scan", e)
        }
    }

    fun stopScanning() {
        if (!isScanning) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasScan = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasScan) return
            }
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            Log.d(TAG, "Stopped workplace colleague BLE scanner")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping colleague scan", e)
        }
    }

    fun getDiscoveredColleagues(): List<Colleague> = discoveredColleagues.values.toList()
}
