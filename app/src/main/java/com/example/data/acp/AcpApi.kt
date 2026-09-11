package com.example.data.acp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class AcpUnauthorized : IOException("Confirme novamente seu acesso à ACP.")
internal class AcpFailure(message: String) : IOException(message)

/** Isolated NextAuth session; never changes NRD/Firebase/Nossa Gente authentication. */
internal class AcpApi(private val store: AcpStorage, clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()) {
    constructor(context: Context) : this(AcpSecureStore(context))
    private val cookies = AcpCookieJar(store.read("session")) { store.write("session", it) }
    private val client = clientBuilder
        .cookieJar(cookies)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS).build()

    suspend fun hasCredentials(): Boolean = withContext(Dispatchers.IO) { credentials() != null }

    suspend fun configure(login: String, password: String) = withContext(Dispatchers.IO) {
        require(login.isNotBlank() && password.isNotBlank())
        store.write("access", JSONObject().put("login", login.trim()).put("password", password).toString())
        cookies.clear()
    }

    suspend fun confirmAccess() = withContext(Dispatchers.IO) {
        try {
            session()
        } catch (_: AcpUnauthorized) {
            val access = credentials() ?: throw AcpFailure("Peça ao administrador para configurar o acesso neste aparelho.")
            cookies.clear()
            val csrf = requestJson(Request.Builder().url("$ORIGIN/api/auth/csrf").get().build())
                .nonBlankString("csrfToken")
                ?: throw AcpFailure("A ACP não disponibilizou o formulário de acesso. Tente novamente.")
            val form = FormBody.Builder()
                .add("login", access.getString("login"))
                .add("password", access.getString("password"))
                .add("csrfToken", csrf).add("callbackUrl", "$ORIGIN/print-template")
                .add("json", "true").build()
            val result = requestJson(Request.Builder().url("$ORIGIN/api/auth/callback/credentials")
                .header("Origin", ORIGIN).header("Referer", "$ORIGIN/login").post(form).build())
            val error = result.nonBlankString("error") ?: result.nonBlankString("url")?.let {
                ORIGIN.toHttpUrl().resolve(it)?.queryParameter("error")
            }
            if (!error.isNullOrBlank()) {
                cookies.clear()
                throw AcpFailure("A ACP não aceitou o acesso. Peça ao administrador para conferir a configuração.")
            }
            session() // A successful callback alone is not proof of authentication.
        }
    }

    private fun credentials(): JSONObject? = store.read("access")?.let {
        runCatching { JSONObject(it) }.getOrNull()
    }?.takeIf { it.nonBlankString("login") != null && it.nonBlankString("password") != null }

    private fun session(): JSONObject {
        val data = requestJson(Request.Builder().url("$ORIGIN/api/auth/session").get().build())
        val user = data.optJSONObject("user")
        if (user == null || user.nonBlankString("accessToken") == null) {
            cookies.clear()
            throw AcpUnauthorized()
        }
        return user
    }

    /** Read-only endpoints. Bearer is obtained from the existing ACP session, never logged. */
    internal suspend fun get(path: String, parameters: List<Pair<String, String>>): JSONObject = withContext(Dispatchers.IO) {
        require(path in setOf("Product/all", "ProductCategory/all"))
        val session = session()
        val base = if (session.optBoolean("proxyEnable")) "$ORIGIN/api/proxy/api/v1/" else "$API_ORIGIN/api/v1/"
        val url = (base + path).toHttpUrl().newBuilder().apply {
            parameters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        requestJson(Request.Builder().url(url).header("Authorization", "Bearer ${session.getString("accessToken")}")
            .header("Accept", "application/json").get().build())
    }

    private fun requestJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || (response.code in 300..399 &&
                    response.header("Location").orEmpty().contains("/login"))) {
                cookies.clear()
                if (request.url.encodedPath == "/api/auth/callback/credentials") {
                    throw AcpFailure("A ACP não aceitou o acesso. Peça ao administrador para conferir a configuração.")
                }
                throw AcpUnauthorized()
            }
            if (response.code == 403) throw AcpFailure("A ACP não autorizou esta consulta.")
            if (response.code == 429) throw AcpFailure("Muitas consultas. Aguarde um momento e tente novamente.")
            if (!response.isSuccessful) throw AcpFailure("Não foi possível acessar a ACP agora (${response.code}).")
            val raw = response.body?.string() ?: throw AcpFailure("A ACP retornou uma resposta vazia.")
            return try { JSONObject(raw) } catch (_: Exception) {
                throw AcpFailure("A resposta da ACP não é compatível com esta consulta.")
            }
        }
    }

    private fun JSONObject.nonBlankString(name: String): String? =
        (opt(name) as? String)?.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        const val ORIGIN = "https://nordestao12.acp.app.br"
        const val API_ORIGIN = "https://api.acp.app.br"
    }
}

/** ACP cookies are sent only to this tenant. API requests use Bearer, not tenant cookies. */
internal class AcpCookieJar(saved: String?, private val persist: (String) -> Unit) : CookieJar {
    private val origin = AcpApi.ORIGIN.toHttpUrl()
    private val values = mutableListOf<Cookie>()

    init {
        runCatching {
            val array = JSONArray(saved ?: "[]")
            for (index in 0 until array.length()) Cookie.parse(origin, array.getString(index))?.let { values.add(it) }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host != origin.host || !url.isHttps) return
        cookies.forEach { cookie ->
            values.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) values.add(cookie)
        }
        persist(JSONArray(values.map { it.toString() }).toString())
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (url.host != origin.host || !url.isHttps) return emptyList()
        return values.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
    }

    @Synchronized
    fun clear() { values.clear(); persist("[]") }
}
