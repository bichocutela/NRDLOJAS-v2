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
        Log.d("MyApplication", "Application started and CrashReporter setup")
    }
}
