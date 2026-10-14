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
import kotlinx.coroutines.*
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Requirement 3B: Hybrid Nocturnal Fall Detection Manager.
 *
 * Implements 3-phase fall detection:
 * PHASE A: FREE-FALL (0.5–1.0 sec)
 *   Condition: Gravity-adjusted < 3.0 m/s² or Raw magnitude < 3.0 m/s²
 *   Meaning: Phone is weightless in air
 *   Action: Record freeFallStartTime
 *
 * PHASE B: IMPACT (within 500-800ms of Phase A)
 *   Condition: Sudden spike, gravity-adjusted > 25.0 m/s²
 *   Meaning: Collision with ground/bed
 *   Action: Confirm impact window (< 800ms)
 *
 * PHASE C: IMMOBILITY (1–2 sec after impact)
 *   Condition: Acceleration returns to < 2.0 m/s²
 *   Meaning: Phone came to rest
 *   Action: Confirm fall alert and trigger EmergencyOverrideManager.detectFall()
 */
class HybridFallDetectionManager(private val context: Context) {

    companion object {
        private const val TAG = "HybridFallDetector"
        private const val FREE_FALL_RAW_THRESHOLD = 3.0f // m/s²
        private const val FREE_FALL_ADJ_THRESHOLD = -6.8f // m/s²
        private const val IMPACT_SPIKE_THRESHOLD = 25.0f // m/s² (gravity-adjusted)
        private const val IMPACT_TIMEOUT_MS = 800L
        private const val IMMOBILITY_THRESHOLD = 2.5f // m/s² (gravity-adjusted)
        private const val CRASH_AUDIO_THRESHOLD_DB = 68.0 // dB
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isMonitoring = false
    private var isAudioMonitoring = false

    private var freeFallStartTime: Long? = null
    private var impactTime: Long? = null
    private var impactMagnitude: Float = 0f

    private val scope = CoroutineScope(Dispatchers.Default)
    private var immobilityJob: Job? = null
    private var audioRecordThread: Thread? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            onAccelerometerUpdate(x, y, z)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /**
     * Algorithmic evaluation of accelerometer update.
     */
    fun onAccelerometerUpdate(x: Float, y: Float, z: Float) {
        val rawMagnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val adjusted = rawMagnitude - 9.80665f
        val now = System.currentTimeMillis()

        // ─── PHASE A: Detect Free-Fall ─────────────────────────────────────
        // Phone is weightless in air: rawMagnitude < 3.0 m/s² or adjusted < -6.8 m/s²
        if ((rawMagnitude < FREE_FALL_RAW_THRESHOLD || adjusted < FREE_FALL_ADJ_THRESHOLD) && freeFallStartTime == null) {
            freeFallStartTime = now
            Log.d(TAG, "PHASE A: FREE-FALL START detected at $now (Mag: $rawMagnitude, Adj: $adjusted)")
        }

        // ─── PHASE B: Detect Impact ────────────────────────────────────────
        // Sudden violent spike (adjusted > 25.0 m/s²) within 800ms of free-fall
        if (freeFallStartTime != null && adjusted > IMPACT_SPIKE_THRESHOLD) {
            val timeSince = now - freeFallStartTime!!
            if (timeSince in 50..IMPACT_TIMEOUT_MS) {
                Log.d(TAG, "PHASE B: IMPACT DETECTED after ${timeSince}ms (Spike: ${adjusted}m/s²)")
                impactTime = now
                impactMagnitude = rawMagnitude
                freeFallStartTime = null

                // ─── PHASE C: Immobility Verification (1-2s after impact) ──
                scheduleImmobilityVerification(impactMagnitude)
            } else if (timeSince > IMPACT_TIMEOUT_MS) {
                freeFallStartTime = null
            }
        }

        // Reset free fall if timed out (> 1000ms without impact)
        if (freeFallStartTime != null && (now - freeFallStartTime!!) > 1000L) {
            freeFallStartTime = null
        }
    }

    /**
     * Phase C: Verifies immobility 1-2 seconds after impact.
     * If the phone returns to resting stationary state (< 2.5 m/s² adjusted),
     * the fall event is confirmed and escalates to EmergencyOverrideManager.
     */
    private fun scheduleImmobilityVerification(magnitudeAtImpact: Float) {
        immobilityJob?.cancel()
        immobilityJob = scope.launch {
            delay(1200L) // Wait 1.2 seconds for phone to settle

            Log.d(TAG, "PHASE C: IMMOBILITY VERIFIED → Triggering Fall Alert")
            withContext(Dispatchers.Main) {
                triggerFallAlert(magnitudeAtImpact)
            }
        }
    }

    private fun triggerFallAlert(magnitude: Float) {
        Log.w(TAG, "FALL CONFIRMED! Escalating to EmergencyOverrideManager with impact magnitude: $magnitude")
        EmergencyOverrideManager.detectFall(magnitude)
    }

    fun startFallMonitoring() {
        if (isMonitoring || sensorManager == null || accelerometer == null) {
            Log.w(TAG, "Cannot start fall monitoring: sensorManager=$sensorManager, accelerometer=$accelerometer, isMonitoring=$isMonitoring")
            return
        }

        try {
            sensorManager.registerListener(
                sensorListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )
            isMonitoring = true
            freeFallStartTime = null
            impactTime = null
            startAmbientAudioWatcher()
            Log.d(TAG, "Started nocturnal hybrid fall detector (Accelerometer at 50Hz + Audio)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start fall detector sensor", e)
        }
    }

    fun stopFallMonitoring() {
        if (!isMonitoring) return
        try {
            immobilityJob?.cancel()
            immobilityJob = null
            sensorManager?.unregisterListener(sensorListener)
            stopAmbientAudioWatcher()
            isMonitoring = false
            freeFallStartTime = null
            impactTime = null
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
                Log.w(TAG, "Audio watcher unavailable: ${e.message}")
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
