package com.womensafety.system

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * Application class — initializes Firebase on app start.
 */
class WomenSafetyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        com.womensafety.system.utils.NotificationChannelManager.createNotificationChannels(this)
        com.womensafety.system.manager.SafetyMonitoringManager.initialize(this)
        com.womensafety.system.manager.TravelModeManager.initialize(this)
        com.womensafety.system.manager.SOSManager.initialize(this)
        com.womensafety.system.manager.EmergencyOverrideManager.initialize(this)
        com.womensafety.system.manager.RoutineAnalyzer.initialize(this)
        com.womensafety.system.manager.ColleagueAwarenessManager.initialize(this)
        val fallDetector = com.womensafety.system.manager.HybridFallDetectionManager.initialize(this)
        fallDetector.startFallMonitoring()
        com.womensafety.system.manager.BatteryMonitoringManager.initialize(this)
    }
}
