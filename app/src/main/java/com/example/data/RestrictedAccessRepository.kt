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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.io.File

data class RestrictedAccess(val loading: Boolean = false, val enabled: Boolean = false, val profile: Boolean = false, val promotions: Boolean = false, val prices: Boolean = false, val publicAccess: Boolean = false, val publicLoading: Boolean = true)

data class AccessAccount(val uid: String, val login: String, val enabled: Boolean, val profile: Boolean, val promotions: Boolean, val prices: Boolean)
data class AccessHistory(val loading: Boolean = true, val accounts: List<AccessAccount> = emptyList(), val error: String? = null)

object RestrictedAccessRepository {
    private val accountClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()

    private val sessionMutex = Mutex()
    @Volatile private var verifiedSessionUid: String? = null

    private fun deviceToken(): String {
        val directory = FirebaseApp.getInstance().applicationContext.noBackupFilesDir
        val file = File(directory, "restricted-device-session")
        if (file.exists()) return file.readText().also { check(it.matches(Regex("[a-f0-9]{64}"))) }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val value = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        file.writeText(value)
        return value
    }

    suspend fun claimDeviceSession() = sessionMutex.withLock {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Entre novamente.")
        if (user.email?.endsWith("@usuarios.nrdlojas.com") != true) return@withLock
        if (verifiedSessionUid == user.uid) return@withLock
        sessionRequest("claim")
        if (FirebaseAuth.getInstance().currentUser?.uid == user.uid) verifiedSessionUid = user.uid
    }

    suspend fun deviceSessionHeader(): String? {
        if (runCatching { FirebaseAuth.getInstance().currentUser?.email?.endsWith("@usuarios.nrdlojas.com") == true }.getOrDefault(false)) {
            claimDeviceSession()
            return withContext(Dispatchers.IO) { deviceToken() }
        }
        return null
    }

    suspend fun abandonLogin() = sessionMutex.withLock {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && verifiedSessionUid == user.uid) sessionRequest("release")
        verifiedSessionUid = null
        FirebaseAuth.getInstance().signOut()
    }

    suspend fun logout() = sessionMutex.withLock {
        val user = FirebaseAuth.getInstance().currentUser
        if (user?.email?.endsWith("@usuarios.nrdlojas.com") == true) sessionRequest("release")
        verifiedSessionUid = null
        FirebaseAuth.getInstance().signOut()
    }

    private suspend fun sessionRequest(action: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Entre novamente.")
        val token = user.getIdToken(true).await().token ?: error("Entre novamente.")
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("action", action).put("deviceToken", deviceToken())
            val request = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-account-admin/session")
                .header("x-firebase-token", token).header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            accountClient.newCall(request).execute().use { response ->
                val result = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                check(response.isSuccessful && result?.optBoolean(if (action == "claim") "claimed" else "released") == true) {
                    if (result?.optString("error") == "DEVICE_IN_USE") "Esta conta está conectada em outro aparelho. Saia da conta no outro aparelho para entrar aqui."
                    else if (action == "release") "Não foi possível sair da conta. Conecte-se à internet e tente novamente para liberar o outro aparelho."
                    else "Não foi possível verificar o aparelho. Verifique a conexão e tente novamente."
                }
            }
        }
    }

    fun observeAccounts() = flow {
        while (true) {
            try {
                val items = PromotionControlClient.request(JSONObject().put("operation", "list")).getJSONArray("items")
                emit(AccessHistory(loading = false, accounts = (0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    AccessAccount(item.getString("uid"), item.optString("login"), item.optBoolean("enabled"), item.optBoolean("profile"), item.optBoolean("promotions"), item.optBoolean("prices"))
                }.sortedBy { it.login }))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emit(AccessHistory(loading = false, error = "Não foi possível carregar os cadastros. Verifique a conexão.")) }
            delay(30_000)
        }
    }

    suspend fun updatePermissions(uid: String, profile: Boolean, promotions: Boolean, prices: Boolean) {
        check(FirebaseAuth.getInstance().currentUser?.email == "mestre@nrdlojas.com") { "Acesso exclusivo do Mestre." }
        PromotionControlClient.write("restricted_access/$uid", mapOf("enabled" to (profile || promotions || prices), "profile" to profile, "promotions" to promotions, "prices" to prices), merge = true)
    }

    suspend fun updateAccount(uid: String, login: String, password: String) {
        val email = loginEmail(login)
        require(!email.endsWith("@nrdlojas.com")) { "Esse login está reservado." }
        require(password.isEmpty() || password.length in 8..128) { "A nova senha deve ter de 8 a 128 caracteres." }
        val user = FirebaseAuth.getInstance().currentUser
        check(user?.email == "mestre@nrdlojas.com") { "Acesso exclusivo do Mestre." }
        val token = user!!.getIdToken(true).await().token ?: error("Entre novamente como Mestre.")
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("action", "update").put("uid", uid).put("login", email.substringBefore('@'))
            if (password.isNotEmpty()) body.put("password", password)
            val request = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-account-admin")
                .header("x-firebase-token", token).header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            accountClient.newCall(request).execute().use { response ->
                val result = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                check(response.isSuccessful && result?.optBoolean("updated") == true) {
                    when (result?.optString("error")) {
                        "LOGIN_EXISTS" -> "Esse login já está em uso. Escolha outro."
                        "UPDATE_INCOMPLETE" -> "Os dados de acesso foram alterados, mas o histórico não foi atualizado. Salve novamente com o mesmo login."
                        "ACCOUNT_NOT_FOUND" -> "Cadastro não encontrado. Atualize a lista."
                        else -> "Não foi possível alterar os dados. Verifique a conexão e tente novamente."
                    }
                }
            }
        }
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
        var job: kotlinx.coroutines.Job? = null
        val listener = FirebaseAuth.AuthStateListener {
            if (it.currentUser == null) verifiedSessionUid = null
            job?.cancel()
            job = launch {
                while (true) {
                    trySend(current())
                    delay(30_000)
                }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { job?.cancel(); auth.removeAuthStateListener(listener) }
    }
    suspend fun setPublicAccess(enabled: Boolean) {
        check(FirebaseAuth.getInstance().currentUser?.email == "mestre@nrdlojas.com") { "Acesso exclusivo do Mestre." }
        PromotionControlClient.write("config/restricted_access", mapOf("publicAccess" to enabled))
    }
    suspend fun current(): RestrictedAccess {
        if (FirebaseAuth.getInstance().currentUser?.email == "mestre@nrdlojas.com") return RestrictedAccess(enabled = true, profile = true, promotions = true, prices = true)
        return try {
            val data = PromotionControlClient.request(JSONObject().put("operation", "access"))
            RestrictedAccess(enabled = data.optBoolean("enabled"), profile = data.optBoolean("profile"), promotions = data.optBoolean("promotions"), prices = data.optBoolean("prices"), publicAccess = data.optBoolean("publicAccess"), publicLoading = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { RestrictedAccess(publicLoading = false) }
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
                PromotionControlClient.write("restricted_access/${user.uid}", mapOf(
                    "login" to login.trim().lowercase(Locale.ROOT), "enabled" to true,
                    "profile" to profile, "promotions" to promotions, "prices" to prices
                ))
            } catch (error: Exception) { runCatching { user.delete().await() }; throw error }
        } finally { auth.signOut(); secondary.delete() }
    }
}
