package com.womensafety.system

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * Application class — initializes Firebase on app start.
 */
class WomenSafetyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("WomenSafetyApp", "Uncaught exception on thread: ${thread.name}", throwable)
        }
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Throwable) {
            android.util.Log.e("WomenSafetyApp", "FirebaseApp initializeApp error", e)
        }
        try {
            com.womensafety.system.utils.NotificationChannelManager.createNotificationChannels(this)
        } catch (e: Throwable) {
            android.util.Log.e("WomenSafetyApp", "createNotificationChannels error", e)
        }
        try {
            com.womensafety.system.manager.SafetyMonitoringManager.initialize(this)
        } catch (e: Throwable) {
            android.util.Log.e("WomenSafetyApp", "SafetyMonitoringManager initialize error", e)
        }
        try {
            com.womensafety.system.manager.TravelModeManager.initialize(this)
        } catch (e: Throwable) {
            android.util.Log.e("WomenSafetyApp", "TravelModeManager initialize error", e)
        }
    }
}
