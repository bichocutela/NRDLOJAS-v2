package com.example.util

import android.content.Context
import com.example.data.StoreEncarte
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object EncarteNotifications {
    const val TYPE = "NEW_ENCARTE"
    const val TOPIC = "encartes_natal"
    enum class Mode { OFF, SIMPLE, DETAILED }
    private fun prefs(context: Context) = context.getSharedPreferences("encarte_notifications", Context.MODE_PRIVATE)
    fun mode(context: Context): Mode = runCatching { Mode.valueOf(prefs(context).getString("mode", "OFF")!!) }.getOrDefault(Mode.OFF)
    internal fun saveMode(context: Context, mode: Mode) { prefs(context).edit().putString("mode", mode.name).apply() }
    suspend fun setMode(context: Context, next: Mode) {
        // One topic and one enum: changing the style cannot create a second subscription.
        if (next == Mode.OFF) FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC).await()
        else FirebaseMessaging.getInstance().subscribeToTopic(TOPIC).await()
        saveMode(context, next)
    }
    suspend fun reconcile(context: Context, generalEnabled: Boolean) {
        if (generalEnabled && mode(context) != Mode.OFF) FirebaseMessaging.getInstance().subscribeToTopic(TOPIC).await()
        else FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC).await()
    }
    fun content(mode: Mode, data: Map<String, String>, now: Long = System.currentTimeMillis()): Pair<String, String>? {
        if (mode == Mode.OFF || data["encarteId"]?.toLongOrNull()?.let { it > 0 } != true) return null
        val name = data["encarteName"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val item = StoreEncarte(1, name, data["startDate"].orEmpty(), data["endDate"].orEmpty(), "", "")
        if (!item.isActive(now)) return null
        return if (mode == Mode.SIMPLE) "Novo encarte disponível" to "Há um novo encarte para Natal e região. Toque para conferir."
        else "Novo Encarte: $name" to "$name\nValidade: ${item.validity}. Toque para conferir."
    }
    fun alreadyDelivered(context: Context, id: String): Boolean = prefs(context).getStringSet("delivered", emptySet())!!.contains(id)
    fun markDelivered(context: Context, id: String) {
        val ids = prefs(context).getStringSet("delivered", emptySet())!!.toMutableSet()
        ids.add(id)
        prefs(context).edit().putStringSet("delivered", ids.sortedByDescending { it.toLongOrNull() ?: 0 }.take(256).toSet()).apply()
    }
}
