package com.example.util

import android.util.Log
import com.example.data.UserPreferences
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data["title"]?.trim()
        val body = message.data["body"]?.trim()
        val type = message.data["type"]?.trim()

        if (title.isNullOrBlank() || body.isNullOrBlank() || type.isNullOrBlank()) {
            Log.w(TAG, "Mensagem FCM ignorada: title, body ou type ausente no payload data-only")
            return
        }

        if (type !in SUPPORTED_TYPES) {
            Log.w(TAG, "Mensagem FCM ignorada: type inválido ($type)")
            return
        }

        Log.d(TAG, "Mensagem FCM data-only recebida: type=$type")
        val preferences = UserPreferences(applicationContext)

        runBlocking {
            val notificationsEnabled = preferences.notificationsEnabled.first()
            if (!notificationsEnabled) {
                Log.d(TAG, "Mensagem FCM ignorada: notificações gerais desativadas; type=$type")
                return@runBlocking
            }

            val specificPreferenceEnabled = when (type) {
                TYPE_NEW_PRODUCT -> preferences.notificationsProductAddedEnabled.first()
                TYPE_CODE_CHANGED -> preferences.notificationsCodeChangedEnabled.first()
                else -> false
            }

            Log.d(
                TAG,
                "Preferências FCM: geral=$notificationsEnabled, específica=$specificPreferenceEnabled, type=$type",
            )

            if (!specificPreferenceEnabled) {
                Log.d(TAG, "Mensagem FCM ignorada: preferência específica desativada; type=$type")
                return@runBlocking
            }

            val channelId = NotificationHelper.channelIdForType(type)
            if (channelId == null) {
                Log.w(TAG, "Mensagem FCM ignorada: canal indisponível para type=$type")
                return@runBlocking
            }

            preferences.addNotification(
                com.example.data.AppNotification(
                    id = System.currentTimeMillis(),
                    type = type,
                    title = title,
                    body = body,
                    read = false,
                    timestamp = System.currentTimeMillis()
                )
            )
            Log.d("MyFirebaseMessaging", "Exibindo notificação local: type=$type, canal=$channelId")
            NotificationHelper.showNotification(applicationContext, type, title, body)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    private companion object {
        const val TAG = "MyFirebaseMessaging"
        const val TYPE_NEW_PRODUCT = "NEW_PRODUCT"
        const val TYPE_CODE_CHANGED = "CODE_CHANGED"
        val SUPPORTED_TYPES = setOf(TYPE_NEW_PRODUCT, TYPE_CODE_CHANGED)
    }
}
