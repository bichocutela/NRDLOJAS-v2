package com.example.data

import android.content.Context
import com.example.BuildConfig
import com.example.util.UpdateChecker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UpdatePolicy(val enabled: Boolean = false, val minimumVersion: String = "", val loaded: Boolean = false) {
    fun requiresUpdate(currentVersion: String): Boolean = enabled && validVersion(minimumVersion) &&
        UpdateChecker.isRemoteVersionNewer(currentVersion, minimumVersion)

    companion object {
        fun validVersion(version: String): Boolean = version.matches(Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+")) &&
            version.removePrefix("v").split('.').all { it.toIntOrNull() != null }
    }
}

object UpdatePolicyRepository {
    private const val PREFERENCES = "mandatory_update_policy"
    fun cached(context: Context): UpdatePolicy {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return UpdatePolicy(preferences.getBoolean("enabled", false), preferences.getString("minimumVersion", "").orEmpty())
    }
    fun observe(context: Context) = callbackFlow {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        trySend(cached(context))
        val registration = FirebaseFirestore.getInstance().collection("config").document("update_policy")
            .addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
                if (error == null && doc != null && !doc.metadata.isFromCache) {
                    val minimum = doc.getString("minimumVersion").orEmpty()
                    val policy = UpdatePolicy(doc.getBoolean("enabled") == true && UpdatePolicy.validVersion(minimum), minimum, loaded = true)
                    preferences.edit().putBoolean("enabled", policy.enabled).putString("minimumVersion", minimum).apply()
                    trySend(policy)
                }
            }
        awaitClose { registration.remove() }
    }
    suspend fun save(enabled: Boolean, minimumVersion: String) {
        check(FirebaseAuth.getInstance().currentUser?.email == "mestre@nrdlojas.com") { "Acesso exclusivo do Mestre." }
        require(UpdatePolicy.validVersion(minimumVersion)) { "Selecione uma versão válida." }
        if (enabled) {
            require(!UpdateChecker.isRemoteVersionNewer(BuildConfig.VERSION_NAME, minimumVersion)) {
                "Atualize primeiro o aparelho do Mestre para a versão selecionada."
            }
            val versions = UpdateChecker.publishedVersionTags()
            require(minimumVersion in versions) { "Essa versão não está disponível para download. Recarregue a lista." }
        }
        FirebaseFirestore.getInstance().collection("config").document("update_policy")
            .set(mapOf("enabled" to enabled, "minimumVersion" to minimumVersion)).await()
    }
}
