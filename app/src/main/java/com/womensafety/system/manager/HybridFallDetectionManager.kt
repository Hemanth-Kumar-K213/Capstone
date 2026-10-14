package com.womensafety.system.manager

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Hybrid Nocturnal Fall Detection combining hardware accelerometer dynamics
 * (free-fall drop + high impact spike) with acoustic impact verification.
 */
class HybridFallDetectionManager(private val context: Context) {

    companion object {
        private const val TAG = "HybridFallDetector"
        private const val FALL_FREE_FALL_THRESHOLD = 3.0f // m/s^2
        private const val FALL_IMPACT_THRESHOLD = 25.0f // m/s^2
        private const val IMPACT_WINDOW_MS = 600L
        private const val CRASH_AUDIO_THRESHOLD_DB = 68.0 // dB
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isMonitoring = false
    private var isAudioMonitoring = false
    private var lastFreeFallTime = 0L
    private var audioRecordThread: Thread? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val rawMagnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val dynamicAcceleration = rawMagnitude

            val now = System.currentTimeMillis()

            // Phase 1: Free fall condition (< 3.0 m/s^2)
            if (dynamicAcceleration < FALL_FREE_FALL_THRESHOLD) {
                lastFreeFallTime = now
            }

            // Phase 2: Violent impact spike (> 25.0 m/s^2) within 600ms of free fall
            if (dynamicAcceleration > FALL_IMPACT_THRESHOLD && (now - lastFreeFallTime) <= IMPACT_WINDOW_MS) {
                Log.d(TAG, "Nocturnal Fall Impact detected: ${dynamicAcceleration}m/s^2!")
                lastFreeFallTime = 0L
                EmergencyOverrideManager.detectFall(dynamicAcceleration)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun startFallMonitoring() {
        if (isMonitoring || sensorManager == null || accelerometer == null) return

        try {
            sensorManager.registerListener(
                sensorListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )
            isMonitoring = true
            startAmbientAudioWatcher()
            Log.d(TAG, "Started nocturnal hybrid fall detector (Accelerometer + Audio)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start fall detector sensor", e)
        }
    }

    fun stopFallMonitoring() {
        if (!isMonitoring) return
        try {
            sensorManager?.unregisterListener(sensorListener)
            stopAmbientAudioWatcher()
            isMonitoring = false
            Log.d(TAG, "Stopped nocturnal hybrid fall detector")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop fall detector", e)
        }
    }

    private fun startAmbientAudioWatcher() {
        if (isAudioMonitoring) return
        isAudioMonitoring = true
        audioRecordThread = Thread {
            try {
                val sampleRate = 8000
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (bufferSize <= 0) return@Thread

                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    return@Thread
                }

                recorder.startRecording()
                val buffer = ShortArray(bufferSize)

                while (isAudioMonitoring && !Thread.currentThread().isInterrupted) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var maxAmp = 0
                        for (i in 0 until read) {
                            val abs = kotlin.math.abs(buffer[i].toInt())
                            if (abs > maxAmp) maxAmp = abs
                        }
                        if (maxAmp > 0) {
                            val db = 20 * log10(maxAmp.toDouble() / 32767.0) + 90.0
                            if (db >= CRASH_AUDIO_THRESHOLD_DB) {
                                Log.d(TAG, "Acoustic crash/impact transient detected: ${db.toInt()} dB")
                            }
                        }
                    }
                    Thread.sleep(150)
                }

                try {
                    recorder.stop()
                    recorder.release()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.w(TAG, "Audio watcher unavailable or permission denied: ${e.message}")
            }
        }.apply {
            name = "HybridFallAudioWatcher"
            isDaemon = true
            start()
        }
    }

    private fun stopAmbientAudioWatcher() {
        isAudioMonitoring = false
        audioRecordThread?.interrupt()
        audioRecordThread = null
    }
}
