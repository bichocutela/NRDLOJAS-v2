package com.example.data

import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class IntegrationLoginStatus(val provider: String, val configured: Boolean, val revision: Long, val updatedAt: String?)

internal object IntegrationLoginsRepository {
    private val client = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).followRedirects(false).build()
    private suspend fun request(body: JSONObject): JSONObject {
        val user = FirebaseAuth.getInstance().currentUser
        require(user?.email == "mestre@nrdlojas.com") { "Entre como Mestre." }
        val token = user!!.getIdToken(false).await().token ?: error("Entre novamente como Mestre.")
        return withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/nrd-integration-logins")
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY).header("x-firebase-token", token)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { response ->
                val result = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                if (!response.isSuccessful) error(when (result?.optString("error")) {
                    "ACCESS_DENIED" -> "Entre novamente como Mestre para gerenciar os logins."
                    "LOGIN_TEST_FAILED" -> "O teste falhou. Confira os dados e a disponibilidade do serviço. O acesso atual foi mantido."
                    "INVALID_CREDENTIALS" -> "Preencha o login e a senha completos."
                    "CREDENTIAL_CONFLICT" -> "Este acesso foi alterado em outro aparelho. Atualize a lista antes de salvar."
                    "LOGIN_IN_USE" -> "Há uma consulta de Ofertas em andamento. Aguarde alguns instantes e tente salvar novamente."
                    "LOGIN_RECOVERY_PENDING" -> "A preferência da conta anterior precisa ser restaurada antes de trocar para outra conta. Você ainda pode corrigir a senha da mesma conta."
                    else -> "Não foi possível confirmar a operação. Atualize a lista antes de tentar novamente."
                })
                result ?: error("Não foi possível confirmar a resposta do servidor. Atualize a lista.")
            }
        }
    }
    suspend fun list(): List<IntegrationLoginStatus> {
        val rows = request(JSONObject().put("operation", "list")).getJSONArray("providers")
        return (0 until rows.length()).map { index -> rows.getJSONObject(index).let {
            IntegrationLoginStatus(it.getString("provider"), it.getBoolean("configured"), it.getLong("revision"),
                if (it.isNull("updatedAt")) null else it.optString("updatedAt").takeIf(String::isNotBlank))
        } }
    }
    suspend fun test(provider: String) { request(JSONObject().put("operation", "test").put("provider", provider).put("current", true)) }
    suspend fun save(provider: String, login: String, password: String, revision: Long) {
        request(JSONObject().put("operation", "save").put("provider", provider)
            .put("login", login.trim()).put("password", password).put("revision", revision))
    }
}
