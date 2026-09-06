package com.womensafety.system.utils

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.womensafety.system.data.model.ShakeSensitivity
import kotlin.math.sqrt

/**
 * High-precision accelerometer sensor listener that detects physical phone shake gestures.
 * Uses gravity-subtracted acceleration magnitude with debouncing to avoid false positives.
 */
class ShakeDetector(
    private var sensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
        private const val DEBOUNCE_TIME_MS = 500L
        private const val COOLDOWN_TIME_MS = 2000L
    }

    private var lastShakeTimestamp = 0L
    private var isListening = false

    fun start(sensorManager: SensorManager): Boolean {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            Log.w(TAG, "Accelerometer sensor not available on this device")
            return false
        }

        val registered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
        isListening = registered
        return registered
    }

    fun stop(sensorManager: SensorManager) {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }

    fun updateSensitivity(newSensitivity: ShakeSensitivity) {
        this.sensitivity = newSensitivity
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate raw acceleration magnitude
        val gForce = sqrt((x * x + y * z + z * z).toDouble()).toFloat()

        // Net acceleration beyond Earth gravity (approx 9.81 m/s²)
        val netAcceleration = kotlin.math.abs(gForce - SensorManager.GRAVITY_EARTH)

        if (netAcceleration >= sensitivity.threshold) {
            val now = System.currentTimeMillis()

            // Check if debounce time has passed since last sample, but cooldown active
            if (lastShakeTimestamp + COOLDOWN_TIME_MS <= now) {
                lastShakeTimestamp = now
                Log.d(TAG, "Shake detected with net acceleration: $netAcceleration (Threshold: ${sensitivity.threshold})")
                onShakeDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed for accelerometer accuracy changes
    }
}
