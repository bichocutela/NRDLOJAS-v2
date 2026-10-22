package com.example.util

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object TrainingNotifications {
    const val TYPE = "NEW_TRAINING"
    const val TOPIC = "training_updates"
    enum class Mode { OFF, ON }
    private fun prefs(c: Context) = c.getSharedPreferences("training_notifications", Context.MODE_PRIVATE)
    fun mode(c: Context) = if (prefs(c).getBoolean("enabled", false)) Mode.ON else Mode.OFF
    suspend fun setMode(c: Context, mode: Mode) {
        if (mode == Mode.ON) FirebaseMessaging.getInstance().subscribeToTopic(TOPIC).await()
        else FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC).await()
        prefs(c).edit().putBoolean("enabled", mode == Mode.ON).apply()
    }
    suspend fun reconcile(c: Context, generalEnabled: Boolean) {
        if (generalEnabled && mode(c) == Mode.ON) FirebaseMessaging.getInstance().subscribeToTopic(TOPIC).await()
        else FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC).await()
    }
    fun delivered(c: Context, id: String) = prefs(c).getStringSet("delivered", emptySet())!!.contains(id)
    fun markDelivered(c: Context, id: String) {
        val ids = prefs(c).getStringSet("delivered", emptySet())!!.toMutableSet()
        ids.add(id)
        prefs(c).edit().putStringSet("delivered", ids.toList().takeLast(256).toSet()).apply()
    }
}
