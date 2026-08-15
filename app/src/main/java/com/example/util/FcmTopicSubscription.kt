package com.example.util

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FcmTopicSubscription {
    private const val TAG = "FcmTopicSubscription"
    private const val PRODUCTS_TOPIC = "products"
    private const val SUGGESTION_TOPIC_PREFIX = "suggestion_"

    fun suggestionTopicForInstallation(installationId: String): String =
        "$SUGGESTION_TOPIC_PREFIX${installationId.replace("-", "")}".take(900)

    suspend fun subscribeToSuggestionTopic(installationId: String) {
        if (installationId.isBlank()) return
        val topic = suggestionTopicForInstallation(installationId)
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
            Log.d(TAG, "Inscrição concluída no tópico privado de sugestões")
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao inscrever no tópico privado de sugestões", error)
        }
    }

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
