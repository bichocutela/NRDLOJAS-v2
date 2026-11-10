package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Best-effort write-through for a tiny set of account preferences.
 * The local DataStore remains authoritative/offline-capable; no periodic reads or listeners.
 * No private notification history, credentials or device settings leave the device.
 */
internal object UserPreferenceCloudSync {
    suspend fun upload(context: Context) = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext
        if (user.isAnonymous) return@withContext
        val preferences = UserPreferences(context.applicationContext)
        val payload = mapOf(
            "favoriteStoreCode" to preferences.favoriteStoreCode.first().orEmpty().take(12),
            "appTheme" to preferences.appTheme.first().take(40),
            "notificationsEnabled" to preferences.notificationsEnabled.first(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        runCatching {
            FirebaseFirestore.getInstance().collection("user_preferences").document(user.uid)
                .set(payload, SetOptions.merge()).await()
        }.onFailure { Log.w("UserPreferenceCloudSync", "Preferências mantidas localmente; sincronização adiada", it) }
    }
}
