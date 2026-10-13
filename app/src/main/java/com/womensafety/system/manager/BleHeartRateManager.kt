package com.womensafety.system.manager

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.womensafety.system.data.health.model.BleConnectionState
import com.womensafety.system.data.health.model.BleDiscoveredService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Direct Bluetooth Low Energy (BLE) Heart Rate Manager.
 * Scans, pairs, and listens for live heart-rate telemetry from smartwatches
 * and fitness bands exposing standard Bluetooth SIG Heart Rate Service (0x180D).
 * Provides truthful, diagnosable reporting of discovered GATT services.
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

    private var currentDeviceName: String = "Smartwatch"
    private var currentDeviceAddress: String = ""

    // State flows
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _detailedState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val detailedState: StateFlow<BleConnectionState> = _detailedState.asStateFlow()

    private val _heartRateBpm = MutableStateFlow<Int?>(null)
    val heartRateBpm: StateFlow<Int?> = _heartRateBpm.asStateFlow()

    private val _lastReadTime = MutableStateFlow<Long?>(null)
    val lastReadTime: StateFlow<Long?> = _lastReadTime.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _serviceStatus = MutableStateFlow("")
    val serviceStatus: StateFlow<String> = _serviceStatus.asStateFlow()

    private val _discoveredServices = MutableStateFlow<List<BleDiscoveredService>>(emptyList())
    val discoveredServices: StateFlow<List<BleDiscoveredService>> = _discoveredServices.asStateFlow()

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
            _detailedState.value = BleConnectionState.Error("Bluetooth not available or disabled")
            Log.e(TAG, "Bluetooth scanner not available")
            return
        }

        _scanResults.value = emptyList()
        _isScanning.value = true
        _connectionStatus.value = "Scanning for smartwatches..."
        _detailedState.value = BleConnectionState.Scanning
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
            _detailedState.value = BleConnectionState.Error("Scan error: ${e.message}")
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
        if (_detailedState.value is BleConnectionState.Scanning) {
            _detailedState.value = BleConnectionState.Disconnected
        }
        _connectionStatus.value = if (gatt != null) _connectionStatus.value else "Scan stopped"
        scanTimeout?.let { handler.removeCallbacks(it) }
        Log.d(TAG, "BLE scan stopped")
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(context: Context, address: String, name: String) {
        Log.d(TAG, "Connecting to watch: $name ($address)")
        currentDeviceName = name
        currentDeviceAddress = address
        _connectionStatus.value = "Connecting to $name..."
        _detailedState.value = BleConnectionState.Connecting(name, address)
        _serviceStatus.value = ""
        _discoveredServices.value = emptyList()

        val device = bluetoothAdapter?.getRemoteDevice(address) ?: run {
            _connectionStatus.value = "Device not found"
            _detailedState.value = BleConnectionState.Error("Device not found")
            return
        }

        stopScan()
        disconnect()

        currentDeviceName = name
        currentDeviceAddress = address
        gatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        scanTimeout?.let { handler.removeCallbacks(it) }
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "disconnect error", e)
        }
        gatt = null
        _connectionStatus.value = "Disconnected"
        _detailedState.value = BleConnectionState.Disconnected
        _heartRateBpm.value = null
        _serviceStatus.value = ""
        _discoveredServices.value = emptyList()
        Log.d(TAG, "BLE Disconnected and GATT cleaned up")
    }

    // Callbacks
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: "Smartwatch Device"
            val address = result.device.address

            val currentResults = _scanResults.value.toMutableList()
            if (!currentResults.any { it.device.address == address }) {
                currentResults.add(result)
                _scanResults.value = currentResults
                Log.d(TAG, "Discovered BLE device: $name ($address), RSSI: ${result.rssi}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed: $errorCode")
            _connectionStatus.value = "Scan failed (Error code: $errorCode)"
            _detailedState.value = BleConnectionState.Error("Scan failed (Error code: $errorCode)")
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "GATT onConnectionStateChange: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionStatus.value = "Connected! Discovering services..."
                    _detailedState.value = BleConnectionState.ConnectedDiscovering(currentDeviceName, currentDeviceAddress)
                    _serviceStatus.value = "Discovering GATT services..."
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected (status=$status)")
                    _connectionStatus.value = if (status != BluetoothGatt.GATT_SUCCESS) {
                        "Disconnected (GATT status $status)"
                    } else {
                        "Disconnected"
                    }
                    _detailedState.value = BleConnectionState.Disconnected
                    _heartRateBpm.value = null
                    _serviceStatus.value = ""
                    try {
                        gatt.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing gatt on disconnect", e)
                    }
                    if (BleHeartRateManager.gatt == gatt) {
                        BleHeartRateManager.gatt = null
                    }
                }
                else -> {
                    _connectionStatus.value = "Connection state: $newState"
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "GATT onServicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _serviceStatus.value = "Service discovery failed (status: $status)"
                _connectionStatus.value = "Service discovery failed"
                _detailedState.value = BleConnectionState.Error("Service discovery failed (status: $status)")
                return
            }

            // Enumerate and catalog all discovered services
            val discoveredList = gatt.services.map { service ->
                val isHr = service.uuid == HEART_RATE_SERVICE_UUID
                val charList = service.characteristics.map { char ->
                    "${char.uuid} (${resolveCharacteristicName(char.uuid)})"
                }
                BleDiscoveredService(
                    uuid = service.uuid.toString(),
                    name = resolveServiceName(service.uuid),
                    isStandardHeartRateService = isHr,
                    characteristics = charList
                )
            }
            _discoveredServices.value = discoveredList

            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
            if (hrService == null) {
                val reason = "0x180D Heart Rate Service not exposed. This watch uses a proprietary sync protocol managed by its companion app, which can write records to Health Connect."
                _serviceStatus.value = reason
                _connectionStatus.value = "Connected (0x180D HR Service Not Found)"
                _detailedState.value = BleConnectionState.ConnectedUnsupported(
                    deviceName = currentDeviceName,
                    deviceAddress = currentDeviceAddress,
                    reason = reason
                )
                Log.w(TAG, "0x180D Heart Rate Service not found among ${discoveredList.size} services")
                return
            }

            val hrCharacteristic = hrService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (hrCharacteristic == null) {
                val reason = "0x2A37 Heart Rate Measurement characteristic not found under 0x180D service."
                _serviceStatus.value = reason
                _connectionStatus.value = "Connected (0x2A37 Characteristic Not Found)"
                _detailedState.value = BleConnectionState.ConnectedUnsupported(
                    deviceName = currentDeviceName,
                    deviceAddress = currentDeviceAddress,
                    reason = reason
                )
                Log.w(TAG, "0x2A37 characteristic not found")
                return
            }

            _serviceStatus.value = "0x180D Service & 0x2A37 Characteristic Found. Subscribing..."
            _connectionStatus.value = "Subscribing to notifications..."

            val notifySet = gatt.setCharacteristicNotification(hrCharacteristic, true)
            if (!notifySet) {
                Log.e(TAG, "setCharacteristicNotification returned false")
            }

            val descriptor = hrCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val res = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    if (res != BluetoothStatusCodes.SUCCESS) {
                        Log.e(TAG, "writeDescriptor returned code: $res")
                        _serviceStatus.value = "Descriptor write failed with status code $res"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    val res = gatt.writeDescriptor(descriptor)
                    if (!res) {
                        Log.e(TAG, "writeDescriptor returned false")
                        _serviceStatus.value = "Descriptor write failed"
                    }
                }
            } else {
                Log.w(TAG, "0x2902 CCCD descriptor not found on 0x2A37")
                _serviceStatus.value = "0x2902 CCCD descriptor not found"
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(TAG, "onDescriptorWrite: uuid=${descriptor.uuid}, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionStatus.value = "Connected: Subscribed (Waiting for pulse)"
                _serviceStatus.value = "Subscribed to 0x2A37. Waiting for smartwatch to transmit first measurement..."
                _detailedState.value = BleConnectionState.ConnectedSubscribedWaiting(
                    deviceName = currentDeviceName,
                    deviceAddress = currentDeviceAddress
                )
            } else {
                _connectionStatus.value = "Subscription failed (GATT status $status)"
                _serviceStatus.value = "Descriptor write failed with status $status"
                _detailedState.value = BleConnectionState.Error("Descriptor subscription failed (status $status)")
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
                    val now = System.currentTimeMillis()
                    _heartRateBpm.value = bpm
                    _lastReadTime.value = now
                    _serviceStatus.value = "Live Smartwatch Heart Rate: $bpm BPM"
                    _connectionStatus.value = "Streaming: $bpm BPM"
                    _detailedState.value = BleConnectionState.Streaming(
                        deviceName = currentDeviceName,
                        deviceAddress = currentDeviceAddress,
                        bpm = bpm,
                        lastPacketTimestamp = now
                    )
                    Log.d(TAG, "BLE Heart Rate telemetry received: $bpm BPM at $now")
                } else {
                    Log.w(TAG, "Rejected 0 or invalid BPM measurement from packet size ${value.size}")
                }
            }
        }
    }

    /**
     * Parses a Bluetooth SIG standard Heart Rate Measurement characteristic (0x2A37) payload.
     * Byte 0: Flags (Bit 0 determines UINT8 vs UINT16 format).
     * Rejects malformed or truncated packets by returning 0.
     * Rejects physiologically impossible readings outside [30..250] BPM by returning 0.
     */
    fun parseHeartRateValue(value: ByteArray): Int {
        if (value.isEmpty()) return 0
        val flags = value[0].toInt()
        val isUint16 = (flags and 0x01) != 0

        val rawBpm = if (isUint16) {
            if (value.size >= 3) {
                ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
            } else {
                0 // Malformed UINT16 packet (truncated)
            }
        } else {
            if (value.size >= 2) {
                value[1].toInt() and 0xFF
            } else {
                0 // Malformed UINT8 packet (truncated)
            }
        }

        // Validate physiological bounds
        return if (rawBpm in 30..250) rawBpm else 0
    }

    fun resolveServiceName(uuid: UUID): String {
        val str = uuid.toString().lowercase()
        return when {
            str.startsWith("0000180d") -> "Heart Rate Service (0x180D)"
            str.startsWith("00001800") -> "Generic Access (0x1800)"
            str.startsWith("00001801") -> "Generic Attribute (0x1801)"
            str.startsWith("0000180a") -> "Device Information (0x180A)"
            str.startsWith("0000180f") -> "Battery Service (0x180F)"
            str.startsWith("00001812") -> "Human Interface Device (0x1812)"
            str.startsWith("0000fee0") -> "Huami/Fit Service (0xFEE0)"
            str.startsWith("0000fee7") -> "Tencent/Fit Service (0xFEE7)"
            str.startsWith("6e400001") -> "Nordic UART Service (NUS)"
            else -> "Vendor/Proprietary Service"
        }
    }

    fun resolveCharacteristicName(uuid: UUID): String {
        val str = uuid.toString().lowercase()
        return when {
            str.startsWith("00002a37") -> "Heart Rate Measurement (0x2A37)"
            str.startsWith("00002a38") -> "Body Sensor Location (0x2A38)"
            str.startsWith("00002a00") -> "Device Name (0x2A00)"
            str.startsWith("00002a01") -> "Appearance (0x2A01)"
            str.startsWith("00002a19") -> "Battery Level (0x2A19)"
            str.startsWith("00002a24") -> "Model Number (0x2A24)"
            str.startsWith("00002a26") -> "Firmware Revision (0x2A26)"
            str.startsWith("00002a28") -> "Software Revision (0x2A28)"
            str.startsWith("00002a29") -> "Manufacturer Name (0x2A29)"
            else -> "Characteristic"
        }
    }
}

