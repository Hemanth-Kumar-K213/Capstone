package com.example.testble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.UUID

/**
 * Manages BLE scanning and heart-rate reading from boAt Storm Plus
 * Tests whether the watch exposes standard Heart Rate Service (0x180D)
 */
class BleHeartRateManager(context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    companion object {
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // State flows
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _heartRateBpm = MutableStateFlow<Int?>(null)
    val heartRateBpm: StateFlow<Int?> = _heartRateBpm

    private val _lastReadTime = MutableStateFlow<Long?>(null)
    val lastReadTime: StateFlow<Long?> = _lastReadTime

    private val _serviceStatus = MutableStateFlow("")
    val serviceStatus: StateFlow<String> = _serviceStatus

    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanTimeout: Runnable? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return

        _isScanning.value = true
        _scanResults.value = emptyList()
        _connectionStatus.value = "Scanning for Storm Plus..."
        _serviceStatus.value = ""

        Timber.d("🔍 Starting BLE scan")

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(emptyList(), scanSettings, scanCallback)

        // Auto-stop after 15 seconds
        scanTimeout = Runnable {
            stopScan()
        }
        handler.postDelayed(scanTimeout!!, 15000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return

        scanner?.stopScan(scanCallback)
        _isScanning.value = false
        _connectionStatus.value = "Scan stopped"

        scanTimeout?.let { handler.removeCallbacks(it) }
        Timber.d("⏹️ Scan stopped")
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String, name: String) {
        Timber.d("🔗 Connecting to: $name ($address)")
        _connectionStatus.value = "Connecting to $name..."
        _serviceStatus.value = ""

        val device = bluetoothAdapter?.getRemoteDevice(address) ?: run {
            _connectionStatus.value = "❌ Device not found"
            return
        }

        gatt = device.connectGatt(null, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionStatus.value = "Disconnected"
        _heartRateBpm.value = null
        _serviceStatus.value = ""
        Timber.d("🔌 Disconnected")
    }

    // ============ CALLBACKS ============

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: "Unknown"
            val address = result.device.address
            Timber.d("📱 Found: $name ($address) - RSSI: ${result.rssi}")

            val currentResults = _scanResults.value.toMutableList()
            if (!currentResults.any { it.device.address == address }) {
                currentResults.add(result)
                _scanResults.value = currentResults
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("❌ Scan failed: $errorCode")
            _connectionStatus.value = "Scan failed (Error: $errorCode)"
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Timber.d("📡 Connection state: status=$status, state=$newState")

            when (newState) {
                android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                    _connectionStatus.value = "✅ Connected! Discovering services..."
                    gatt.discoverServices()
                }
                android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionStatus.value = "❌ Disconnected"
                    _heartRateBpm.value = null
                }
                else -> {
                    _connectionStatus.value = "⚠️ Unknown state: $newState"
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Timber.d("🔎 Services discovered: status=$status")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                _serviceStatus.value = "❌ Service discovery failed"
                _connectionStatus.value = "Service discovery failed"
                return
            }

            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
            if (hrService == null) {
                _serviceStatus.value = "❌ 0x180D Heart Rate Service NOT FOUND!\n\nThe watch may use a proprietary protocol."
                _connectionStatus.value = "❌ 0x180D not found (proprietary protocol?)"
                Timber.e("❌ 0x180D service not found!")
                
                // Log all available services for debugging
                logAllServices(gatt)
                return
            }

            Timber.d("✅ 0x180D Service FOUND!")
            _serviceStatus.value = "✅ 0x180D Heart Rate Service FOUND!"
            _connectionStatus.value = "✅ Enabling notifications..."

            val hrCharacteristic = hrService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (hrCharacteristic == null) {
                _serviceStatus.value = "❌ 0x2A37 characteristic NOT FOUND"
                Timber.e("❌ 0x2A37 not found!")
                return
            }

            Timber.d("✅ 0x2A37 Characteristic FOUND!")
            _serviceStatus.value = "✅ 0x180D Service FOUND!\n✅ 0x2A37 Characteristic FOUND!\n\nEnabling notifications..."

            // Enable notifications
            gatt.setCharacteristicNotification(hrCharacteristic, true)

            val descriptor = hrCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                Timber.d("✅ Notifications enabled")
            }
        }

        // For Android API < 33 compatibility
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: byteArrayOf()
            handleHeartRatePacket(characteristic, data)
        }

        // For Android API 33+
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
                _heartRateBpm.value = bpm
                _lastReadTime.value = System.currentTimeMillis()
                _serviceStatus.value = "✅ 0x180D Service FOUND!\n✅ 0x2A37 Characteristic FOUND!\n✅ LIVE HR DATA RECEIVED: $bpm BPM"
                _connectionStatus.value = "✅ READING: $bpm BPM"
                Timber.d("❤️ HR: $bpm BPM")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("✅ Descriptor write successful - waiting for notifications...")
            } else {
                Timber.e("❌ Descriptor write failed: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun logAllServices(gatt: BluetoothGatt) {
        val sb = StringBuilder("=== ALL SERVICES FOUND ===\n")
        gatt.services.forEach { service ->
            sb.append("Service: ${service.uuid}\n")
            service.characteristics.forEach { char ->
                sb.append("  └─ Char: ${char.uuid}\n")
            }
        }
        val logOutput = sb.toString()
        Timber.d(logOutput)
        _serviceStatus.value += "\n\nAvailable Services:\n" + gatt.services.joinToString("\n") { it.uuid.toString().substring(0, 8) }
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
