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
import java.util.UUID

/**
 * Manages Bluetooth Low Energy (BLE) beacon advertising when in WORKING Mode.
 * Broadcasts minimal presence metadata (userId, userName, status) to nearby coworkers.
 */
class WorkplaceBeaconManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothAdvertiser: BluetoothLeAdvertiser? = null

    companion object {
        private const val TAG = "WorkplaceBeacon"
        val BEACON_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
        val BEACON_PARCEL_UUID: ParcelUuid = ParcelUuid(BEACON_SERVICE_UUID)
    }

    private var isAdvertising = false

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

    fun startWorkplaceBeacon(userId: String, userName: String, status: String) {
        if (isAdvertising) {
            Log.d(TAG, "Workplace beacon is already active")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled or unavailable on this device")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w(TAG, "BLUETOOTH_ADVERTISE permission not granted")
                return
            }
        }

        bluetoothAdvertiser = adapter.bluetoothLeAdvertiser
        if (bluetoothAdvertiser == null) {
            Log.w(TAG, "BLE advertising is not supported on this hardware chipset")
            return
        }

        try {
            val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0) // Never timeout while in Working Mode
                .build()

            val sanitizedName = userName.take(16).ifBlank { "Coworker" }
            val beaconPayload = "$userId|$sanitizedName|$status".toByteArray(Charsets.UTF_8)

            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(BEACON_PARCEL_UUID)
                .addServiceData(BEACON_PARCEL_UUID, beaconPayload)
                .build()

            bluetoothAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            Log.d(TAG, "Initiated workplace beacon broadcast for $sanitizedName ($status)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch workplace BLE beacon", e)
        }
    }

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
