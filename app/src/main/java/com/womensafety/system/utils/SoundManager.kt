package com.womensafety.system.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Manages alarm sirens and audio cues for emergency SOS triggers.
 * Bypasses silent mode by routing audio to USAGE_ALARM.
 */
object SoundManager {

    private const val TAG = "SoundManager"
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var isPlayingSiren = false

    /**
     * Plays emergency siren sound on repeat.
     */
    fun playSiren(context: Context) {
        if (isPlayingSiren) return

        try {
            stopSiren()

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (alarmUri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                isPlayingSiren = true
            } else {
                // Fallback using ToneGenerator
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 10000)
                isPlayingSiren = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing siren alarm", e)
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 10000)
                isPlayingSiren = true
            } catch (te: Exception) {
                Log.e(TAG, "ToneGenerator fallback also failed", te)
            }
        }
    }

    /**
     * Stops the running siren.
     */
    fun stopSiren() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null

            toneGenerator?.let {
                it.stopTone()
                it.release()
            }
            toneGenerator = null
            isPlayingSiren = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping siren", e)
        }
    }

    /**
     * Plays a pleasant chime sound when SOS is marked safe or resolved.
     */
    fun playResolved(context: Context) {
        try {
            stopSiren()
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (notificationUri != null) {
                val player = MediaPlayer.create(context, notificationUri)
                player?.setOnCompletionListener { it.release() }
                player?.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing resolved tone", e)
        }
    }
}
