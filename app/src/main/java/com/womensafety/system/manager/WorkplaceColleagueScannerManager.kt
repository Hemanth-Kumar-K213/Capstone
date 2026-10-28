package com.womensafety.system.manager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.data.model.ColleagueStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Requirement 1B: Complete BLE Rewrite — WorkplaceColleagueScannerManager.
 *
 * Scans for manufacturer data (0xFFFF) containing compact [len][userId][len][userName] payloads.
 * Implements continuous balanced scanning, path-loss distance calculation, and auto-trust on first discovery.
 */
class WorkplaceColleagueScannerManager(
    private val context: Context,
    private val firestoreManager: FirestoreManager = FirestoreManager(),
    private val authManager: FirebaseAuthManager = FirebaseAuthManager(),
    private val onColleagueDiscovered: (Colleague) -> Unit = {},
    private val onColleaguesUpdate: (List<Colleague>) -> Unit = {}
) {

    constructor(
        context: Context,
        firestoreManager: FirestoreManager,
        currentUserId: String
    ) : this(
        context = context,
        firestoreManager = firestoreManager,
        authManager = FirebaseAuthManager()
    )

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false
    private val discoveredColleagues = ConcurrentHashMap<String, Colleague>()
    private var scanCallback: ScanCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var customOnFoundCallback: ((Colleague) -> Unit)? = null

    companion object {
        private const val SCAN_PERIOD_MS = 2500L // Scan interval
        private const val MANUFACTURER_ID = 0xFFFF
        private const val TAG = "BLE_SCAN"
    }

    val currentUserId: String
        get() = authManager.currentUserId ?: "user"

    fun startScanning(currentUserId: String) {
        startScanning()
    }

    fun startScanning(onColleagueFound: (colleague: Colleague) -> Unit = {}) {
        this.customOnFoundCallback = onColleagueFound
        val uid = currentUserId
        Log.d(TAG, "=== START BLE SCANNING ===")
        Log.d(TAG, "currentUserId=$uid")

        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.e(TAG, "ERROR: Device does not support Bluetooth")
            return
        }

        if (!adapter.isEnabled) {
            Log.e(TAG, "ERROR: Bluetooth is disabled")
            return
        }

        // Check runtime permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasScan) {
                Log.e(TAG, "ERROR: BLUETOOTH_SCAN permission not granted")
                return
            }
        }

        try {
            scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                Log.e(TAG, "ERROR: BLE scanner not available on hardware")
                return
            }

            // Create scan callback
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let { handleScanResult(it) }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { handleScanResult(it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Scan failed - errorCode=$errorCode")
                    when (errorCode) {
                        SCAN_FAILED_ALREADY_STARTED -> Log.d(TAG, "Scan already started")
                        SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(TAG, "App registration failed")
                        SCAN_FAILED_INTERNAL_ERROR -> Log.e(TAG, "Internal error")
                        SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "Feature unsupported")
                        else -> Log.e(TAG, "Unknown scan error")
                    }
                }
            }

            isScanning = true
            continuousScan()
            Log.i(TAG, "BLE_SCAN: Started scanning")
            Log.d(TAG, "BLE scanning started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Exception during startScanning: ${e.message}", e)
        }
    }

    private fun continuousScan() {
        if (!isScanning) return

        try {
            val sc = scanner ?: return
            val cb = scanCallback ?: return

            // Scan settings
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setReportDelay(0)
                .build()

            // Open scan without hardware filters so no manufacturer packets are dropped
            sc.startScan(emptyList(), settings, cb)
            Log.d(TAG, "Starting scan batch...")

            // Schedule batch cycle
            mainHandler.postDelayed({
                if (isScanning) {
                    try {
                        sc.stopScan(cb)
                        Log.d(TAG, "Stopped scan batch, restarting...")
                    } catch (e: Exception) {
                        Log.w(TAG, "Batch stop error: ${e.message}")
                    }
                    if (isScanning) {
                        continuousScan()
                    }
                }
            }, SCAN_PERIOD_MS)

        } catch (e: Exception) {
            Log.e(TAG, "Error in continuousScan: ${e.message}")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        try {
            val record = result.scanRecord ?: return

            // 1. Try reading Manufacturer Data (0xFFFF)
            var manufacturerData: ByteArray? = record.getManufacturerSpecificData(MANUFACTURER_ID)

            // If not found at 0xFFFF directly, search manufacturer sparse array
            if (manufacturerData == null || manufacturerData.isEmpty()) {
                val sparse = record.manufacturerSpecificData
                if (sparse != null && sparse.size() > 0) {
                    for (i in 0 until sparse.size()) {
                        val data: ByteArray? = sparse.valueAt(i)
                        if (data != null && data.isNotEmpty()) {
                            manufacturerData = data
                            break
                        }
                    }
                }
            }

            if (manufacturerData != null && manufacturerData.isNotEmpty()) {
                val (discoveredUserId, discoveredName) = decodePayload(manufacturerData)
                val uid = currentUserId

                if (discoveredUserId.isNotBlank() && discoveredUserId != uid) {
                    val distance = calculateDistance(result.rssi)
                    val formattedDist = String.format(java.util.Locale.US, "%.1f", distance)
                    Log.i(TAG, "BLE_SCAN: Found beacon from $discoveredUserId")
                    Log.i(TAG, "BLE_SCAN: Decoded colleague: $discoveredName (distance: ${formattedDist}m)")
                    Log.d(TAG, "Found colleague: userId=$discoveredUserId, name=$discoveredName, RSSI=${result.rssi}")

                    val colleague = Colleague(
                        id = discoveredUserId,
                        userId = discoveredUserId,
                        name = discoveredName,
                        workplaceName = "Workplace",
                        currentStatus = ColleagueStatus.AT_WORKPLACE,
                        distanceMeters = distance.toFloat(),
                        rssi = result.rssi,
                        dataSource = "BLE",
                        detectionMethod = "BLE",
                        confidence = 0.95f,
                        lastSeen = System.currentTimeMillis(),
                        isTrustedContact = true
                    )

                    discoveredColleagues[discoveredUserId] = colleague

                    // Auto-add to trusted contacts on first discovery
                    autoAddToTrustedContacts(discoveredUserId, discoveredName)

                    // Dispatch to callbacks
                    customOnFoundCallback?.invoke(colleague)
                    onColleagueDiscovered(colleague)
                    onColleaguesUpdate(discoveredColleagues.values.toList())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing scan result: ${e.message}")
        }
    }

    fun stopScanning() {
        Log.d(TAG, "Stopping BLE scanning")
        isScanning = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            if (scanner != null && scanCallback != null) {
                scanner?.stopScan(scanCallback)
                Log.d(TAG, "BLE scanning stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }

    private fun decodePayload(data: ByteArray): Pair<String, String> {
        return try {
            var offset = 0
            if (data.size < 2) return Pair("", "")

            val userIdLength = (data[offset++].toInt() and 0xFF)
            if (offset + userIdLength > data.size) return Pair("", "")
            val userIdBytes = data.copyOfRange(offset, offset + userIdLength)
            offset += userIdLength

            if (offset >= data.size) return Pair(String(userIdBytes, Charsets.UTF_8), "Colleague")

            val userNameLength = (data[offset++].toInt() and 0xFF)
            val actualNameLen = minOf(userNameLength, data.size - offset)
            val userNameBytes = data.copyOfRange(offset, offset + actualNameLen)

            val userId = String(userIdBytes, Charsets.UTF_8).trim()
            val userName = String(userNameBytes, Charsets.UTF_8).trim()

            Log.d(TAG, "Decoded: userId=$userId, userName=$userName")
            Pair(userId, userName)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding payload: ${e.message}")
            Pair("", "")
        }
    }

    private fun calculateDistance(rssi: Int): Double {
        // Path loss distance formula: 10^((txPower - RSSI) / (10 * n))
        val txPower = -59.0 // dBm typical for BLE at 1 meter
        val n = 2.0 // Path loss exponent in free space / office
        val distance = 10.0.pow((txPower - rssi) / (10.0 * n))
        val formatted = String.format(java.util.Locale.US, "%.2f", distance)
        Log.d(TAG, "RSSI=$rssi dBm -> Distance=${formatted}m")
        return distance
    }

    private fun autoAddToTrustedContacts(userId: String, name: String) {
        val uid = currentUserId
        if (uid.isBlank() || userId.isBlank()) return

        scope.launch {
            try {
                firestoreManager.db.collection("trustedContacts")
                    .document(uid)
                    .collection("contacts")
                    .document(userId)
                    .set(
                        mapOf(
                            "userId" to userId,
                            "name" to name,
                            "addedAt" to FieldValue.serverTimestamp(),
                            "source" to "BLE_AUTO_DISCOVERY"
                        ),
                        SetOptions.merge()
                    )
                Log.d(TAG, "Auto-added to trusted contacts: $userId ($name)")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding trusted contact: ${e.message}")
            }
        }
    }

    fun getDiscoveredColleagues(): List<Colleague> {
        return discoveredColleagues.values.toList()
    }
}
