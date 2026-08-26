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
import java.security.MessageDigest
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
    @Volatile
    private var inMemoryToken: String? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

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
                // A cópia em memória atende a sessão corrente, enquanto a persistência
                // cifrada mantém o acesso após fechar e reabrir o aplicativo.
                if (!sessionStore.saveToken(token)) {
                    inMemoryToken = null
                    return@withContext NossaGenteLoginResult.Error(
                        "Não foi possível salvar a sessão neste aparelho. Tente novamente."
                    )
                }
                inMemoryToken = token
                NossaGenteLoginResult.Success
            }
        } catch (_: Exception) {
            NossaGenteLoginResult.Error("Não foi possível conectar ao Nossa Gente. Tente novamente.")
        }
    }

    suspend fun fetchPromotions(): NossaGentePromotionsResult = withContext(Dispatchers.IO) {
        val token = currentToken()
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
                    inMemoryToken = null
                    sessionStore.clear()
                    return@withContext NossaGentePromotionsResult.Unauthorized
                }
                if (!response.isSuccessful) {
                    return@withContext NossaGentePromotionsResult.Error("Não foi possível carregar as promoções agora.")
                }
                val promotions = parsePromotions(body)
                NossaGentePromotionsResult.Success(
                    promotions = promotions,
                    fingerprint = fingerprintPromotions(promotions)
                )
            }
        } catch (_: Exception) {
            NossaGentePromotionsResult.Error("Não foi possível carregar as promoções. Verifique a internet.")
        }
    }

    fun logout() {
        inMemoryToken = null
        sessionStore.clear()
    }

    private fun currentToken(): String? {
        val memoryToken = inMemoryToken
        if (!memoryToken.isNullOrBlank()) return memoryToken
        return sessionStore.readToken()?.also { restoredToken ->
            inMemoryToken = restoredToken
        }
    }

    internal fun parsePromotionsForTest(raw: String): List<Promotion> = parsePromotions(raw)

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
                            { it.regularPrice.orEmpty() }
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

private class NossaGenteSessionStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("nossa_gente_session", Context.MODE_PRIVATE)
    private val alias = "nrd_nossa_gente_session_key"
    private val keyLock = Any()

    fun saveToken(token: String): Boolean = runCatching {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()
    }.getOrDefault(false)

    fun readToken(): String? = runCatching {
        val encodedIv = preferences.getString("iv", null) ?: return@runCatching null
        val encodedToken = preferences.getString("token", null) ?: return@runCatching null
        val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(encodedToken, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }.getOrNull()

    fun clear(): Boolean = preferences.edit().clear().commit()

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null)
        if (existing is SecretKey) return@synchronized existing
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
        generator.generateKey()
    }
}
