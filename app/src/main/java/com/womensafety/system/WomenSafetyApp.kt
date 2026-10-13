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
    }
}
