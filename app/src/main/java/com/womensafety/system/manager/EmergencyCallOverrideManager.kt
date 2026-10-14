package com.womensafety.system.manager

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Monitors incoming telephony calls in SLEEPING Mode to break through silent mode
 * if 3 emergency calls arrive within 60 seconds.
 */
class EmergencyCallOverrideManager(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    companion object {
        private const val TAG = "EmergencyCallOverride"
    }

    private var isListening = false
    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: Any? = null

    fun startCallMonitoring() {
        if (isListening || telephonyManager == null) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerModernCallback()
            } else {
                registerLegacyListener()
            }
            isListening = true
            Log.d(TAG, "Registered telephony call state listener for Emergency Sleep Override")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering phone state listener", e)
        }
    }

    fun stopCallMonitoring() {
        if (!isListening || telephonyManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (modernCallback as? TelephonyCallback)?.let {
                    telephonyManager.unregisterTelephonyCallback(it)
                }
            } else {
                legacyListener?.let {
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
            isListening = false
            Log.d(TAG, "Unregistered telephony call state listener")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering phone state listener", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyListener() {
        legacyListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    val number = phoneNumber?.ifBlank { "Emergency Contact" } ?: "Emergency Contact"
                    EmergencyOverrideManager.onIncomingCall(number, number)
                }
            }
        }
        telephonyManager?.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernCallback() {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    EmergencyOverrideManager.onIncomingCall("Incoming Caller", "Emergency Caller")
                }
            }
        }
        modernCallback = callback
        context.mainExecutor?.let { executor ->
            telephonyManager?.registerTelephonyCallback(executor, callback)
        }
    }
}
