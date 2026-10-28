package com.womensafety.system.manager

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.womensafety.system.data.model.BatteryStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * Requirement 4B: Device Battery Monitoring Manager.
 * Periodically collects battery health, percentage, voltage, and charging state
 * every 5 minutes and synchronizes to Firestore 'deviceBatteryStatus' collection.
 */
object BatteryMonitoringManager {

    private const val TAG = "BatteryMonitoring"
    private const val MONITOR_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

    private var appContext: Context? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitoringJob: Job? = null

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        startMonitoring()

        // Also observe auth state changes so when user logs in, monitoring is active
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null && monitoringJob?.isActive != true) {
                startMonitoring()
            }
        }
    }

    fun startMonitoring() {
        val context = appContext ?: return
        if (monitoringJob?.isActive == true) {
            Log.d(TAG, "Battery monitoring job already running")
            return
        }

        Log.d(TAG, "Starting periodic battery monitoring loop (every 5 min)")
        monitoringJob = scope.launch {
            while (isActive) {
                try {
                    val uid = auth.currentUser?.uid
                    if (!uid.isNullOrBlank()) {
                        val status = getBatteryStatus(context, uid)
                        saveBatteryStatus(uid, status)
                        Log.d(TAG, "Recorded battery update: ${status.batteryPercentage}% (Charging: ${status.isCharging})")
                    } else {
                        Log.d(TAG, "No authenticated user. Skipping battery sync.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in battery monitoring cycle: ${e.message}", e)
                }

                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        Log.d(TAG, "Stopped battery monitoring")
    }

    /**
     * Reads the immediate live battery percentage directly from system broadcast receiver.
     */
    fun getCurrentBatteryPercentage(context: Context? = null): Int {
        val ctx = context ?: appContext
        if (ctx == null) return 100
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = ctx.registerReceiver(null, ifilter)
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            100
        }
    }

    /**
     * Checks if the device is currently plugged in and charging.
     */
    fun isDeviceCharging(context: Context? = null): Boolean {
        val ctx = context ?: appContext ?: return false
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = ctx.registerReceiver(null, ifilter)
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getBatteryStatus(context: Context, userId: String): BatteryStatus {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, ifilter)

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100).toInt()
        } else {
            -1
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargingPlugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = if (rawTemp > 0) rawTemp / 10 else 0 // Extra temperature is in tenths of degree Celsius
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: 0
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        val now = System.currentTimeMillis()
        return BatteryStatus(
            userId = userId,
            deviceId = "${Build.MANUFACTURER} ${Build.MODEL}",
            batteryPercentage = batteryPct.coerceIn(0, 100),
            isCharging = isCharging,
            chargingStatus = chargingPlugged,
            temperature = tempCelsius,
            health = health,
            voltage = voltage,
            technology = technology,
            timestamp = now,
            lastUpdated = now
        )
    }

    private suspend fun saveBatteryStatus(userId: String, status: BatteryStatus) {
        try {
            val nowStr = status.timestamp.toString()

            // 1. Save detailed periodic history record
            db.collection("deviceBatteryStatus")
                .document(userId)
                .collection("history")
                .document(nowStr)
                .set(status.toMap())
                .await()

            // 2. Update top-level latest telemetry document for fast dashboard queries
            db.collection("deviceBatteryStatus")
                .document(userId)
                .set(mapOf(
                    "userId" to userId,
                    "deviceId" to status.deviceId,
                    "latestBattery" to status.batteryPercentage,
                    "latestCharging" to status.isCharging,
                    "latestChargingStatus" to status.chargingStatus,
                    "temperature" to status.temperature,
                    "health" to status.health,
                    "voltage" to status.voltage,
                    "technology" to status.technology,
                    "lastUpdated" to FieldValue.serverTimestamp()
                ), SetOptions.merge())
                .await()

        } catch (e: Exception) {
            Log.e(TAG, "Failed saving battery status to Firestore: ${e.message}", e)
        }
    }
}
