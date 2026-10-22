package com.example

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

object CrashReporter {
    private const val PREFS_NAME = "crash_prefs"
    private const val CRASH_KEY = "last_crash"

    fun setup(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            val error = sw.toString()
            
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(CRASH_KEY, error)
                .commit()
                
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    fun getCrashLog(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(CRASH_KEY, null)
    }

    fun clearCrashLog(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(CRASH_KEY).apply()
    }
}
