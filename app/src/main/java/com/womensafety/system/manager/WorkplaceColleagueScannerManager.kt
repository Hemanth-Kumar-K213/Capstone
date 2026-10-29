package com.womensafety.system.manager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
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
 * Scans for manufacturer data (0xFFFF) strictly matching our SafeHer MAGIC_BYTE (0x57),
 * ignores foreign devices (laptops, TVs, etc.), decodes full 28-char Firebase UIDs,
 * and reads scan responses (0xFFFE) for colleague names.
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
        const val MANUFACTURER_ID = 0xFFFF
        const val MANUFACTURER_ID_NAME = 0xFFFE
        const val MAGIC_BYTE: Byte = 0x57 // 'W' for Workplace SafeHer
        const val TYPE_UID_PACKED_28: Byte = 0x01
        const val TYPE_UID_RAW_UTF8: Byte = 0x02
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

            // 1. Strictly read Manufacturer Data for 0xFFFF
            val manufacturerData = record.getManufacturerSpecificData(MANUFACTURER_ID)
            if (manufacturerData == null || manufacturerData.size < 3 || manufacturerData[0] != MAGIC_BYTE) {
                // Strictly ignore laptops, smart TVs, and non-SafeHer BLE devices
                return
            }

            val type = manufacturerData[1]
            val discoveredUserId = when (type) {
                TYPE_UID_PACKED_28 -> {
                    if (manufacturerData.size >= 23) {
                        val rawBytes = manufacturerData.copyOfRange(2, 23)
                        Base64.encodeToString(
                            rawBytes,
                            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                        ).trim()
                    } else ""
                }
                TYPE_UID_RAW_UTF8 -> {
                    String(manufacturerData.copyOfRange(2, manufacturerData.size), Charsets.UTF_8).trim()
                }
                else -> ""
            }

            if (discoveredUserId.isBlank()) return

            val uid = currentUserId
            if (discoveredUserId == uid) {
                // Don't detect ourselves
                return
            }

            // 2. Read Colleague Name from 0xFFFE if available in ScanResponse
            var discoveredName = "Colleague"
            val nameData = record.getManufacturerSpecificData(MANUFACTURER_ID_NAME)
            if (nameData != null && nameData.size > 1 && nameData[0] == MAGIC_BYTE) {
                val parsedName = String(nameData.copyOfRange(1, nameData.size), Charsets.UTF_8).trim()
                if (parsedName.isNotBlank()) {
                    discoveredName = parsedName
                }
            } else {
                val existing = discoveredColleagues[discoveredUserId]
                if (existing != null && existing.name.isNotBlank() && !existing.name.startsWith("Colleague (")) {
                    discoveredName = existing.name
                } else {
                    discoveredName = "Colleague (${discoveredUserId.take(4)})"
                }
            }

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

            // Auto-add to trusted contacts on first discovery (safe catch)
            autoAddToTrustedContacts(discoveredUserId, discoveredName)

            // Dispatch to callbacks
            customOnFoundCallback?.invoke(colleague)
            onColleagueDiscovered(colleague)
            onColleaguesUpdate(discoveredColleagues.values.toList())

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
