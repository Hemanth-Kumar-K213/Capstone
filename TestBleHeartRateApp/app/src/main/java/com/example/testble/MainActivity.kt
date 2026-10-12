package com.example.testble

import android.Manifest
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var bleManager: BleHeartRateManager
    private lateinit var adapter: BleDeviceAdapter

    private lateinit var statusText: TextView
    private lateinit var heartRateText: TextView
    private lateinit var serviceStatusText: TextView
    private lateinit var scanButton: Button
    private lateinit var stopButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var deviceList: RecyclerView

    private val PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        bleManager = BleHeartRateManager(this)
        setupAdapter()
        setupListeners()
        observeBleState()

        // Request permissions on startup
        if (!hasPermissions()) {
            requestPermissions()
        }

        Timber.d("🚀 App started")
    }

    private fun initViews() {
        statusText = findViewById(R.id.status_text)
        heartRateText = findViewById(R.id.heart_rate_text)
        serviceStatusText = findViewById(R.id.service_status_text)
        scanButton = findViewById(R.id.scan_button)
        stopButton = findViewById(R.id.stop_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        deviceList = findViewById(R.id.device_list)
    }

    private fun setupAdapter() {
        adapter = BleDeviceAdapter { address, name ->
            bleManager.stopScan()
            bleManager.connectToDevice(address, name)
        }
        deviceList.layoutManager = LinearLayoutManager(this)
        deviceList.adapter = adapter
    }

    private fun setupListeners() {
        scanButton.setOnClickListener {
            if (hasPermissions()) {
                bleManager.startScan()
            } else {
                requestPermissions()
            }
        }

        stopButton.setOnClickListener {
            bleManager.stopScan()
        }

        disconnectButton.setOnClickListener {
            bleManager.disconnect()
        }
    }

    private fun observeBleState() {
        lifecycleScope.launch {
            bleManager.connectionStatus.collect { status ->
                statusText.text = status
            }
        }

        lifecycleScope.launch {
            bleManager.heartRateBpm.collect { bpm ->
                heartRateText.text = if (bpm != null && bpm > 0) {
                    "$bpm BPM ❤️"
                } else {
                    "-- BPM"
                }
            }
        }

        lifecycleScope.launch {
            bleManager.serviceStatus.collect { status ->
                serviceStatusText.text = status.ifEmpty { "Not tested yet" }
            }
        }

        lifecycleScope.launch {
            bleManager.scanResults.collect { results ->
                adapter.updateDevices(results)
            }
        }

        lifecycleScope.launch {
            bleManager.isScanning.collect { isScanning ->
                scanButton.isEnabled = !isScanning
                stopButton.isEnabled = isScanning
                disconnectButton.isEnabled = !isScanning
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Timber.d("✅ Permissions granted")
        } else {
            Timber.e("❌ Permissions denied")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}
