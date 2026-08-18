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
    }
}
