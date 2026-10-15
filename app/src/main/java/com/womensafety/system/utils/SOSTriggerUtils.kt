package com.womensafety.system.utils

import android.content.Context
import android.util.Log
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.model.SOSTriggerMethod
import com.womensafety.system.manager.SOSManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Requirement 3B: Unified SOS Trigger Utility.
 * Provides a single, centralized trigger mechanism for all safety events
 * (fall detection, route deviation, manual SOS button, sensor shakes, etc.)
 */
object SOSTriggerUtils {

    private const val TAG = "SOSTriggerUtils"
    private val authManager by lazy { FirebaseAuthManager() }

    /**
     * Unified entry point for triggering an emergency SOS alert.
     *
     * @param context Application or UI context
     * @param userId Current user ID (defaults to active Firebase auth user)
     * @param triggerMethod String description (e.g. "FALL_DETECTION", "ROUTE_DEVIATION", "MANUAL_BUTTON_PRESS")
     * @param eventType Optional event categorization tag
     * @return The created [SOSEvent] or null if triggering failed
     */
    suspend fun triggerSOS(
        context: Context,
        userId: String? = null,
        triggerMethod: String = "MANUAL_BUTTON_PRESS",
        eventType: String = "EMERGENCY_ALERT"
    ): SOSEvent? = withContext(Dispatchers.IO) {
        val effectiveUid = userId ?: authManager.currentUserId
        Log.w(TAG, "Initiating unified SOS trigger: method=$triggerMethod, eventType=$eventType, uid=$effectiveUid")

        val methodEnum = when (triggerMethod.uppercase()) {
            "FALL_DETECTION", "FALL" -> SOSTriggerMethod.FALL
            "ROUTE_DEVIATION", "DEVIATION" -> SOSTriggerMethod.ROUTE_DEVIATION
            "SHAKE", "SHAKE_DETECTION" -> SOSTriggerMethod.SHAKE
            "VOICE" -> SOSTriggerMethod.VOICE
            "MANUAL_BUTTON_PRESS", "BUTTON", "MANUAL_SOS_BUTTON" -> SOSTriggerMethod.BUTTON
            else -> SOSTriggerMethod.AUTO
        }

        try {
            // SOSManager.triggerSOS handles:
            // 1. Instant publication of active SOSEvent StateFlow
            // 2. Maximum volume siren alarm playback via SoundManager
            // 3. High-priority SMS dispatch via carrier + 2Factor gateway
            // 4. Creation & Firestore sync of SOSEvent under sosEvents/{eventId}
            // 5. Automated emergency phone call placement (if enabled)
            val event = SOSManager.triggerSOS(
                context = context,
                triggerMethod = methodEnum
            )

            Log.i(TAG, "Unified SOS successfully executed. EventId: ${event?.id}")
            event
        } catch (e: Exception) {
            Log.e(TAG, "Error executing unified SOS: ${e.message}", e)
            null
        }
    }
}
