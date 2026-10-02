package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

data class RestrictedAccess(val loading: Boolean = false, val enabled: Boolean = false, val profile: Boolean = false, val promotions: Boolean = false, val prices: Boolean = false, val publicAccess: Boolean = false, val publicLoading: Boolean = true)

data class AccessAccount(val uid: String, val login: String, val enabled: Boolean, val profile: Boolean, val promotions: Boolean, val prices: Boolean)
data class AccessHistory(val loading: Boolean = true, val accounts: List<AccessAccount> = emptyList(), val error: String? = null)

object RestrictedAccessRepository {
    private val accountClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()

    fun observeAccounts() = callbackFlow {
        val registration = FirebaseFirestore.getInstance().collection("restricted_access")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                when {
                    error != null -> trySend(AccessHistory(loading = false, error = "Não foi possível carregar os cadastros. Verifique a conexão."))
                    snapshot == null || snapshot.metadata.isFromCache -> trySend(AccessHistory())
                    else -> trySend(AccessHistory(loading = false, accounts = snapshot.documents.mapNotNull { doc ->
                        val login = doc.getString("login") ?: return@mapNotNull null
                        AccessAccount(doc.id, login, doc.getBoolean("enabled") == true,
                            doc.getBoolean("profile") == true, doc.getBoolean("promotions") == true, doc.getBoolean("prices") == true)
                    }.sortedBy { it.login }))
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun updatePermissions(uid: String, profile: Boolean, promotions: Boolean, prices: Boolean) {
        check(FirebaseAuth.getInstance().currentUser?.email == "mestre@nrdlojas.com") { "Acesso exclusivo do Mestre." }
        FirebaseFirestore.getInstance().collection("restricted_access").document(uid)
            .update(mapOf("enabled" to (profile || promotions || prices), "profile" to profile,
                "promotions" to promotions, "prices" to prices)).await()
    }

    suspend fun deleteAccount(uid: String) {
        val user = FirebaseAuth.getInstance().currentUser
        check(user?.email == "mestre@nrdlojas.com") { "Acesso exclusivo do Mestre." }
        val token = user!!.getIdToken(true).await().token ?: error("Entre novamente como Mestre.")
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-account-admin")
                .header("x-firebase-token", token).header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .post(JSONObject().put("uid", uid).toString().toRequestBody("application/json".toMediaType())).build()
            accountClient.newCall(request).execute().use { response ->
                val result = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                check(response.isSuccessful && result?.optBoolean("deleted") == true) {
                    if (result?.optString("error") == "DELETE_INCOMPLETE") "O acesso foi bloqueado, mas a exclusão não terminou. Tente excluir novamente."
                    else "Não foi possível excluir o login. Verifique a conexão e tente novamente."
                }
            }
        }
    }

    fun loginEmail(login: String): String {
        val value = login.trim().lowercase(Locale.ROOT)
        require(value.matches(Regex("[a-z0-9._-]{3,64}"))) { "Use um login de 3 a 64 letras, números, ponto, traço ou sublinhado." }
        return if (value == "admin" || value == "mestre") "$value@nrdlojas.com" else "$value@usuarios.nrdlojas.com"
    }
    fun observe() = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val database = FirebaseFirestore.getInstance()
        var registration: com.google.firebase.firestore.ListenerRegistration? = null
        var accountAccess = RestrictedAccess(loading = true)
        var publicAccess = false
        var publicLoading = true
        fun publish() {
            val effective = if (publicAccess) RestrictedAccess(enabled = true, profile = true, promotions = true, prices = true, publicAccess = true) else accountAccess
            trySend(effective.copy(loading = effective.loading || (publicLoading && !effective.enabled), publicLoading = publicLoading))
        }
        val publicRegistration = database.collection("config").document("restricted_access")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { doc, error ->
                publicLoading = error == null && (doc == null || doc.metadata.isFromCache)
                publicAccess = !publicLoading && error == null && doc?.getBoolean("publicAccess") == true
                publish()
            }
        val listener = FirebaseAuth.AuthStateListener {
            registration?.remove()
            val user = it.currentUser
            accountAccess = when {
                user == null -> RestrictedAccess()
                user.email == "mestre@nrdlojas.com" -> RestrictedAccess(enabled = true, profile = true, promotions = true, prices = true)
                else -> RestrictedAccess(loading = true)
            }
            publish()
            if (user != null && user.email != "mestre@nrdlojas.com") {
                registration = database.collection("restricted_access").document(user.uid)
                    .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { doc, error ->
                        if (auth.currentUser?.uid == user.uid) {
                            accountAccess = when {
                                error != null || doc == null -> RestrictedAccess()
                                doc.metadata.isFromCache -> RestrictedAccess(loading = true)
                                else -> fromDocument(doc)
                            }
                            publish()
                        }
                    }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { publicRegistration.remove(); registration?.remove(); auth.removeAuthStateListener(listener) }
    }
    suspend fun setPublicAccess(enabled: Boolean) {
        check(FirebaseAuth.getInstance().currentUser?.email == "mestre@nrdlojas.com") { "Acesso exclusivo do Mestre." }
        FirebaseFirestore.getInstance().collection("config").document("restricted_access")
            .set(mapOf("publicAccess" to enabled)).await()
    }
    private fun fromDocument(doc: com.google.firebase.firestore.DocumentSnapshot): RestrictedAccess {
        val enabled = doc.getBoolean("enabled") == true
        return RestrictedAccess(enabled = enabled, profile = enabled && doc.getBoolean("profile") == true,
            promotions = enabled && doc.getBoolean("promotions") == true, prices = enabled && doc.getBoolean("prices") == true)
    }
    suspend fun current(): RestrictedAccess {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser?.email == "mestre@nrdlojas.com") return RestrictedAccess(enabled = true, profile = true, promotions = true, prices = true)
        return try {
            val database = FirebaseFirestore.getInstance()
            val settings = database.collection("config").document("restricted_access").get(Source.SERVER).await()
            if (settings.getBoolean("publicAccess") == true) {
                RestrictedAccess(enabled = true, profile = true, promotions = true, prices = true, publicAccess = true)
            } else {
                val user = auth.currentUser ?: return RestrictedAccess()
                val doc = database.collection("restricted_access").document(user.uid).get(Source.SERVER).await()
                if (auth.currentUser?.uid == user.uid) fromDocument(doc) else RestrictedAccess()
            }
        } catch (_: Exception) { RestrictedAccess() }
    }
    suspend fun create(login: String, password: String, profile: Boolean, promotions: Boolean, prices: Boolean) {
        check(FirebaseAuth.getInstance().currentUser?.email == "mestre@nrdlojas.com") { "Acesso exclusivo do Mestre." }
        val email = loginEmail(login)
        require(!email.endsWith("@nrdlojas.com")) { "Esse login está reservado." }
        require(password.length >= 8) { "A senha deve ter pelo menos 8 caracteres." }
        require(profile || promotions || prices) { "Selecione pelo menos uma aba." }
        val base = FirebaseApp.getInstance()
        val secondary = FirebaseApp.initializeApp(base.applicationContext, base.options, "account-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(secondary)
        try {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user ?: error("Não foi possível criar o login.")
            try {
                FirebaseFirestore.getInstance().collection("restricted_access").document(user.uid).set(mapOf(
                    "login" to login.trim().lowercase(Locale.ROOT), "enabled" to true,
                    "profile" to profile, "promotions" to promotions, "prices" to prices
                )).await()
            } catch (error: Exception) { runCatching { user.delete().await() }; throw error }
        } finally { auth.signOut(); secondary.delete() }
    }
}
