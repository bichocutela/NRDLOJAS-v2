package com.example.data

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Cliente mínimo para autenticar e consultar promoções da Nossa Gente.
 *  A senha é usada somente na requisição de login e nunca é persistida.
 */
class NossaGenteApi(context: Context) {
    private companion object {
        const val MAX_PROFILE_PHOTO_BYTES = 8 * 1024 * 1024L
    }

    @Volatile
    private var inMemoryToken: String? = null
    private val secureSession = NossaGenteSecureSession(context)
    private val credentialStore = NossaGenteCredentialStore(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val profilePhotoCache = ConcurrentHashMap<String, ByteArray>()

    fun hasSession(): Boolean = !currentToken().isNullOrBlank()

    suspend fun login(cpf: String, password: String): NossaGenteLoginResult = withContext(Dispatchers.IO) {
        val cleanCpf = cpf.filter(Char::isDigit)
        if (cleanCpf.length != 11 || password.isBlank()) {
            return@withContext NossaGenteLoginResult.Error("Informe um CPF válido e sua senha.")
        }

        try {
            val payload = JSONObject()
                .put("cpf", cleanCpf)
                .put("senha", password)
                .toString()
            val request = Request.Builder()
                .url("${BuildConfig.NOSSA_GENTE_API_BASE_URL}/auth/login")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext NossaGenteLoginResult.Error(loginErrorMessage(response.code, body))
                }
                val json = JSONObject(body)
                val token = firstNonBlank(
                    json.optString("token"),
                    json.optString("access_token"),
                    json.optJSONObject("data")?.optString("token"),
                    json.optJSONObject("user")?.optString("token")
                )
                if (token.isNullOrBlank()) {
                    return@withContext NossaGenteLoginResult.Error("A resposta de autenticação não trouxe uma sessão válida.")
                }
                inMemoryToken = token
                secureSession.saveToken(token)
                NossaGenteLoginResult.Success
            }
        } catch (_: Exception) {
            NossaGenteLoginResult.Error("Não foi possível conectar ao Nossa Gente. Tente novamente.")
        }
    }

    suspend fun fetchPromotions(): NossaGentePromotionsResult = withContext(Dispatchers.IO) {
        fetchPromotionsOnce(allowSavedCredentialRecovery = true)
    }

    /** Endpoint confirmado no APK oficial do Nossa Gente: GET /ponto?limit=. */
    suspend fun fetchPoint(limit: Int = 100): NossaGentePointResult = withContext(Dispatchers.IO) {
        fetchPointOnce(limit.coerceIn(1, 100), allowSavedCredentialRecovery = true)
    }

    /** Perfil autenticado do colaborador. O APK oficial usa GET /me, /tempo-casa e /me/foto. */
    suspend fun fetchEmployeeProfile(): NossaGenteProfileResult = withContext(Dispatchers.IO) {
        fetchEmployeeProfileOnce(allowSavedCredentialRecovery = true)
    }

    private suspend fun fetchEmployeeProfileOnce(allowSavedCredentialRecovery: Boolean): NossaGenteProfileResult {
        val token = currentToken() ?: return NossaGenteProfileResult.Unauthorized
        return try {
            val meResponse = authenticatedGet("/me", token)
            if (meResponse.code == 401 || meResponse.code == 403) {
                if (allowSavedCredentialRecovery && renewFromSavedCredentials()) {
                    return fetchEmployeeProfileOnce(allowSavedCredentialRecovery = false)
                }
                return NossaGenteProfileResult.Unauthorized
            }
            if (!meResponse.successful) {
                return NossaGenteProfileResult.Error("Não foi possível carregar os dados do perfil agora.")
            }

            var profile = parseEmployeeProfile(meResponse.body)
            if (profile.name.isNullOrBlank() && profile.admissionDate.isNullOrBlank()) {
                return NossaGenteProfileResult.Error("O Nossa Gente não retornou os dados do colaborador.")
            }

            runCatching {
                val tenureResponse = authenticatedGet("/tempo-casa?limit=100&page=1", token)
                if (tenureResponse.successful) {
                    profile = mergeTenureProfile(profile, tenureResponse.body)
                }
            }

            profile = profile.withComputedTenure()
            NossaGenteProfileResult.Success(profile)
        } catch (_: Exception) {
            NossaGenteProfileResult.Error("Não foi possível carregar os dados do perfil. Verifique a internet.")
        }
    }

    /**
     * Foto autenticada do colaborador.
     *
     * O APK oficial do Nossa Gente não depende de uma URL dentro do GET /me:
     * ele possui o endpoint dedicado GET /me/foto. Por isso este método consulta
     * primeiro esse endpoint e usa a URL do perfil apenas como compatibilidade.
     */
    suspend fun fetchProfilePhoto(fallbackPhotoUrl: String? = null): ByteArray? = withContext(Dispatchers.IO) {
        fetchProfilePhotoOnce(
            fallbackPhotoUrl = fallbackPhotoUrl,
            allowSavedCredentialRecovery = true
        )
    }

    private suspend fun fetchProfilePhotoOnce(
        fallbackPhotoUrl: String?,
        allowSavedCredentialRecovery: Boolean
    ): ByteArray? {
        val token = currentToken() ?: return null
        val authenticatedCacheKey = "me:" + tokenCacheFingerprint(token)
        profilePhotoCache[authenticatedCacheKey]?.let { return it }

        val endpointResult = runCatching {
            val request = Request.Builder()
                .url("${BuildConfig.NOSSA_GENTE_API_BASE_URL}/me/foto")
                .get()
                .header("Accept", "image/*, application/json;q=0.9, */*;q=0.8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cache-Control", "no-cache, no-store")
                .header("Pragma", "no-cache")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> ProfilePhotoEndpointResult.Unauthorized
                    response.code == 204 || response.code == 404 -> ProfilePhotoEndpointResult.NoPhoto
                    !response.isSuccessful -> ProfilePhotoEndpointResult.NoPhoto
                    else -> {
                        val body = response.body ?: return@use ProfilePhotoEndpointResult.NoPhoto
                        val contentLength = body.contentLength()
                        if (contentLength > MAX_PROFILE_PHOTO_BYTES) {
                            return@use ProfilePhotoEndpointResult.NoPhoto
                        }
                        val mediaType = body.contentType()?.toString().orEmpty()
                        val bytes = body.bytes()
                        if (bytes.isEmpty() || bytes.size.toLong() > MAX_PROFILE_PHOTO_BYTES) {
                            return@use ProfilePhotoEndpointResult.NoPhoto
                        }
                        when {
                            mediaType.startsWith("image/", ignoreCase = true) || looksLikeImageBytes(bytes) -> {
                                ProfilePhotoEndpointResult.Photo(bytes)
                            }
                            else -> {
                                val rawPayload = bytes.toString(Charsets.UTF_8)
                                val embedded = parseProfilePhotoBytes(rawPayload)
                                if (embedded != null) {
                                    ProfilePhotoEndpointResult.Photo(embedded)
                                } else {
                                    val reference = parseProfilePhotoReference(rawPayload)
                                    val downloaded = downloadProfilePhotoReference(reference, token)
                                    if (downloaded != null) ProfilePhotoEndpointResult.Photo(downloaded)
                                    else ProfilePhotoEndpointResult.NoPhoto
                                }
                            }
                        }
                    }
                }
            }
        }.getOrElse { ProfilePhotoEndpointResult.NoPhoto }

        when (endpointResult) {
            ProfilePhotoEndpointResult.Unauthorized -> {
                if (allowSavedCredentialRecovery && renewFromSavedCredentials()) {
                    return fetchProfilePhotoOnce(
                        fallbackPhotoUrl = fallbackPhotoUrl,
                        allowSavedCredentialRecovery = false
                    )
                }
                return null
            }
            is ProfilePhotoEndpointResult.Photo -> {
                profilePhotoCache[authenticatedCacheKey] = endpointResult.bytes
                return endpointResult.bytes
            }
            ProfilePhotoEndpointResult.NoPhoto -> Unit
        }

        val fallbackBytes = downloadProfilePhotoReference(fallbackPhotoUrl, token)
        if (fallbackBytes != null) {
            profilePhotoCache[authenticatedCacheKey] = fallbackBytes
        }
        return fallbackBytes
    }

    private fun downloadProfilePhotoReference(reference: String?, token: String): ByteArray? {
        val normalizedUrl = normalizeProfilePhotoUrl(reference) ?: return null
        if (normalizedUrl.startsWith("data:image/", ignoreCase = true)) {
            val encoded = normalizedUrl.substringAfter("base64,", missingDelimiterValue = "")
            if (encoded.isBlank()) return null
            return runCatching {
                Base64.decode(encoded, Base64.DEFAULT)
            }.getOrNull()?.takeIf { it.isNotEmpty() && it.size.toLong() <= MAX_PROFILE_PHOTO_BYTES }
        }

        val request = Request.Builder()
            .url(normalizedUrl)
            .get()
            .header("Accept", "image/*")
            .header("Cache-Control", "no-cache")
            .header("Authorization", "Bearer $token")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val contentLength = body.contentLength()
                if (contentLength > MAX_PROFILE_PHOTO_BYTES) return@use null
                val mediaType = body.contentType()?.toString().orEmpty()
                val bytes = body.bytes()
                bytes.takeIf {
                    it.isNotEmpty() &&
                        it.size.toLong() <= MAX_PROFILE_PHOTO_BYTES &&
                        (mediaType.startsWith("image/", ignoreCase = true) || looksLikeImageBytes(it))
                }
            }
        }.getOrNull()
    }

    private fun looksLikeImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        val jpeg = b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF
        val png = b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47
        val gif = b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38
        val webp = bytes.size >= 12 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        return jpeg || png || gif || webp
    }

    private fun parseProfilePhotoBytes(raw: String): ByteArray? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        fun decodeCandidate(candidate: String?): ByteArray? {
            val value = candidate?.trim()?.trim('"')?.takeIf { it.isNotBlank() } ?: return null
            val encoded = when {
                value.startsWith("data:image/", ignoreCase = true) ->
                    value.substringAfter("base64,", missingDelimiterValue = "")
                value.length >= 64 && value.matches(Regex("""^[A-Za-z0-9+/=_\\r\\n-]+$""")) -> value
                else -> return null
            }
            if (encoded.isBlank()) return null
            return runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()?.takeIf { bytes ->
                bytes.isNotEmpty() && bytes.size.toLong() <= MAX_PROFILE_PHOTO_BYTES && looksLikeImageBytes(bytes)
            }
        }

        decodeCandidate(trimmed)?.let { return it }
        val parsed = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull() ?: return null
        fun find(value: Any?, depth: Int): ByteArray? {
            if (depth > 6 || value == null || value == JSONObject.NULL) return null
            return when (value) {
                is String -> decodeCandidate(value)
                is JSONObject -> {
                    val keys = arrayOf("foto", "fotoBase64", "foto_base64", "base64", "photo", "photoBase64", "photo_base64", "avatar", "imagem", "image", "data", "resultado", "result", "payload")
                    keys.firstNotNullOfOrNull { key -> if (value.has(key)) find(value.opt(key), depth + 1) else null }
                }
                is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { index -> find(value.opt(index), depth + 1) }
                else -> null
            }
        }
        return find(parsed, 0)
    }

    private fun parseProfilePhotoReference(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (
            trimmed.startsWith("data:image/", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("//") ||
            trimmed.startsWith("/")
        ) {
            return trimmed.trim('"')
        }

        val parsed = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull() ?: return null
        fun find(value: Any?, depth: Int): String? {
            if (depth > 5 || value == null || value == JSONObject.NULL) return null
            return when (value) {
                is String -> value.trim().trim('"').takeIf { candidate ->
                    candidate.startsWith("data:image/", ignoreCase = true) ||
                        candidate.startsWith("https://", ignoreCase = true) ||
                        candidate.startsWith("http://", ignoreCase = true) ||
                        candidate.startsWith("//") ||
                        candidate.startsWith("/") ||
                        candidate.contains("/uploads/", ignoreCase = true) ||
                        candidate.contains("/media/", ignoreCase = true)
                }
                is JSONObject -> {
                    val priorityKeys = arrayOf(
                        "foto", "fotoUrl", "foto_url", "urlFoto", "url_foto",
                        "photo", "photoUrl", "photo_url",
                        "avatar", "avatarUrl", "avatar_url",
                        "imagem", "image", "imageUrl", "image_url",
                        "url", "uri", "src", "path",
                        "data", "resultado", "result", "payload"
                    )
                    priorityKeys.firstNotNullOfOrNull { key ->
                        if (value.has(key)) find(value.opt(key), depth + 1) else null
                    }
                }
                is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { index ->
                    find(value.opt(index), depth + 1)
                }
                else -> null
            }
        }
        return find(parsed, 0)
    }

    private fun tokenCacheFingerprint(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte) }

    private sealed interface ProfilePhotoEndpointResult {
        data class Photo(val bytes: ByteArray) : ProfilePhotoEndpointResult
        data object NoPhoto : ProfilePhotoEndpointResult
        data object Unauthorized : ProfilePhotoEndpointResult
    }

    /** Banco de horas do Nossa Gente. O contrato oficial usa SALDOTOTAL e MESES. */
    suspend fun fetchHours(): NossaGenteHoursResult = withContext(Dispatchers.IO) {
        fetchHoursOnce(allowSavedCredentialRecovery = true)
    }

    private suspend fun fetchHoursOnce(allowSavedCredentialRecovery: Boolean): NossaGenteHoursResult {
        val token = currentToken() ?: return NossaGenteHoursResult.Unauthorized
        return try {
            val response = authenticatedGet("/horas", token)
            if (response.code == 401 || response.code == 403) {
                if (allowSavedCredentialRecovery && renewFromSavedCredentials()) {
                    return fetchHoursOnce(allowSavedCredentialRecovery = false)
                }
                return NossaGenteHoursResult.Unauthorized
            }
            if (!response.successful) return NossaGenteHoursResult.Error("Não foi possível carregar o banco de horas agora.")
            runCatching { NossaGenteHoursResult.Success(parseHours(response.body)) }
                .getOrElse { NossaGenteHoursResult.Error("A resposta do banco de horas não pôde ser lida.") }
        } catch (_: Exception) {
            NossaGenteHoursResult.Error("Não foi possível carregar o banco de horas. Verifique a internet.")
        }
    }

    /** Convênio e compras do usuário, usando os mesmos endpoints do app Nossa Gente. */
    suspend fun fetchBenefit(): NossaGenteBenefitResult = withContext(Dispatchers.IO) {
        fetchBenefitOnce(allowSavedCredentialRecovery = true)
    }

    private fun parseHours(raw: String): HoursSummary {
        val json = JSONObject(raw)
        val months = json.optJSONArray("MESES") ?: JSONArray()
        val entries = (0 until months.length()).mapNotNull { index ->
            months.optJSONObject(index)?.let { month ->
                HoursMonth(month.optInt("ano"), month.optString("mes"), normalizeHours(month.optString("saldo")))
            }
        }
        return HoursSummary(
            total = normalizeHours(json.optString("SALDOTOTAL")),
            months = entries
        )
    }

    private suspend fun fetchBenefitOnce(allowSavedCredentialRecovery: Boolean): NossaGenteBenefitResult {
        val token = currentToken() ?: return NossaGenteBenefitResult.Unauthorized
        return try {
            val saldo = authenticatedGet("/convenios/compras/mercado/saldo", token)
            if (saldo.code == 401 || saldo.code == 403) {
                if (allowSavedCredentialRecovery && renewFromSavedCredentials()) {
                    return fetchBenefitOnce(allowSavedCredentialRecovery = false)
                }
                return NossaGenteBenefitResult.Unauthorized
            }
            if (!saldo.successful) return NossaGenteBenefitResult.Error("Não foi possível carregar o convênio agora.")

            val compras = authenticatedGet("/convenios/compras/mercado/itens?limit=20", token)
            if (compras.code == 401 || compras.code == 403) {
                if (allowSavedCredentialRecovery && renewFromSavedCredentials()) {
                    return fetchBenefitOnce(allowSavedCredentialRecovery = false)
                }
                return NossaGenteBenefitResult.Unauthorized
            }
            if (!compras.successful) return NossaGenteBenefitResult.Error("Não foi possível carregar as compras do convênio agora.")

            runCatching { NossaGenteBenefitResult.Success(parseBenefit(saldo.body, compras.body)) }
                .getOrElse { NossaGenteBenefitResult.Error("A resposta do convênio não pôde ser lida.") }
        } catch (_: Exception) {
            NossaGenteBenefitResult.Error("Não foi possível carregar o convênio. Verifique a internet.")
        }
    }

    private fun authenticatedGet(path: String, token: String): AuthenticatedResponse {
        val separator = if (path.contains('?')) '&' else '?'
        val request = Request.Builder()
            .url("${BuildConfig.NOSSA_GENTE_API_BASE_URL}$path${separator}_sync=${System.currentTimeMillis()}")
            .get()
            .header("Accept", "application/json")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .header("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            AuthenticatedResponse(response.code, response.isSuccessful, response.body?.string().orEmpty())
        }
    }

    private fun parseEmployeeProfile(raw: String): EmployeeProfile {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return EmployeeProfile()
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return EmployeeProfile()
        val level1 = firstObject(root, "data", "user", "usuario", "profile", "perfil", "colaborador", "funcionario", "employee") ?: root
        val source = firstObject(level1, "user", "usuario", "profile", "perfil", "colaborador", "funcionario", "employee") ?: level1
        val admissionRaw = source.textValue(
            "dataAdmissao", "data_admissao", "dtAdmissao", "dt_admissao",
            "admissao", "admissionDate", "hireDate"
        )
        val tenureYears = source.optValue(
            "tempoAnos", "tempoCasaAnos", "tempo_casa_anos", "anos", "years"
        ).toIntOrNullSafe()
        val tenureText = source.textValue("tempo", "tempoCasa", "tempo_casa", "tempoEmpresa", "tempo_empresa")
        val photoRaw = firstNonBlank(
            source.profilePhotoValue(),
            level1.profilePhotoValue(),
            root.profilePhotoValue()
        )
        return EmployeeProfile(
            name = source.textValue("nome", "NOME", "name", "nomeCompleto", "nome_completo", "fullName"),
            admissionDate = formatAdmissionDate(admissionRaw),
            tenure = normalizeTenureLabel(tenureText, tenureYears),
            tenureYears = tenureYears,
            employeeId = source.textValue("id", "codigoUsuario", "codigo_usuario", "userId", "usuarioId"),
            registration = source.textValue("matricula", "MATRICULA", "registro", "registration"),
            photoUrl = normalizeProfilePhotoUrl(photoRaw)
        ).withComputedTenure(admissionRaw)
    }

    private fun mergeTenureProfile(base: EmployeeProfile, raw: String): EmployeeProfile {
        val array = runCatching {
            payloadArray(
                raw, "data", "resultado", "result", "payload", "items",
                "tempoCasa", "tempo_casa", "colaboradores", "employees"
            )
        }.getOrElse { JSONArray() }
        val candidates = (0 until array.length()).mapNotNull(array::optJSONObject)
        if (candidates.isEmpty()) return base

        fun JSONObject.matchesBase(): Boolean {
            val id = textValue("id", "codigoUsuario", "codigo_usuario", "userId", "usuarioId")
            val registration = textValue("matricula", "MATRICULA", "registro", "registration")
            val name = textValue("nome", "NOME", "name", "nomeCompleto", "nome_completo", "fullName")
            return when {
                !base.employeeId.isNullOrBlank() && !id.isNullOrBlank() -> base.employeeId == id
                !base.registration.isNullOrBlank() && !registration.isNullOrBlank() -> base.registration == registration
                !base.name.isNullOrBlank() && !name.isNullOrBlank() -> base.name.equals(name, ignoreCase = true)
                else -> false
            }
        }

        val candidate = candidates.firstOrNull { it.matchesBase() } ?: candidates.singleOrNull() ?: return base
        val admissionRaw = candidate.textValue(
            "dataAdmissao", "data_admissao", "dtAdmissao", "dt_admissao",
            "admissao", "admissionDate", "hireDate"
        )
        val tenureYears = candidate.optValue(
            "tempoAnos", "tempoCasaAnos", "tempo_casa_anos", "anos", "years"
        ).toIntOrNullSafe()
        val tenureText = candidate.textValue("tempo", "tempoCasa", "tempo_casa", "tempoEmpresa", "tempo_empresa")

        return base.copy(
            name = base.name ?: candidate.textValue("nome", "NOME", "name", "nomeCompleto", "nome_completo", "fullName"),
            admissionDate = base.admissionDate ?: formatAdmissionDate(admissionRaw),
            tenure = normalizeTenureLabel(tenureText, tenureYears) ?: base.tenure,
            tenureYears = tenureYears ?: base.tenureYears,
            employeeId = base.employeeId ?: candidate.textValue("id", "codigoUsuario", "codigo_usuario", "userId", "usuarioId"),
            registration = base.registration ?: candidate.textValue("matricula", "MATRICULA", "registro", "registration"),
            photoUrl = base.photoUrl ?: normalizeProfilePhotoUrl(candidate.profilePhotoValue())
        ).withComputedTenure(admissionRaw)
    }

    private fun EmployeeProfile.withComputedTenure(rawAdmission: String? = null): EmployeeProfile {
        val admission = rawAdmission ?: admissionDate
        val exact = formatExactTenure(admission)
        if (!exact.isNullOrBlank()) {
            return copy(
                tenure = exact,
                tenureYears = calculateTenureParts(admission)?.first ?: tenureYears
            )
        }
        if (!tenure.isNullOrBlank()) return this
        val years = tenureYears
        val label = when (years) {
            null -> null
            0 -> "0 meses"
            1 -> "1 ano"
            else -> "$years anos"
        }
        return copy(tenure = label, tenureYears = years)
    }

    private fun normalizeTenureLabel(raw: String?, years: Int?): String? {
        raw?.trim()?.takeIf { it.isNotBlank() && it != "null" }?.let { value ->
            if (value.matches(Regex("""\d+"""))) {
                val n = value.toIntOrNull()
                if (n != null) return if (n == 1) "1 ano" else "$n anos"
            }
            return value
        }
        return years?.let { if (it == 1) "1 ano" else "$it anos" }
    }

    private fun formatAdmissionDate(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(value)?.let { match ->
            val (year, month, day) = match.destructured
            return "$day/$month/$year"
        }
        Regex("""^(\d{2})/(\d{2})/(\d{4})""").find(value)?.let { return it.value }
        return value.substringBefore("T").substringBefore(" 00:00:00")
    }

    private fun calculateTenureYears(raw: String?): Int? = calculateTenureParts(raw)?.first

    private fun calculateTenureParts(
        raw: String?,
        now: java.util.Calendar = java.util.Calendar.getInstance()
    ): Pair<Int, Int>? {
        val value = raw?.trim() ?: return null
        val iso = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(value)
        val br = Regex("""^(\d{2})/(\d{2})/(\d{4})""").find(value)
        val year: Int
        val month: Int
        val day: Int
        when {
            iso != null -> {
                year = iso.groupValues[1].toIntOrNull() ?: return null
                month = iso.groupValues[2].toIntOrNull() ?: return null
                day = iso.groupValues[3].toIntOrNull() ?: return null
            }
            br != null -> {
                day = br.groupValues[1].toIntOrNull() ?: return null
                month = br.groupValues[2].toIntOrNull() ?: return null
                year = br.groupValues[3].toIntOrNull() ?: return null
            }
            else -> return null
        }

        var totalMonths =
            (now.get(java.util.Calendar.YEAR) - year) * 12 +
                ((now.get(java.util.Calendar.MONTH) + 1) - month)
        if (now.get(java.util.Calendar.DAY_OF_MONTH) < day) totalMonths--
        if (totalMonths < 0) return null
        return (totalMonths / 12) to (totalMonths % 12)
    }

    private fun formatExactTenure(
        raw: String?,
        now: java.util.Calendar = java.util.Calendar.getInstance()
    ): String? {
        val (years, months) = calculateTenureParts(raw, now) ?: return null
        val yearLabel = when (years) {
            0 -> null
            1 -> "1 ano"
            else -> "$years anos"
        }
        val monthLabel = when (months) {
            0 -> null
            1 -> "1 mês"
            else -> "$months meses"
        }
        return listOfNotNull(yearLabel, monthLabel).joinToString(" e ").ifBlank { "0 meses" }
    }

    internal fun formatExactTenureForTest(raw: String?, nowMillis: Long): String? {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        return formatExactTenure(raw, calendar)
    }

    private fun Any?.toIntOrNullSafe(): Int? = when (this) {
        is Number -> toInt()
        is String -> trim().toIntOrNull()
        else -> null
    }

    private fun parseBenefit(saldoRaw: String, comprasRaw: String): BenefitSummary {
        val saldos = payloadArray(saldoRaw, "data", "resultado", "result", "payload", "saldos", "saldo")
        val saldo = (0 until saldos.length()).mapNotNull(saldos::optJSONObject).firstOrNull() ?: JSONObject()
        val purchases = payloadArray(comprasRaw, "data", "resultado", "result", "payload", "compras", "itens")
        val limitValue = saldo.optValue("limite", "valorLimite")
        val spentValue = saldo.optValue("valorGasto", "gasto", "valor_gasto")
        val balanceValue = saldo.optValue("saldoDisponivel", "saldo", "valorSaldo")
            ?: run {
                val limit = limitValue.toDecimalOrNull()
                val spent = spentValue.toDecimalOrNull()
                if (limit != null && spent != null) limit.subtract(spent) else null
            }
        return BenefitSummary(
            period = saldo.textValue("periodo", "referencia", "ciclo", "periodoAtual"),
            updatedAt = saldo.textValue("atualizadoEm", "atualizado", "dataAtualizacao", "updatedAt", "dataCompra"),
            limit = formatMoney(limitValue),
            spent = formatMoney(spentValue),
            balance = formatMoney(balanceValue),
            purchases = (0 until purchases.length()).mapNotNull { index ->
                purchases.optJSONObject(index)?.let { item ->
                    val purchaseDateTime = item.textValue("dataDaCompra", "dataCompra", "data", "dataLancamento")
                    BenefitPurchase(
                        date = purchaseDateTime,
                        time = item.textValue("hora", "time", "horario"),
                        place = item.textValue("localCompra", "estabelecimento", "loja", "local"),
                        amount = formatMoney(item.optValue("vlrPago", "valorGasto", "valor", "total")),
                        description = item.textValue("descricao", "description", "compra", "descontaEm")
                    )
                }
            }
        )
    }

    private fun payloadArray(raw: String, vararg wrapperKeys: String): JSONArray {
        val parsed = JSONTokener(raw.trim()).nextValue()
        if (parsed is JSONArray) return parsed
        if (parsed !is JSONObject) return JSONArray()
        wrapperKeys.forEach { key ->
            when (val value = parsed.opt(key)) {
                is JSONArray -> return value
                is JSONObject -> return payloadArray(value.toString(), *wrapperKeys)
            }
        }
        return JSONArray().put(parsed)
    }

    private fun JSONObject.profilePhotoValue(): String? {
        val keys = arrayOf(
            "foto", "fotoUrl", "foto_url", "urlFoto", "url_foto",
            "photo", "photoUrl", "photo_url",
            "avatar", "avatarUrl", "avatar_url",
            "imagem", "image", "imageUrl", "image_url"
        )
        keys.forEach { key ->
            when (val value = opt(key)) {
                is JSONObject -> {
                    value.textValue(
                        "url", "uri", "src", "path",
                        "foto", "fotoUrl", "imagem", "imageUrl", "photoUrl", "avatarUrl"
                    )?.let { return it }
                }
                null, JSONObject.NULL -> Unit
                else -> value.toString().trim()
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { return it }
            }
        }
        return null
    }

    private fun normalizeProfilePhotoUrl(raw: String?): String? {
        val value = raw?.trim()?.trim('"')
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        if (value.startsWith("data:image/", ignoreCase = true)) return value
        if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) {
            return value
        }
        if (value.startsWith("//")) return "https:" + value

        val base = BuildConfig.NOSSA_GENTE_API_BASE_URL.toHttpUrlOrNull() ?: return null
        return base.resolve(value)?.toString()
            ?: base.resolve("/" + value.trimStart('/'))?.toString()
    }

    private fun JSONObject.optValue(vararg keys: String): Any? {
        keys.forEach { key ->
            val value = opt(key)
            if (value != null && value != JSONObject.NULL && value.toString().isNotBlank()) return value
        }
        return null
    }

    private fun JSONObject.textValue(vararg keys: String): String? =
        optValue(*keys)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    private fun formatMoney(value: Any?): String? {
        val number = value.toDecimalOrNull() ?: return null
        return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(number)
    }

    private fun normalizeHours(value: String): String = value.trim().let {
        if (it.matches(Regex("\\d{3}:\\d{2}"))) it.removePrefix("0") else it
    }

    private suspend fun fetchPointOnce(limit: Int, allowSavedCredentialRecovery: Boolean): NossaGentePointResult {
        val token = currentToken() ?: return NossaGentePointResult.Unauthorized
        return try {
            val request = Request.Builder()
                .url("${BuildConfig.NOSSA_GENTE_API_BASE_URL}/ponto?limit=$limit&_sync=${System.currentTimeMillis()}")
                .get()
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache, no-store")
                .header("Pragma", "no-cache")
                .build()
            val result = client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401 || response.code == 403) {
                    if (allowSavedCredentialRecovery && renewFromSavedCredentials()) {
                        return@use fetchPointOnce(limit, allowSavedCredentialRecovery = false)
                    }
                    return@use NossaGentePointResult.Unauthorized
                }
                if (!response.isSuccessful) return@use NossaGentePointResult.Error("Não foi possível carregar o ponto agora.")
                // Uma resposta válida pode ter apenas o banco de horas ou uma lista
                // vazia de marcações. Isso não deve bloquear o restante do Meu Perfil.
                NossaGentePointResult.Success(parsePoint(body))
            }
            result
        } catch (_: Exception) {
            NossaGentePointResult.Error("Não foi possível carregar o ponto. Verifique a internet.")
        }
    }

    private fun parsePoint(raw: String): PointSummary {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return PointSummary()
        val rootObject = runCatching { if (trimmed.startsWith("[")) null else JSONObject(trimmed) }.getOrNull()
        val rootArray = if (trimmed.startsWith("[")) runCatching { JSONArray(trimmed) }.getOrNull() else null
        val dataObject = rootObject?.let { firstObject(it, "data", "resultado", "result", "payload", "ponto", "folha", "espelho") }
        val records = linkedMapOf<String, PointEntry>()
        rootObject?.let { collectPointEntries(it, records) }
        rootArray?.let { collectPointEntries(it, records) }
        return PointSummary(
            period = firstPointString(rootObject, dataObject, "periodo", "PERIODO", "period", "mesAno", "competencia", "mes", "referencia", "periodoReferencia", "periodo_atual"),
            status = firstPointString(rootObject, dataObject, "status", "situacao", "PontoStatus", "pontoStatus", "situacaoPonto"),
            balance = firstPointString(rootObject, dataObject, "saldo", "saldoHoras", "bancoHoras", "saldoBanco", "saldo_horas"),
            worked = firstPointString(rootObject, dataObject, "horasTrabalhadas", "horas", "totalHoras", "horasApuradas", "horas_trabalhadas"),
            records = records.values.toList()
        )
    }

    /** Compatibilidade com contêineres JSON; o contrato autenticado ainda precisa
     * de validação. Uma resposta desconhecida não comprova ausência de ponto. */
    private fun collectPointEntries(value: Any, out: LinkedHashMap<String, PointEntry>, depth: Int = 0) {
        if (depth > 8) return
        when (value) {
            is JSONArray -> for (index in 0 until value.length()) value.opt(index)?.let { collectPointEntries(it, out, depth + 1) }
            is JSONObject -> {
                if (looksLikePointEntry(value)) {
                    val entry = parsePointEntry(value)
                    val fields = listOf(entry.date, entry.entry, entry.exit, entry.interval, entry.status)
                    if (fields.any { !it.isNullOrBlank() }) {
                        val key = fields.joinToString("|") { it.orEmpty() }
                        out.putIfAbsent(key, entry)
                    }
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if (child is JSONObject || child is JSONArray) collectPointEntries(child, out, depth + 1)
                }
            }
        }
    }

    private fun looksLikePointEntry(item: JSONObject): Boolean {
        val keys = setOf(
            "data", "DATA", "dia", "date", "dataPonto", "data_ponto", "dataMarcacao", "data_marcacao", "diaPonto", "dia_ponto", "dtPonto", "dt_ponto",
            "entrada", "ENTRADA", "horaEntrada", "hora_entrada", "horarioEntrada", "horario_entrada", "entradaHora", "entrada1", "entrada_1", "in", "in1",
            "saida", "SAIDA", "horaSaida", "hora_saida", "horarioSaida", "horario_saida", "saidaHora", "saida1", "saida_1", "out", "out1",
            "hora", "horario", "time", "batida", "marcacao", "tipoMarcacao", "tipo_marcacao"
        )
        return keys.any { key ->
            val value = item.opt(key)
            value != null && value != JSONObject.NULL && value !is JSONArray &&
                (value !is JSONObject || key in setOf("entrada", "saida", "intervalo", "batida", "marcacao"))
        }
    }

    private fun firstPointString(root: JSONObject?, data: JSONObject?, vararg keys: String): String? {
        keys.forEach { key -> firstNonBlank(root?.optString(key), data?.optString(key))?.let { return it } }
        return null
    }

    private fun parsePointEntry(item: JSONObject): PointEntry = PointEntry(
        date = firstValueString(item, "data", "DATA", "dia", "date", "dataPonto", "data_ponto", "dataMarcacao", "data_marcacao", "diaPonto", "dia_ponto", "dtPonto", "dt_ponto"),
        entry = firstValueString(item, "entrada", "ENTRADA", "horaEntrada", "hora_entrada", "horarioEntrada", "horario_entrada", "entradaHora", "entrada1", "entrada_1", "in", "in1", "inicio"),
        exit = firstValueString(item, "saida", "SAIDA", "horaSaida", "hora_saida", "horarioSaida", "horario_saida", "saidaHora", "saida1", "saida_1", "out", "out1", "fim"),
        interval = firstValueString(item, "intervalo", "almoco", "pausa", "horaIntervalo", "interval"),
        status = firstValueString(item, "status", "situacao", "situacaoPonto")
    )

    private fun firstValueString(item: JSONObject, vararg keys: String): String? {
        keys.forEach { key ->
            val value = item.opt(key)
            when (value) {
                is JSONObject -> firstValueString(value, "hora", "horario", "time", "valor", "value", "data")?.let { return it }
                is JSONArray -> Unit
                null, JSONObject.NULL -> Unit
                else -> value.toString().trim().takeIf { it.isNotBlank() && it != "null" }?.let { return it }
            }
        }
        return null
    }

    /**
     * Mantém o acesso persistente enquanto o usuário não tocar em "Sair".
     * Se o token expirar no servidor, tenta uma única renovação silenciosa com as
     * credenciais que o próprio usuário escolheu salvar neste aparelho.
     */
    private suspend fun fetchPromotionsOnce(allowSavedCredentialRecovery: Boolean): NossaGentePromotionsResult {
        val token = currentToken() ?: return NossaGentePromotionsResult.Unauthorized
        return try {
            val request = Request.Builder()
                .url("${BuildConfig.NOSSA_GENTE_API_BASE_URL}/promocoes?limit=10&_sync=${System.currentTimeMillis()}")
                .get()
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cache-Control", "no-cache, no-store")
                .header("Pragma", "no-cache")
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401 || response.code == 403) {
                    if (allowSavedCredentialRecovery && renewFromSavedCredentials()) {
                        return fetchPromotionsOnce(allowSavedCredentialRecovery = false)
                    }
                    return NossaGentePromotionsResult.Unauthorized
                }
                if (!response.isSuccessful) {
                    return NossaGentePromotionsResult.Error("Não foi possível carregar as promoções agora.")
                }
                val promotions = parsePromotions(body)
                if (body.isBlank() || (promotions.isEmpty() && !isEmptyPromotionsPayload(body))) {
                    return NossaGentePromotionsResult.Error("O Nossa Gente respondeu em um formato inesperado. Tente novamente.")
                }
                NossaGentePromotionsResult.Success(
                    promotions = promotions,
                    fingerprint = fingerprintPromotions(promotions)
                )
            }
        } catch (_: Exception) {
            NossaGentePromotionsResult.Error("Não foi possível carregar as promoções. Verifique a internet.")
        }
    }

    private suspend fun renewFromSavedCredentials(): Boolean {
        val saved = credentialStore.load() ?: return false
        clearSession()
        return when (login(saved.cpf, saved.password)) {
            NossaGenteLoginResult.Success -> true
            is NossaGenteLoginResult.Error -> false
        }
    }

    fun logout() {
        clearSession()
    }

    /**
     * Descarta apenas a sessão inválida detectada pelo servidor.
     * Isso evita o ciclo Promoções -> Login -> Promoções quando um token salvo expirou.
     * As credenciais salvas no aparelho continuam preservadas para preencher o login.
     */
    fun invalidateSession() {
        clearSession()
    }

    private fun currentToken(): String? {
        inMemoryToken?.takeIf { it.isNotBlank() }?.let { return it }
        return secureSession.readToken()?.also { inMemoryToken = it }
    }

    private fun clearSession() {
        inMemoryToken = null
        profilePhotoCache.clear()
        secureSession.clear()
    }

    internal fun parsePromotionsForTest(raw: String): List<Promotion> = parsePromotions(raw)
    internal fun parseEmployeeProfileForTest(raw: String): EmployeeProfile = parseEmployeeProfile(raw)
    internal fun normalizeProfilePhotoUrlForTest(raw: String?): String? = normalizeProfilePhotoUrl(raw)
    internal fun parseProfilePhotoBytesForTest(raw: String): ByteArray? = parseProfilePhotoBytes(raw)
    internal fun parseProfilePhotoReferenceForTest(raw: String): String? = parseProfilePhotoReference(raw)
    internal fun looksLikeImageBytesForTest(bytes: ByteArray): Boolean = looksLikeImageBytes(bytes)

    /** Assinatura estável do conteúdo comercial; a ordem da resposta não altera o resultado. */
    private fun fingerprintPromotions(promotions: List<Promotion>): String = fingerprintPromotionsForTest(promotions)

    private fun loginErrorMessage(code: Int, body: String): String {
        val serverCode = runCatching {
            val json = JSONObject(body)
            json.optString("erro").ifBlank { json.optString("code") }
        }.getOrNull().orEmpty().lowercase()
        return when {
            code == 401 || code == 403 -> "CPF ou senha incorretos."
            serverCode in setOf("dados_invalidos", "credenciais_invalidas", "login_invalido") -> "CPF ou senha incorretos."
            serverCode.contains("bloque") -> "Acesso bloqueado. Procure o suporte do Nossa Gente."
            else -> "Não foi possível autenticar agora."
        }
    }

    private fun isEmptyPromotionsPayload(raw: String): Boolean = runCatching {
        val trimmed = raw.trim()
        if (trimmed == "[]") return@runCatching true
        val rootObject = if (trimmed.startsWith("[")) return@runCatching false else JSONObject(trimmed)
        firstArray(rootObject, "data", "promocoes", "promotions", "items", "results")?.length() == 0
    }.getOrDefault(false)

    private fun parsePromotions(raw: String): List<Promotion> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        return runCatching {
            val rootObject = if (trimmed.startsWith("[")) null else JSONObject(trimmed)
            val rootArray = if (trimmed.startsWith("[")) JSONArray(trimmed) else {
                firstArray(rootObject, "data", "promocoes", "promotions", "items", "results") ?: JSONArray()
            }
            if (isFlatPromotionArray(rootArray)) parseFlatPromotions(rootArray) else {
                (0 until rootArray.length()).mapNotNull { index ->
                    rootArray.optJSONObject(index)?.let(::parsePromotion)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun isFlatPromotionArray(array: JSONArray): Boolean {
        val first = array.optJSONObject(0) ?: return false
        return first.has("codproduto") || first.has("desc_prod") || first.has("preco_promo")
    }

    /** Converte o contrato real: uma linha por produto e loja, não promoções aninhadas. */
    private fun parseFlatPromotions(array: JSONArray): List<Promotion> {
        val grouped = linkedMapOf<String, FlatPromotionAccumulator>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val code = firstNonBlank(
                item.optString("codproduto"),
                item.optString("codigoProduto"),
                item.optString("codigo"),
                item.optString("code")
            ) ?: continue
            val name = firstNonBlank(item.optString("desc_prod"), item.optString("nome"), item.optString("name")) ?: "Produto em oferta"
            val start = firstNonBlank(item.optString("datainicio"), item.optString("dataInicio"), item.optString("inicio"))
            val end = firstNonBlank(item.optString("datafim"), item.optString("dataFim"), item.optString("fim"))
            val groupKey = listOf(code, name, start.orEmpty(), end.orEmpty()).joinToString("|")
            val accumulator = grouped.getOrPut(groupKey) {
                FlatPromotionAccumulator(
                    id = groupKey.hashCode().toString(),
                    title = name,
                    description = item.optString("categoria").trim(),
                    imageUrl = firstNonBlank(item.optString("imagem"), item.optString("image"), item.optString("imageUrl")),
                    validFrom = start,
                    validTo = end
                )
            }
            val store = item.optString("loja").trim().takeIf { it.isNotBlank() }
            val productKey = listOf(store.orEmpty(), code, item.optString("preco_normal"), item.optString("preco_promo")).joinToString("|")
            if (accumulator.products.containsKey(productKey)) continue
            val regularPrice = formatPrice(item.opt("preco_normal"))
            val offerPrice = formatPrice(item.opt("preco_promo"))
            accumulator.products[productKey] = PromotionProduct(
                code = code,
                name = name,
                offerPrice = offerPrice,
                regularPrice = regularPrice,
                discount = calculateDiscount(item.opt("preco_normal"), item.opt("preco_promo")),
                storeCode = store,
                imageUrl = firstNonBlank(item.optString("imagem"), item.optString("image"), item.optString("imageUrl")),
                linkUrl = firstNonBlank(item.optString("linkloja"), item.optString("link"), item.optString("url"))
            )
        }
        return grouped.values.map { accumulator ->
            Promotion(
                id = accumulator.id,
                title = accumulator.title,
                description = accumulator.description,
                imageUrl = accumulator.imageUrl,
                validFrom = accumulator.validFrom,
                validTo = accumulator.validTo,
                products = accumulator.products.values.toList()
            )
        }
    }

    private fun parsePromotion(item: JSONObject): Promotion {
        val productsArray = firstArray(item, "produtos", "products", "itens", "items", "ofertas")
        val products = if (productsArray == null) emptyList() else {
            (0 until productsArray.length()).mapNotNull { index ->
                productsArray.optJSONObject(index)?.let(::parsePromotionProduct)
            }
        }
        return Promotion(
            id = firstNonBlank(item.optString("id"), item.optString("codigo"), item.optString("codproduto"), item.optString("code"))
                ?: item.toString().hashCode().toString(),
            title = firstNonBlank(item.optString("titulo"), item.optString("title"), item.optString("nome"), item.optString("name"), item.optString("desc_prod"))
                ?: "Promoção",
            description = firstNonBlank(item.optString("descricao"), item.optString("description"), item.optString("texto"), item.optString("detalhes"), item.optString("categoria"))
                .orEmpty(),
            imageUrl = firstNonBlank(item.optString("imagem"), item.optString("image"), item.optString("imageUrl"), item.optString("banner"), item.optString("urlImagem")),
            validFrom = firstNonBlank(item.optString("dataInicio"), item.optString("datainicio"), item.optString("inicio"), item.optString("validFrom"), item.optString("startDate")),
            validTo = firstNonBlank(item.optString("dataFim"), item.optString("datafim"), item.optString("fim"), item.optString("validTo"), item.optString("endDate")),
            products = products
        )
    }

    private fun parsePromotionProduct(item: JSONObject): PromotionProduct {
        return PromotionProduct(
            code = firstNonBlank(item.optString("codigo"), item.optString("code"), item.optString("codigoProduto"), item.optString("codproduto"), item.optString("productCode")).orEmpty(),
            name = firstNonBlank(item.optString("nome"), item.optString("name"), item.optString("produto"), item.optString("description"), item.optString("desc_prod")).orEmpty(),
            offerPrice = firstNonBlank(item.optString("precoOferta"), item.optString("offerPrice"), item.optString("preco_promo"), item.optString("preco"), item.optString("price")),
            regularPrice = firstNonBlank(item.optString("precoOriginal"), item.optString("regularPrice"), item.optString("preco_normal"), item.optString("precoDe"), item.optString("originalPrice")),
            discount = firstNonBlank(item.optString("desconto"), item.optString("discount"), item.optString("percentualDesconto")),
            storeCode = firstNonBlank(item.optString("loja"), item.optString("store"), item.optString("storeCode")),
            imageUrl = firstNonBlank(item.optString("imagem"), item.optString("image"), item.optString("imageUrl")),
            linkUrl = firstNonBlank(item.optString("linkloja"), item.optString("link"), item.optString("url"))
        )
    }

    private fun formatPrice(value: Any?): String? {
        val raw = when (value) {
            null, JSONObject.NULL -> return null
            is Number -> value.toString()
            else -> value.toString().trim()
        }
        val numeric = raw.replace(",", ".").toBigDecimalOrNull() ?: return raw.takeIf { it.isNotBlank() }
        return "R$ " + numeric.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',')
    }

    private fun calculateDiscount(normal: Any?, offer: Any?): String? {
        val normalValue = normal.toDecimalOrNull() ?: return null
        val offerValue = offer.toDecimalOrNull() ?: return null
        if (normalValue <= java.math.BigDecimal.ZERO || offerValue < java.math.BigDecimal.ZERO || offerValue >= normalValue) return null
        val percentage = normalValue.subtract(offerValue)
            .divide(normalValue, 4, java.math.RoundingMode.HALF_UP)
            .multiply(java.math.BigDecimal(100))
            .setScale(0, java.math.RoundingMode.HALF_UP)
        return "${percentage.toPlainString()}%"
    }

    private fun Any?.toDecimalOrNull(): java.math.BigDecimal? {
        if (this == null || this == JSONObject.NULL) return null
        return toString().replace(",", ".").toBigDecimalOrNull()
    }

    private fun firstArray(objectValue: JSONObject?, vararg keys: String): JSONArray? {
        if (objectValue == null) return null
        keys.forEach { key ->
            objectValue.optJSONArray(key)?.let { return it }
        }
        return null
    }

    private fun firstObject(objectValue: JSONObject?, vararg keys: String): JSONObject? {
        if (objectValue == null) return null
        keys.forEach { key -> objectValue.optJSONObject(key)?.let { return it } }
        return null
    }

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private data class FlatPromotionAccumulator(
        val id: String,
        val title: String,
        val description: String,
        val imageUrl: String?,
        val validFrom: String?,
        val validTo: String?,
        val products: LinkedHashMap<String, PromotionProduct> = linkedMapOf()
    )

    private data class AuthenticatedResponse(val code: Int, val successful: Boolean, val body: String)
}

internal fun fingerprintPromotionsForTest(promotions: List<Promotion>): String {
    val canonical = buildString {
        promotions
            .sortedWith(compareBy<Promotion>({ it.id }, { it.title }, { it.validFrom.orEmpty() }, { it.validTo.orEmpty() }))
            .forEach { promotion ->
                appendFingerprintValue(promotion.id)
                appendFingerprintValue(promotion.title)
                appendFingerprintValue(promotion.description)
                appendFingerprintValue(promotion.validFrom)
                appendFingerprintValue(promotion.validTo)
                promotion.products
                    .sortedWith(
                        compareBy<PromotionProduct>(
                            { it.code },
                            { it.name },
                            { it.storeCode.orEmpty() },
                            { it.offerPrice.orEmpty() },
                            { it.regularPrice.orEmpty() },
                            { it.discount.orEmpty() },
                            { it.imageUrl.orEmpty() },
                            { it.linkUrl.orEmpty() }
                        )
                    )
                    .forEach { product ->
                        appendFingerprintValue(product.code)
                        appendFingerprintValue(product.name)
                        appendFingerprintValue(product.offerPrice)
                        appendFingerprintValue(product.regularPrice)
                        appendFingerprintValue(product.discount)
                        appendFingerprintValue(product.storeCode)
                        appendFingerprintValue(product.imageUrl)
                        appendFingerprintValue(product.linkUrl)
                    }
            }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

private fun StringBuilder.appendFingerprintValue(value: String?) {
    val safeValue = value.orEmpty()
    append(safeValue.length).append(':').append(safeValue)
}

data class Promotion(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val validFrom: String?,
    val validTo: String?,
    val products: List<PromotionProduct>
)

    data class PromotionProduct(
        val code: String,
        val name: String,
        val offerPrice: String?,
        val regularPrice: String?,
        val discount: String?,
        val storeCode: String? = null,
        val imageUrl: String? = null,
        val linkUrl: String? = null
    )


sealed interface NossaGenteLoginResult {
    data object Success : NossaGenteLoginResult
    data class Error(val message: String) : NossaGenteLoginResult
}

sealed interface NossaGentePromotionsResult {
    data class Success(
        val promotions: List<Promotion>,
        val fingerprint: String
    ) : NossaGentePromotionsResult
    data object Unauthorized : NossaGentePromotionsResult
    data class Error(val message: String) : NossaGentePromotionsResult
}

data class PointSummary(
    val period: String? = null,
    val status: String? = null,
    val balance: String? = null,
    val worked: String? = null,
    val records: List<PointEntry> = emptyList()
)

data class PointEntry(
    val date: String? = null,
    val entry: String? = null,
    val exit: String? = null,
    val interval: String? = null,
    val status: String? = null
)

data class HoursSummary(val total: String, val months: List<HoursMonth>)
data class HoursMonth(val year: Int, val month: String, val balance: String)

data class EmployeeProfile(
    val name: String? = null,
    val admissionDate: String? = null,
    val tenure: String? = null,
    val tenureYears: Int? = null,
    val employeeId: String? = null,
    val registration: String? = null,
    val photoUrl: String? = null
)

data class BenefitSummary(
    val period: String? = null,
    val updatedAt: String? = null,
    val limit: String? = null,
    val spent: String? = null,
    val balance: String? = null,
    val purchases: List<BenefitPurchase> = emptyList()
)

data class BenefitPurchase(
    val date: String? = null,
    val time: String? = null,
    val place: String? = null,
    val amount: String? = null,
    val description: String? = null
)

sealed interface NossaGenteProfileResult {
    data class Success(val profile: EmployeeProfile) : NossaGenteProfileResult
    data object Unauthorized : NossaGenteProfileResult
    data class Error(val message: String) : NossaGenteProfileResult
}

sealed interface NossaGenteHoursResult {
    data class Success(val hours: HoursSummary) : NossaGenteHoursResult
    data object Unauthorized : NossaGenteHoursResult
    data class Error(val message: String) : NossaGenteHoursResult
}

sealed interface NossaGenteBenefitResult {
    data class Success(val benefit: BenefitSummary) : NossaGenteBenefitResult
    data object Unauthorized : NossaGenteBenefitResult
    data class Error(val message: String) : NossaGenteBenefitResult
}

sealed interface NossaGentePointResult {
    data class Success(val point: PointSummary) : NossaGentePointResult
    data object Unauthorized : NossaGentePointResult
    data class Error(val message: String) : NossaGentePointResult
}
