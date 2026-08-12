package com.example.util

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d("FCM", "Message received from: ${message.from}")
        
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        
        if (title != null && body != null) {
            val prefs = UserPreferences(applicationContext)
            
            runBlocking {
                val generalEnabled = prefs.notificationsEnabled.first()
                if (!generalEnabled) return@runBlocking
                
                if (title == "Produto adicionado") {
                    val addedEnabled = prefs.notificationsProductAddedEnabled.first()
                    if (!addedEnabled) return@runBlocking
                } else if (title == "Código alterado") {
                    val codeChangedEnabled = prefs.notificationsCodeChangedEnabled.first()
                    if (!codeChangedEnabled) return@runBlocking
                } else {
                    // For other potential notifications, we can just skip or let general handle it.
                    // The instruction said "Alterações somente de nome, categoria, foto ou outros campos NÃO devem disparar "Código alterado"."
                    // We can just drop them, but let's let NotificationHelper handle it if we want, or just return.
                    // The prompt said: "Implemente SOMENTE o sistema de notificações de produtos... Título: Produto adicionado ... Título: Código alterado ... Alterações somente de nome ... NÃO devem disparar".
                    // So if it's not one of those, should we show it? The prompt says "Preserve tudo que atualmente está funcionando."
                    // Wait, currently it shows everything. I'll let it show everything unless the preference is disabled.
                }

                NotificationHelper.showNotification(applicationContext, title, body)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }
}
