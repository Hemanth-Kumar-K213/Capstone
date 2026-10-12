package com.womensafety.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.womensafety.system.manager.SafetyMonitoringManager
import com.womensafety.system.utils.SafetyNotificationManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles actionable responses from the Android system "Are You Safe?" notification.
 * Thread-safe and idempotent to ensure no duplicate emergency escalation occurs.
 */
class SafetyNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SafetyActionReceiver"
        private val isProcessing = AtomicBoolean(false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Received safety notification action: $action")

        if (isProcessing.compareAndSet(false, true)) {
            try {
                // 1. Immediately dismiss system notification
                SafetyNotificationManager.cancelSafetyCheckNotification(context)

                // 2. Resolve safety check idempotently
                when (action) {
                    SafetyNotificationManager.ACTION_CONFIRM_SAFE -> {
                        Log.i(TAG, "User confirmed: I'M SAFE via notification action")
                        SafetyMonitoringManager.resolveSafetyCheck(context, safe = true)
                    }
                    SafetyNotificationManager.ACTION_CONFIRM_HELP -> {
                        Log.w(TAG, "User confirmed: NEED HELP via notification action")
                        SafetyMonitoringManager.resolveSafetyCheck(context, safe = false)
                    }
                }
            } finally {
                isProcessing.set(false)
            }
        } else {
            Log.w(TAG, "Duplicate or overlapping safety notification action ignored: $action")
        }
    }
}
