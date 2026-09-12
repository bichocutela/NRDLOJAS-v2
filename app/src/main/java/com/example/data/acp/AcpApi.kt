package com.example.data.acp

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.security.MessageDigest
import java.text.Normalizer
import java.util.concurrent.TimeUnit

internal class AcpUnauthorized : IOException("Confirme novamente seu acesso à ACP.")
internal class AcpFailure(message: String) : IOException(message)

/** Isolated NextAuth session; never changes NRD/Firebase/Nossa Gente authentication. */
internal class AcpApi(private val store: AcpStorage, clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder(),
    private val bundledLogin: String = "", private val bundledPassword: String = "") {
    constructor(context: Context) : this(AcpSecureStore(context), bundledLogin = BuildConfig.ACP_LOGIN, bundledPassword = BuildConfig.ACP_PASSWORD)
    private val sessionLock = Mutex()
    private var accessConfirmed = false
    private var clubCategoryId: String? = null
    private var clubCategoryResolved = false
    private val cookies = AcpCookieJar(store.read("session")) { store.write("session", it) }
    private val client = clientBuilder
        .cookieJar(cookies)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS).build()

    private val diagnostics = LinkedHashMap<String, JSONObject>()
    private val diagnosticRequests = mutableListOf<JSONObject>()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshingKeys = mutableSetOf<String>()

    suspend fun hasCredentials(): Boolean = withContext(Dispatchers.IO) { credentials() != null }

    suspend fun configure(login: String, password: String) = sessionLock.withLock {
        withContext(Dispatchers.IO) {
            require(login.isNotBlank() && password.isNotBlank())
            store.write("access", JSONObject().put("login", login.trim()).put("password", password).toString())
            accessConfirmed = false
            clubCategoryId = null
            clubCategoryResolved = false
            cookies.clear()
        }
    }

    /** Restore only an existing server session; never submit credentials on initial screen entry. */
    suspend fun restoreSession(): Boolean = sessionLock.withLock {
        withContext(Dispatchers.IO) {
            accessConfirmed = false
            if (cookies.loadForRequest("$ORIGIN/api/auth/session".toHttpUrl()).isEmpty()) return@withContext false
            try {
                session()
                accessConfirmed = true
                true
            } catch (_: AcpUnauthorized) { false }
        }
    }

    suspend fun confirmAccess() = sessionLock.withLock {
        withContext(Dispatchers.IO) {
            accessConfirmed = false
            try { session() } catch (_: AcpUnauthorized) { signIn() }
            accessConfirmed = true
        }
    }

    private fun signIn() {
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

    fun hasBundledAccess(): Boolean = bundledLogin.isNotBlank() && bundledPassword.isNotBlank()

    private fun credentials(): JSONObject? {
        if (hasBundledAccess()) return JSONObject().put("login", bundledLogin).put("password", bundledPassword)
        return store.read("access")?.let {
            runCatching { JSONObject(it) }.getOrNull()
        }?.takeIf { it.nonBlankString("login") != null && it.nonBlankString("password") != null }
    }

    private fun session(): JSONObject {
        val data = requestJson(Request.Builder().url("$ORIGIN/api/auth/session").get().build())
        val user = data.optJSONObject("user")
        if (user == null || user.nonBlankString("accessToken") == null) {
            cookies.clear()
            throw AcpUnauthorized()
        }
        return user
    }

    /**
     * Read-only endpoints. Product and promotion responses use a 24h device cache.
     * Fresh cache is returned immediately. Stale cache remains usable while a silent
     * refresh runs in the background, so the product screen never waits for the daily sync.
     *
     * The Clube carousel has one intentionally recognizable catalog request: Product/all
     * without search filters and pageSize=100. Instead of walking the complete store catalog,
     * that request is narrowed server-side to the ACP category "Clube de Vantagens". This
     * keeps the same source of truth (Product/all / clubValue) while avoiding hundreds of
     * irrelevant pages and the endless loading state seen on the consultation screen.
     */
    internal suspend fun get(path: String, parameters: List<Pair<String, String>>): JSONObject {
        require(path in READ_ONLY_ENDPOINTS) { "ACP endpoint not allowed: $path" }

        val clubCatalogRequest = isClubCatalogRequest(path, parameters)
        val effectiveParameters = if (clubCatalogRequest) {
            val clubId = resolveClubCategoryId()
                ?: throw AcpFailure("A categoria Clube de Vantagens não foi localizada na ACP. Tente sincronizar novamente.")
            parameters
                .filterNot { it.first == "pageSize" || it.first == "productCategoryIds" }
                .plus("pageSize" to CLUB_PAGE_SIZE.toString())
                .plus("productCategoryIds" to clubId)
        } else {
            parameters
        }

        // A listagem do Clube precisa refletir a operação atual do caixa. Para ela não servimos
        // uma fotografia de 24h: buscamos a categoria diretamente na ACP e só então atualizamos
        // o cache. As pesquisas comuns continuam com o comportamento de cache existente.
        if (clubCatalogRequest) {
            val result = authenticatedRead(path, effectiveParameters, record = true)
            withContext(Dispatchers.IO) { writeCachedResponse(path, effectiveParameters, result) }
            return result
        }

        if (path in DAILY_CACHE_ENDPOINTS) {
            val cached = withContext(Dispatchers.IO) { readCachedResponse(path, effectiveParameters) }
            if (cached != null) {
                recordDiagnostic(path, effectiveParameters, cached.payload)
                if (!isFresh(cached.savedAtMillis)) {
                    if (path in SILENT_PAGED_ENDPOINTS && pageIndex(effectiveParameters) == 0) {
                        schedulePagedRefresh(path, effectiveParameters)
                    } else {
                        scheduleRefresh(path, effectiveParameters)
                    }
                }
                return cached.payload
            }

            if (path in SILENT_PAGED_ENDPOINTS) {
                if (pageIndex(effectiveParameters) == 0) schedulePagedRefresh(path, effectiveParameters)
                else scheduleRefresh(path, effectiveParameters)
                return emptyPagedResponse(effectiveParameters).also { recordDiagnostic(path, effectiveParameters, it) }
            }
        }

        val result = authenticatedRead(path, effectiveParameters, record = true)
        if (path in DAILY_CACHE_ENDPOINTS) {
            withContext(Dispatchers.IO) { writeCachedResponse(path, effectiveParameters, result) }
        }
        return result
    }

    /** Only the dedicated Clube loader currently performs this exact unfiltered batch request. */
    private fun isClubCatalogRequest(path: String, parameters: List<Pair<String, String>>): Boolean {
        if (path != "Product/all") return false
        if (parameters.lastOrNull { it.first == "pageSize" }?.second != "100") return false
        val filterNames = setOf("code", "barCode", "description", "productCategoryIds")
        return parameters.none { it.first in filterNames }
    }

    /**
     * Resolve the ACP category once per authenticated API instance. ProductCategory/all is
     * deliberately read from the server here because the normal category helper is allowed
     * to warm its cache silently and can legitimately return an empty first response.
     */
    private suspend fun resolveClubCategoryId(): String? {
        if (clubCategoryResolved) return clubCategoryId

        var page = 0
        var totalPages = 1
        while (page < totalPages && page < MAX_CLUB_CATEGORY_PAGES) {
            val parameters = listOf("pageSize" to "100", "pageIndex" to page.toString())
            val root = authenticatedRead("ProductCategory/all", parameters, record = false)
            val items = root.optJSONArray("items") ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val description = item.optString("description").trim()
                val normalized = normalizeCategoryName(description)
                if (normalized == "clubedevantagens" || normalized == "clubvantagens") {
                    val id = item.opt("id")?.toString()?.trim().orEmpty()
                    if (id.isNotEmpty() && id != "null") {
                        clubCategoryId = id
                        clubCategoryResolved = true
                        return id
                    }
                }
            }
            totalPages = responseTotalPages(root).coerceAtLeast(1)
            page++
        }

        clubCategoryResolved = true
        clubCategoryId = null
        return null
    }

    private fun normalizeCategoryName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")

    private suspend fun authenticatedRead(path: String, parameters: List<Pair<String, String>>, record: Boolean): JSONObject =
        sessionLock.withLock {
            withContext(Dispatchers.IO) {
                try { readOnce(path, parameters, record) } catch (expired: AcpUnauthorized) {
                    val mayRenew = accessConfirmed
                    accessConfirmed = false
                    if (!mayRenew) throw expired
                    // One normal sign-in and one GET retry. Forbidden/rate-limit/network errors never submit a password.
                    signIn()
                    val result = readOnce(path, parameters, record)
                    accessConfirmed = true
                    result
                }
            }
        }

    private fun readOnce(path: String, parameters: List<Pair<String, String>>, record: Boolean = true): JSONObject {
        val session = session()
        val base = if (session.optBoolean("proxyEnable")) "$ORIGIN/api/proxy/api/v1/" else "$API_ORIGIN/api/v1/"
        val url = (base + path).toHttpUrl().newBuilder().apply {
            parameters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val result = requestJson(Request.Builder().url(url).header("Authorization", "Bearer ${session.getString("accessToken")}")
            .header("Cache-Control", "no-cache").header("Accept", "application/json").get().build())
        if (record) recordDiagnostic(path, parameters, result)
        return result
    }

    private data class CachedResponse(val savedAtMillis: Long, val payload: JSONObject)

    private fun readCachedResponse(path: String, parameters: List<Pair<String, String>>): CachedResponse? {
        val raw = store.read(cacheName(path, parameters)) ?: return null
        return try {
            val envelope = JSONObject(raw)
            val savedAt = envelope.optLong("savedAtMillis", 0L)
            val payload = envelope.optJSONObject("payload") ?: return null
            if (savedAt <= 0L) null else CachedResponse(savedAt, payload)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCachedResponse(path: String, parameters: List<Pair<String, String>>, payload: JSONObject) {
        try {
            val envelope = JSONObject()
                .put("savedAtMillis", System.currentTimeMillis())
                .put("payload", payload)
            store.write(cacheName(path, parameters), envelope.toString())
        } catch (_: Exception) {
            // Cache failures must never break the ACP consultation.
        }
    }

    private fun cacheName(path: String, parameters: List<Pair<String, String>>): String {
        val rawKey = buildString {
            append(path)
            parameters.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }).forEach { (key, value) ->
                append('|').append(key).append('=').append(value)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "response_cache_$hex"
    }

    private fun isFresh(savedAtMillis: Long): Boolean {
        val age = System.currentTimeMillis() - savedAtMillis
        return age >= 0L && age < DAILY_CACHE_TTL_MILLIS
    }

    private fun scheduleRefresh(path: String, parameters: List<Pair<String, String>>) {
        val key = cacheName(path, parameters)
        synchronized(refreshingKeys) { if (!refreshingKeys.add(key)) return }
        backgroundScope.launch {
            try {
                delay(SILENT_REFRESH_DELAY_MILLIS)
                val fresh = authenticatedRead(path, parameters, record = false)
                writeCachedResponse(path, parameters, fresh)
            } catch (_: Exception) {
                // Keep serving the last known value and try again on a later consultation.
            } finally {
                synchronized(refreshingKeys) { refreshingKeys.remove(key) }
            }
        }
    }

    private fun schedulePagedRefresh(path: String, initialParameters: List<Pair<String, String>>) {
        val baseParameters = initialParameters.filterNot { it.first == "pageIndex" }
        val key = "paged:${cacheName(path, baseParameters)}"
        synchronized(refreshingKeys) { if (!refreshingKeys.add(key)) return }
        backgroundScope.launch {
            try {
                delay(SILENT_REFRESH_DELAY_MILLIS)
                var page = 0
                var pageCount = 1
                while (page < pageCount && page < MAX_SILENT_PAGES) {
                    val parameters = baseParameters + ("pageIndex" to page.toString())
                    val fresh = authenticatedRead(path, parameters, record = false)
                    writeCachedResponse(path, parameters, fresh)
                    pageCount = responseTotalPages(fresh).coerceAtLeast(1)
                    page++
                }
            } catch (_: Exception) {
                // Categories and campaigns are optional helpers. General product search stays available.
            } finally {
                synchronized(refreshingKeys) { refreshingKeys.remove(key) }
            }
        }
    }

    private fun pageIndex(parameters: List<Pair<String, String>>): Int =
        parameters.lastOrNull { it.first == "pageIndex" }?.second?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    private fun responseTotalPages(root: JSONObject): Int {
        val data = root.optJSONObject("data")
        return root.optInt("totalPages", data?.optInt("totalPages", 1) ?: 1)
    }

    private fun emptyPagedResponse(parameters: List<Pair<String, String>>): JSONObject = JSONObject()
        .put("items", JSONArray())
        .put("pageIndex", pageIndex(parameters))
        .put("totalPages", 1)
        .put("totalCount", 0)

    @Synchronized internal fun beginDiagnosticSession() {
        diagnostics.clear()
        diagnosticRequests.clear()
    }

    @Synchronized private fun recordDiagnostic(path: String, parameters: List<Pair<String, String>>, value: JSONObject) {
        val sanitizedResponse = sanitize(value) as JSONObject
        diagnostics[path] = sanitizedResponse
        val sanitizedParameters = JSONArray().also { array ->
            parameters.forEach { (key, parameterValue) ->
                array.put(JSONObject().put("name", key).put("value", if (isSensitiveKey(key)) "[REDACTED]" else parameterValue))
            }
        }
        diagnosticRequests += JSONObject()
            .put("endpoint", path)
            .put("parameters", sanitizedParameters)
            .put("response", sanitizedResponse)
        while (diagnosticRequests.size > MAX_DIAGNOSTIC_REQUESTS) diagnosticRequests.removeAt(0)
    }

    @Synchronized internal fun diagnosticText(): String? {
        if (diagnostics.isEmpty() && diagnosticRequests.isEmpty()) return null
        val payload = JSONObject()
            .put("diagnostic", "NRD ACP read-only response capture")
            .put("endpoints", JSONObject())
            .put("requests", JSONArray())
        val endpoints = payload.getJSONObject("endpoints")
        diagnostics.forEach { (path, json) -> endpoints.put(path, json) }
        val requests = payload.getJSONArray("requests")
        diagnosticRequests.forEach { requests.put(it) }
        return payload.toString(2)
    }

    private fun sanitize(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().also { clean ->
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                clean.put(key, if (isSensitiveKey(key)) "[REDACTED]" else sanitize(value.opt(key)))
            }
        }
        is JSONArray -> JSONArray().also { clean -> for (index in 0 until value.length()) clean.put(sanitize(value.opt(index))) }
        else -> value
    }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().replace("_", "").replace("-", "")
        return normalized in setOf("password", "passwd", "accesstoken", "refreshtoken", "token", "authorization", "cookie", "setcookie", "csrftoken", "secret")
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
        private const val MAX_DIAGNOSTIC_REQUESTS = 12
        private const val DAILY_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L
        private const val SILENT_REFRESH_DELAY_MILLIS = 900L
        private const val MAX_SILENT_PAGES = 30
        private const val MAX_CLUB_CATEGORY_PAGES = 10
        private const val CLUB_PAGE_SIZE = 250
        private val READ_ONLY_ENDPOINTS = setOf("Product/all", "ProductCategory/all", "Product/integrationInfo", "Campaign/all")
        private val DAILY_CACHE_ENDPOINTS = setOf("Product/all", "ProductCategory/all", "Campaign/all")
        private val SILENT_PAGED_ENDPOINTS = setOf("ProductCategory/all", "Campaign/all")
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
