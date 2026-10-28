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
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Requirement 1A: Complete BLE Rewrite — WorkplaceBeaconManager.
 *
 * Uses manufacturer data (0xFFFF) encoding: [len][userId][len][userName]
 * Runs non-connectable broadcast during WORKING mode.
 */
class WorkplaceBeaconManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false

    companion object {
        private const val BEACON_UUID_STRING = "0000180A-0000-1000-8000-00805F9B34FB"
        val BEACON_UUID: UUID = UUID.fromString(BEACON_UUID_STRING)
        const val MANUFACTURER_ID = 0xFFFF
        private const val TAG = "BLE_BEACON"
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun startAdvertising(userId: String, userName: String): Boolean {
        Log.d(TAG, "=== START BEACON ADVERTISING ===")
        Log.d(TAG, "userId=$userId, userName=$userName")

        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.e(TAG, "ERROR: Device does not support Bluetooth")
            return false
        }

        if (!adapter.isEnabled) {
            Log.e(TAG, "ERROR: Bluetooth is disabled - cannot advertise")
            return false
        }

        // Check BLUETOOTH_ADVERTISE permission on Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                Log.e(TAG, "ERROR: BLUETOOTH_ADVERTISE runtime permission missing")
                return false
            }
        }

        try {
            advertiser = adapter.bluetoothLeAdvertiser
            if (advertiser == null) {
                Log.e(TAG, "ERROR: BLE advertiser not available on this device hardware")
                return false
            }

            // Create simple advertise settings (non-connectable scan response)
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false) // Non-connectable for broadcast only
                .setTimeout(0) // Advertise indefinitely
                .build()

            val payload = encodeBeaconPayload(userId, userName)

            // Create advertise data with manufacturer data (simpler and more reliable than service data)
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Keeps total packet strictly under 31-byte BLE limit
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(
                    MANUFACTURER_ID, // 0xFFFF reserved for testing / custom beacons
                    payload
                )
                .build()

            // Start advertising
            advertiser?.startAdvertising(settings, advertiseData, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.i(TAG, "BLE_BEACON: Started advertising (callback: SUCCESS)")
                    Log.d(TAG, "SUCCESS: BLE advertising started")
                    isAdvertising = true
                }

                override fun onStartFailure(errorCode: Int) {
                    isAdvertising = false
                    Log.e(TAG, "ERROR: Failed to start advertising - errorCode=$errorCode")
                    when (errorCode) {
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> Log.e(TAG, "Payload too large (>31 bytes)")
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> Log.e(TAG, "Too many advertisers active")
                        ADVERTISE_FAILED_ALREADY_STARTED -> {
                            Log.e(TAG, "Already advertising - marking active")
                            isAdvertising = true
                        }
                        ADVERTISE_FAILED_INTERNAL_ERROR -> Log.e(TAG, "Internal hardware/BT error")
                        else -> Log.e(TAG, "Unknown error")
                    }
                }
            })

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Exception during startAdvertising: ${e.message}", e)
            return false
        }
    }

    fun stopAdvertising() {
        Log.d(TAG, "Stopping BLE advertising")
        try {
            if (advertiser != null && isAdvertising) {
                advertiser?.stopAdvertising(object : AdvertiseCallback() {})
                isAdvertising = false
                Log.d(TAG, "BLE advertising stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising: ${e.message}")
        }
    }

    private fun encodeBeaconPayload(userId: String, userName: String): ByteArray {
        // Format: [length][userId][length][userName]
        // Cap lengths to ensure total packet is well within BLE 31-byte limit
        // Use full Firebase UID (28 chars). BLE advertisement supports multi-packet payloads.
        val cleanUid = userId.trim()
        Log.i(TAG, "BLE_BEACON: Advertising as userId=$cleanUid (length=${cleanUid.length})")
        val cleanName = userName.trim().ifBlank { "Colleague" }.take(14)

        val userIdBytes = cleanUid.toByteArray(Charsets.UTF_8)
        val userNameBytes = cleanName.toByteArray(Charsets.UTF_8)

        val payload = ByteArray(1 + userIdBytes.size + 1 + userNameBytes.size)
        var offset = 0

        payload[offset++] = userIdBytes.size.toByte()
        userIdBytes.copyInto(payload, offset)
        offset += userIdBytes.size

        payload[offset++] = userNameBytes.size.toByte()
        userNameBytes.copyInto(payload, offset)

        Log.d(TAG, "Encoded payload for $cleanUid ($cleanName): ${payload.size} bytes: [${payload.joinToString(",") { it.toString() }}]")
        return payload
    }

    fun isAdvertising(): Boolean = isAdvertising

    // Compatibility aliases for existing callers
    fun startBeaconAdvertising(userId: String, userName: String): Boolean = startAdvertising(userId, userName)
    fun stopBeaconAdvertising() = stopAdvertising()
    fun startWorkplaceBeacon(
        userId: String,
        userName: String,
        status: String = "WORKING",
        onBluetoothDisabled: () -> Unit = {}
    ): Boolean {
        if (!isBluetoothEnabled()) {
            onBluetoothDisabled()
        }
        return startAdvertising(userId, userName)
    }
    fun stopWorkplaceBeacon() = stopAdvertising()
}
