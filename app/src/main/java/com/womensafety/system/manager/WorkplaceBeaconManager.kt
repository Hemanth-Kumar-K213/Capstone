package com.womensafety.system.manager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Requirement 2A: Real BLE Broadcasting Manager — Mobile only (no watch).
 *
 * Broadcasts Service UUID 0000180A-0000-1000-8000-00805F9B34FB with
 * payload: { userId, userName, status: "WORKING", timestamp }
 * Runs continuously while WORKING mode is active.
 */
class WorkplaceBeaconManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var bluetoothAdvertiser: BluetoothLeAdvertiser? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "WorkplaceBeacon"
        val BEACON_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
        val BEACON_PARCEL_UUID: ParcelUuid = ParcelUuid(BEACON_SERVICE_UUID)
    }

    private var isAdvertising = false

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            Log.d(TAG, "Workplace BLE beacon advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.w(TAG, "Workplace BLE beacon advertising failed with errorCode: $errorCode")
        }
    }

    /**
     * Starts BLE advertising for coworker discovery.
     * Returns true if advertising initiated, false if Bluetooth disabled or unsupported.
     */
    fun startWorkplaceBeacon(
        userId: String,
        userName: String,
        status: String = "WORKING",
        onBluetoothDisabled: () -> Unit = {}
    ): Boolean {
        if (isAdvertising) {
            Log.d(TAG, "Workplace beacon is already active")
            return true
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth disabled. Prompting user to enable.")
            onBluetoothDisabled()
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w(TAG, "BLUETOOTH_ADVERTISE permission not granted")
                return false
            }
        }

        bluetoothAdvertiser = adapter.bluetoothLeAdvertiser
        if (bluetoothAdvertiser == null) {
            Log.w(TAG, "BLE advertising is not supported on this device hardware")
            return false
        }

        try {
            val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0) // Run while WORKING mode is active
                .build()

            val sanitizedName = userName.take(16).ifBlank { "Colleague" }
            val now = System.currentTimeMillis()

            // Construct payload JSON or compact encoding
            val jsonPayload = JSONObject().apply {
                put("userId", userId)
                put("userName", sanitizedName)
                put("status", status)
                put("timestamp", now)
            }.toString()

            val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)

            // Setup AdvertiseData with Service UUID
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(BEACON_PARCEL_UUID)
                .build()

            // Put Service Data into ScanResponse to prevent 31-byte legacy advertisement overflow
            val scanResponseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceData(BEACON_PARCEL_UUID, payloadBytes)
                .build()

            bluetoothAdvertiser?.startAdvertising(
                advertiseSettings,
                advertiseData,
                scanResponseData,
                advertiseCallback
            )

            // Also mirror active status to Firestore for geofence/remote coordination
            scope.launch {
                try {
                    val statusMap = mapOf(
                        "userId" to userId,
                        "name" to sanitizedName,
                        "status" to status,
                        "lastSeen" to now,
                        "source" to "BLE_BEACON"
                    )
                    firestore.collection("colleagues")
                        .document(userId)
                        .set(statusMap)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to mirror status to Firestore", e)
                }
            }

            Log.d(TAG, "Initiated BLE workplace beacon advertising for $sanitizedName ($userId)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting workplace BLE beacon", e)
            return false
        }
    }

    /**
     * Stops BLE advertising when exiting WORKING mode.
     */
    fun stopWorkplaceBeacon() {
        if (!isAdvertising) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) return
            }
            bluetoothAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d(TAG, "Stopped workplace BLE beacon advertising")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping workplace beacon", e)
        }
    }
}
