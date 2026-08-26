package com.example

import android.app.Application
import android.util.Log

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.setup(this)
        try {
            com.example.data.FirebaseService.initialize(this)
            Log.d("MyApplication", "Firebase initialized manually")
        } catch (e: Exception) {
            Log.e("MyApplication", "Firebase initialization failed", e)
        }
        try {
            com.example.util.UpdateNotificationWorker.schedule(this)
            Log.d("MyApplication", "Periodic update check scheduled")
        } catch (e: IllegalStateException) {
            Log.w("MyApplication", "Periodic update check not scheduled in this process", e)
        }
        try {
            com.example.util.PromotionNotificationWorker.schedule(this)
            Log.d("MyApplication", "Favorite store promotion check scheduled")
        } catch (e: IllegalStateException) {
            Log.w("MyApplication", "Favorite store promotion check not scheduled in this process", e)
        }
        Log.d("MyApplication", "Application started and CrashReporter setup")
    }
}
