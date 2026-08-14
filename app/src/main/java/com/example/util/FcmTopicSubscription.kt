package com.example.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FcmTopicSubscription {
    private const val TAG = "FcmTopicSubscription"
    private const val PRODUCTS_TOPIC = "products"

    suspend fun reconcile(notificationsEnabled: Boolean) {
        val operation = if (notificationsEnabled) "subscribe" else "unsubscribe"
        Log.d(TAG, "Operação solicitada: $operation no tópico $PRODUCTS_TOPIC")

        try {
            if (notificationsEnabled) {
                FirebaseMessaging.getInstance().subscribeToTopic(PRODUCTS_TOPIC).await()
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(PRODUCTS_TOPIC).await()
            }
            Log.d(TAG, "Operação concluída com sucesso: $operation no tópico $PRODUCTS_TOPIC")
        } catch (error: Exception) {
            Log.e(TAG, "Falha na operação $operation no tópico $PRODUCTS_TOPIC", error)
        }
    }
}
