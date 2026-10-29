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
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Requirement 1A: Complete BLE Rewrite — WorkplaceBeaconManager.
 *
 * Uses manufacturer data (0xFFFF) encoding for UID (compact 21-byte Base64 decode for 28-char UIDs)
 * and (0xFFFE) scan response for colleague name, strictly keeping packet <= 31 bytes.
 * Runs non-connectable broadcast during WORKING mode.
 */
class WorkplaceBeaconManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    var onAdvertisingStatusChanged: ((Boolean, String?) -> Unit)? = null

    companion object {
        private const val BEACON_UUID_STRING = "0000180A-0000-1000-8000-00805F9B34FB"
        val BEACON_UUID: UUID = UUID.fromString(BEACON_UUID_STRING)
        const val MANUFACTURER_ID = 0xFFFF
        const val MANUFACTURER_ID_NAME = 0xFFFE
        const val MAGIC_BYTE: Byte = 0x57 // 'W' for Workplace SafeHer
        const val TYPE_UID_PACKED_28: Byte = 0x01
        const val TYPE_UID_RAW_UTF8: Byte = 0x02
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

            val uidPayload = encodeUidPayload(userId)

            // Primary advertise data with UID: Strictly <= 31-byte BLE limit
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(
                    MANUFACTURER_ID, // 0xFFFF reserved for testing / custom beacons
                    uidPayload
                )
                .build()

            // Scan response with Colleague Name: Separate 31-byte packet
            val namePayload = encodeNamePayload(userName)
            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(
                    MANUFACTURER_ID_NAME, // 0xFFFE
                    namePayload
                )
                .build()

            // Start advertising with Scan Response
            advertiser?.startAdvertising(settings, advertiseData, scanResponse, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.i(TAG, "BLE_BEACON: Started advertising (callback: SUCCESS)")
                    Log.d(TAG, "SUCCESS: BLE advertising started")
                    isAdvertising = true
                    onAdvertisingStatusChanged?.invoke(true, null)
                }

                override fun onStartFailure(errorCode: Int) {
                    isAdvertising = false
                    val errorReason = when (errorCode) {
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> "Payload too large (>31 bytes)"
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers active"
                        ADVERTISE_FAILED_ALREADY_STARTED -> {
                            isAdvertising = true
                            "Already advertising"
                        }
                        ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal hardware/BT error"
                        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Hardware BLE advertising unsupported"
                        else -> "Error code: $errorCode"
                    }
                    Log.e(TAG, "ERROR: Failed to start advertising - errorCode=$errorCode ($errorReason)")
                    onAdvertisingStatusChanged?.invoke(isAdvertising, errorReason)
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
                onAdvertisingStatusChanged?.invoke(false, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising: ${e.message}")
        }
    }

    private fun encodeUidPayload(userId: String): ByteArray {
        val cleanUid = userId.trim()
        Log.i(TAG, "BLE_BEACON: Advertising as userId=$cleanUid (length=${cleanUid.length})")

        // If 28-char Firebase UID, pack into 21 bytes using Base64 decode
        if (cleanUid.length == 28) {
            try {
                val decodedBytes = Base64.decode(cleanUid, Base64.URL_SAFE or Base64.NO_WRAP)
                if (decodedBytes.size == 21) {
                    val payload = ByteArray(23)
                    payload[0] = MAGIC_BYTE
                    payload[1] = TYPE_UID_PACKED_28
                    System.arraycopy(decodedBytes, 0, payload, 2, 21)
                    Log.d(TAG, "Encoded 28-char UID into 21 packed bytes (total payload 23 bytes)")
                    return payload
                }
            } catch (e: Exception) {
                Log.w(TAG, "Base64 decode failed for 28-char UID: ${e.message}")
            }
        }

        // Fallback for custom or test UIDs (take up to 18 bytes so total packet stays <= 31 bytes)
        val uidBytes = cleanUid.take(18).toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + uidBytes.size)
        payload[0] = MAGIC_BYTE
        payload[1] = TYPE_UID_RAW_UTF8
        System.arraycopy(uidBytes, 0, payload, 2, uidBytes.size)
        Log.d(TAG, "Encoded raw UID into ${payload.size} bytes")
        return payload
    }

    private fun encodeNamePayload(userName: String): ByteArray {
        val cleanName = userName.trim().ifBlank { "Colleague" }.take(18)
        val nameBytes = cleanName.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + nameBytes.size)
        payload[0] = MAGIC_BYTE
        System.arraycopy(nameBytes, 0, payload, 1, nameBytes.size)
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
