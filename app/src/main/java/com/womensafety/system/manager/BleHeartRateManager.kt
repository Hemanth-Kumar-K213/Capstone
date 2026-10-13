package com.womensafety.system.manager

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Direct Bluetooth Low Energy (BLE) Heart Rate Manager.
 * Scans, pairs, and listens for live heart-rate telemetry from smartwatches
 * and fitness bands exposing standard Bluetooth SIG Heart Rate Service (0x180D).
 */
object BleHeartRateManager {

    private const val TAG = "BleHeartRateManager"

    // Standard BLE Heart Rate Service & Characteristic UUIDs
    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanTimeout: Runnable? = null

    // State flows
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _heartRateBpm = MutableStateFlow<Int?>(null)
    val heartRateBpm: StateFlow<Int?> = _heartRateBpm.asStateFlow()

    private val _lastReadTime = MutableStateFlow<Long?>(null)
    val lastReadTime: StateFlow<Long?> = _lastReadTime.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _serviceStatus = MutableStateFlow("")
    val serviceStatus: StateFlow<String> = _serviceStatus.asStateFlow()

    fun init(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _connectionStatus.value = "Bluetooth not available or disabled"
            Log.e(TAG, "Bluetooth scanner not available")
            return
        }

        _scanResults.value = emptyList()
        _isScanning.value = true
        _connectionStatus.value = "Scanning for smartwatches..."
        _serviceStatus.value = ""

        Log.d(TAG, "Starting BLE smartwatch scan")

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(emptyList(), scanSettings, scanCallback)

            // Auto-stop after 15 seconds
            scanTimeout?.let { handler.removeCallbacks(it) }
            scanTimeout = Runnable { stopScan() }
            handler.postDelayed(scanTimeout!!, 15000)
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
            _isScanning.value = false
            _connectionStatus.value = "Scan error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return

        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan error", e)
        }

        _isScanning.value = false
        _connectionStatus.value = "Scan stopped"
        scanTimeout?.let { handler.removeCallbacks(it) }
        Log.d(TAG, "BLE scan stopped")
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(context: Context, address: String, name: String) {
        Log.d(TAG, "Connecting to watch: $name ($address)")
        _connectionStatus.value = "Connecting to $name..."
        _serviceStatus.value = ""

        val device = bluetoothAdapter?.getRemoteDevice(address) ?: run {
            _connectionStatus.value = "Device not found"
            return
        }

        stopScan()
        disconnect()

        gatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "disconnect error", e)
        }
        gatt = null
        _connectionStatus.value = "Disconnected"
        _heartRateBpm.value = null
        _serviceStatus.value = ""
        Log.d(TAG, "BLE Disconnected")
    }

    // Callbacks
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: "Smartwatch Device"
            val address = result.device.address
            Log.d(TAG, "Found BLE Device: $name ($address) RSSI: ${result.rssi}")

            val currentResults = _scanResults.value.toMutableList()
            if (!currentResults.any { it.device.address == address }) {
                currentResults.add(result)
                _scanResults.value = currentResults
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed: $errorCode")
            _connectionStatus.value = "Scan failed (Error: $errorCode)"
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "GATT connection state: status=$status, state=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionStatus.value = "Connected! Discovering services..."
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionStatus.value = "Disconnected"
                    _heartRateBpm.value = null
                }
                else -> {
                    _connectionStatus.value = "State: $newState"
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "GATT Services discovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _serviceStatus.value = "Service discovery failed"
                _connectionStatus.value = "Service discovery failed"
                return
            }

            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
            if (hrService == null) {
                _serviceStatus.value = "0x180D Heart Rate Service not exposed. The watch may sync via Health Connect / Companion App."
                _connectionStatus.value = "Connected (HR service not exposed)"
                Log.w(TAG, "0x180D Heart Rate Service not found")
                return
            }

            val hrCharacteristic = hrService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (hrCharacteristic == null) {
                _serviceStatus.value = "0x2A37 measurement characteristic not found"
                Log.w(TAG, "0x2A37 characteristic not found")
                return
            }

            _serviceStatus.value = "0x180D Service & 0x2A37 Characteristic Active"
            _connectionStatus.value = "Subscribing to live telemetry..."

            gatt.setCharacteristicNotification(hrCharacteristic, true)

            val descriptor = hrCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                _connectionStatus.value = "Active: Receiving live BPM"
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: byteArrayOf()
            handleHeartRatePacket(characteristic, data)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleHeartRatePacket(characteristic, value)
        }

        private fun handleHeartRatePacket(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                val bpm = parseHeartRateValue(value)
                if (bpm > 0) {
                    _heartRateBpm.value = bpm
                    _lastReadTime.value = System.currentTimeMillis()
                    _serviceStatus.value = "Live Smartwatch Heart Rate: $bpm BPM"
                    _connectionStatus.value = "Reading: $bpm BPM"
                    Log.d(TAG, "BLE Heart Rate: $bpm BPM")
                }
            }
        }
    }

    fun parseHeartRateValue(value: ByteArray): Int {
        return if (value.isNotEmpty()) {
            val flags = value[0].toInt()
            val isUint16 = (flags and 0x01) != 0

            if (isUint16 && value.size >= 3) {
                ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
            } else if (value.size >= 2) {
                value[1].toInt() and 0xFF
            } else {
                0
            }
        } else {
            0
        }
    }
}
