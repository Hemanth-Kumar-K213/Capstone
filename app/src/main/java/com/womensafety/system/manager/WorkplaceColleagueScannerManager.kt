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
 * Manages Bluetooth Low Energy (BLE) scanning for nearby coworker beacons in WORKING Mode.
 */
class WorkplaceColleagueScannerManager(
    private val context: Context,
    private val firestoreManager: FirestoreManager,
    private val authManager: FirebaseAuthManager,
    private val onColleagueDiscovered: (Colleague) -> Unit = {},
    private val onColleaguesUpdate: (List<Colleague>) -> Unit = {}
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothScanner = bluetoothAdapter?.bluetoothLeScanner

    companion object {
        private const val TAG = "ColleagueScanner"
        private const val MAX_DISTANCE_METERS = 150f
    }

    private val discoveredColleagues = ConcurrentHashMap<String, Colleague>()
    private var isScanning = false
    private val scope = CoroutineScope(Dispatchers.IO)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanRecord = result.scanRecord ?: return
            val serviceData = scanRecord.getServiceData(WorkplaceBeaconManager.BEACON_PARCEL_UUID) ?: return

            try {
                val beaconString = String(serviceData, Charsets.UTF_8)
                val parts = beaconString.split("|")
                if (parts.size < 3) return

                val colleagueId = parts[0].trim()
                val colleagueName = parts[1].trim()
                val rawStatus = parts[2].trim()

                val currentUserId = authManager.currentUserId ?: ""
                if (colleagueId.isBlank() || colleagueId == currentUserId) {
                    return // Ignore own device
                }

                val rssi = result.rssi
                val distanceMeters = calculateDistanceFromSignal(rssi)
                if (distanceMeters > MAX_DISTANCE_METERS) {
                    return // Out of workplace BLE range
                }

                val statusEnum = when (rawStatus.uppercase()) {
                    "LEAVING" -> ColleagueStatus.LEAVING
                    "LEFT" -> ColleagueStatus.LEFT
                    "ON_BREAK" -> ColleagueStatus.ON_BREAK
                    "AWAY_FROM_DESK" -> ColleagueStatus.AWAY_FROM_DESK
                    else -> ColleagueStatus.AT_WORKPLACE
                }

                val colleague = Colleague(
                    id = colleagueId,
                    userId = colleagueId,
                    name = colleagueName,
                    currentStatus = statusEnum,
                    distanceMeters = distanceMeters,
                    rssi = rssi,
                    dataSource = "BLE",
                    lastSeen = System.currentTimeMillis()
                )

                discoveredColleagues[colleagueId] = colleague
                onColleagueDiscovered(colleague)
                onColleaguesUpdate(discoveredColleagues.values.toList())
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding colleague beacon payload", e)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.w(TAG, "Workplace colleague scan failed: $errorCode")
        }
    }

    fun startScanning() {
        if (isScanning) return

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled or unavailable")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            if (!hasScan) {
                Log.w(TAG, "BLUETOOTH_SCAN permission not granted")
                return
            }
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "bluetoothLeScanner is null")
            return
        }

        try {
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(WorkplaceBeaconManager.BEACON_PARCEL_UUID)
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            Log.d(TAG, "Started workplace colleague BLE scanner")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting colleague BLE scan", e)
        }
    }

    fun stopScanning() {
        if (!isScanning) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
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

    private fun calculateDistanceFromSignal(rssi: Int): Float {
        val txPower = -59.0
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            ratio.pow(10.0).toFloat()
        } else {
            (0.89976 * ratio.pow(7.7095) + 0.111).toFloat()
        }
    }
}
