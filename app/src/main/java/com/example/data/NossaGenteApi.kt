package com.example.data

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/** Cliente mínimo para autenticar e consultar promoções da Nossa Gente.
 *  A senha é usada somente na requisição de login e nunca é persistida.
 */
class NossaGenteApi(context: Context) {
    private val appContext = context.applicationContext
    private val sessionStore = NossaGenteSessionStore(appContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    fun hasSession(): Boolean = !sessionStore.readToken().isNullOrBlank()

    suspend fun login(matricula: String, password: String): NossaGenteLoginResult = withContext(Dispatchers.IO) {
        val cleanMatricula = matricula.trim()
        if (cleanMatricula.isBlank() || password.isBlank()) {
            return@withContext NossaGenteLoginResult.Error("Informe matrícula e senha.")
        }

        try {
            val payload = JSONObject()
                .put("matricula", cleanMatricula)
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
                sessionStore.saveToken(token)
                NossaGenteLoginResult.Success
            }
        } catch (_: Exception) {
            NossaGenteLoginResult.Error("Não foi possível conectar ao Nossa Gente. Tente novamente.")
        }
    }

    suspend fun fetchPromotions(): NossaGentePromotionsResult = withContext(Dispatchers.IO) {
        val token = sessionStore.readToken()
            ?: return@withContext NossaGentePromotionsResult.Unauthorized
        try {
            val request = Request.Builder()
                .url("${BuildConfig.NOSSA_GENTE_API_BASE_URL}/promocoes?limit=10")
                .get()
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401 || response.code == 403) {
                    sessionStore.clear()
                    return@withContext NossaGentePromotionsResult.Unauthorized
                }
                if (!response.isSuccessful) {
                    return@withContext NossaGentePromotionsResult.Error("Não foi possível carregar as promoções agora.")
                }
                NossaGentePromotionsResult.Success(parsePromotions(body))
            }
        } catch (_: Exception) {
            NossaGentePromotionsResult.Error("Não foi possível carregar as promoções. Verifique a internet.")
        }
    }

    fun logout() {
        sessionStore.clear()
    }

    private fun loginErrorMessage(code: Int, body: String): String {
        if (code == 401 || code == 403) return "Matrícula ou senha incorretas."
        val serverMessage = runCatching { JSONObject(body).optString("erro") }.getOrNull()
        return if (!serverMessage.isNullOrBlank()) "Não foi possível autenticar: $serverMessage" else "Não foi possível autenticar agora."
    }

    private fun parsePromotions(raw: String): List<Promotion> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        return runCatching {
            val rootObject = if (trimmed.startsWith("[")) null else JSONObject(trimmed)
            val rootArray = if (trimmed.startsWith("[")) JSONArray(trimmed) else {
                firstArray(rootObject, "data", "promocoes", "promotions", "items", "results") ?: JSONArray()
            }
            (0 until rootArray.length()).mapNotNull { index ->
                rootArray.optJSONObject(index)?.let(::parsePromotion)
            }
        }.getOrDefault(emptyList())
    }

    private fun parsePromotion(item: JSONObject): Promotion {
        val productsArray = firstArray(item, "produtos", "products", "itens", "items", "ofertas")
        val products = if (productsArray == null) emptyList() else {
            (0 until productsArray.length()).mapNotNull { index ->
                productsArray.optJSONObject(index)?.let(::parsePromotionProduct)
            }
        }
        return Promotion(
            id = firstNonBlank(item.optString("id"), item.optString("codigo"), item.optString("code"))
                ?: item.toString().hashCode().toString(),
            title = firstNonBlank(item.optString("titulo"), item.optString("title"), item.optString("nome"), item.optString("name"))
                ?: "Promoção",
            description = firstNonBlank(item.optString("descricao"), item.optString("description"), item.optString("texto"), item.optString("detalhes"))
                .orEmpty(),
            imageUrl = firstNonBlank(item.optString("imagem"), item.optString("image"), item.optString("imageUrl"), item.optString("banner"), item.optString("urlImagem")),
            validFrom = firstNonBlank(item.optString("dataInicio"), item.optString("inicio"), item.optString("validFrom"), item.optString("startDate")),
            validTo = firstNonBlank(item.optString("dataFim"), item.optString("fim"), item.optString("validTo"), item.optString("endDate")),
            products = products
        )
    }

    private fun parsePromotionProduct(item: JSONObject): PromotionProduct {
        return PromotionProduct(
            code = firstNonBlank(item.optString("codigo"), item.optString("code"), item.optString("codigoProduto"), item.optString("productCode")).orEmpty(),
            name = firstNonBlank(item.optString("nome"), item.optString("name"), item.optString("produto"), item.optString("description")).orEmpty(),
            offerPrice = firstNonBlank(item.optString("precoOferta"), item.optString("offerPrice"), item.optString("preco"), item.optString("price")),
            regularPrice = firstNonBlank(item.optString("precoOriginal"), item.optString("regularPrice"), item.optString("precoDe"), item.optString("originalPrice")),
            discount = firstNonBlank(item.optString("desconto"), item.optString("discount"), item.optString("percentualDesconto"))
        )
    }

    private fun firstArray(objectValue: JSONObject?, vararg keys: String): JSONArray? {
        if (objectValue == null) return null
        keys.forEach { key ->
            objectValue.optJSONArray(key)?.let { return it }
        }
        return null
    }

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }
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
    val discount: String?
)

sealed interface NossaGenteLoginResult {
    data object Success : NossaGenteLoginResult
    data class Error(val message: String) : NossaGenteLoginResult
}

sealed interface NossaGentePromotionsResult {
    data class Success(val promotions: List<Promotion>) : NossaGentePromotionsResult
    data object Unauthorized : NossaGentePromotionsResult
    data class Error(val message: String) : NossaGentePromotionsResult
}

private class NossaGenteSessionStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("nossa_gente_session", Context.MODE_PRIVATE)
    private val alias = "nrd_nossa_gente_session_key"

    fun saveToken(token: String) {
        runCatching {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
            preferences.edit()
                .putString("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString("token", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .apply()
        }
    }

    fun readToken(): String? = runCatching {
        val encodedIv = preferences.getString("iv", null) ?: return@runCatching null
        val encodedToken = preferences.getString("token", null) ?: return@runCatching null
        val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(encodedToken, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }.getOrNull()

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
