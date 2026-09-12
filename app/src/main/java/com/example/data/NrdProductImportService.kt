package com.example.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal data class NrdProductImportResult(
    val success: Boolean,
    val duplicate: Boolean = false,
    val message: String? = null
)

/**
 * Ponte pequena entre a consulta ACP e o catálogo NRD.
 * Mantém `category` como categoria principal para compatibilidade e grava
 * `categories` como a lista completa de vínculos escolhidos pelo administrador.
 */
internal object NrdProductImportService {
    private const val TAG = "NrdProductImport"
    private val client = OkHttpClient()
    private val categoryCache = ConcurrentHashMap<String, List<String>>()

    fun cachedCategories(code: String): List<String>? = categoryCache[code.trim()]?.takeIf { it.isNotEmpty() }

    suspend fun categoriesForCode(code: String): List<String>? {
        val cleanCode = code.trim()
        if (cleanCode.isBlank() || !FirebaseService.isFirebaseConfigured()) return null
        cachedCategories(cleanCode)?.let { return it }
        return try {
            val document = FirebaseFirestore.getInstance().collection("products").document(cleanCode).get().await()
            if (!document.exists()) return null
            val categories = normalizeProductCategories(
                ((document.get("categories") as? List<*>)?.mapNotNull { it as? String }).orEmpty()
                    .ifEmpty { listOfNotNull(document.getString("category")) }
            )
            if (categories.isNotEmpty()) categoryCache[cleanCode] = categories
            categories.takeIf { it.isNotEmpty() }
        } catch (error: Exception) {
            Log.w(TAG, "Não foi possível consultar categorias de $cleanCode", error)
            null
        }
    }

    suspend fun refreshCategoryCache(): Map<String, List<String>> {
        if (!FirebaseService.isFirebaseConfigured()) return emptyMap()
        return try {
            val documents = FirebaseFirestore.getInstance().collection("products").get().await().documents
            val loaded = mutableMapOf<String, List<String>>()
            documents.forEach { document ->
                val code = document.getString("code")?.trim().orEmpty().ifBlank { document.id }
                if (code.isBlank()) return@forEach
                val categories = normalizeProductCategories(
                    ((document.get("categories") as? List<*>)?.mapNotNull { it as? String }).orEmpty()
                        .ifEmpty { listOfNotNull(document.getString("category")) }
                )
                if (categories.isNotEmpty()) loaded[code] = categories
            }
            categoryCache.clear()
            categoryCache.putAll(loaded)
            loaded
        } catch (error: Exception) {
            Log.w(TAG, "Não foi possível atualizar o cache de categorias", error)
            emptyMap()
        }
    }

    suspend fun addProduct(
        name: String,
        code: String,
        categories: Collection<String>,
        unit: String = "un"
    ): NrdProductImportResult {
        val normalizedCode = code.trim()
        val normalizedName = ProductStandards.normalizeProductName(name)
        val normalizedCategories = normalizeProductCategories(categories)
        if (normalizedCode.isBlank() || normalizedName.isBlank()) {
            return NrdProductImportResult(false, message = "Nome ou código inválido.")
        }
        if (normalizedCategories.isEmpty()) {
            return NrdProductImportResult(false, message = "Selecione pelo menos uma categoria.")
        }
        if (!FirebaseService.isFirebaseConfigured()) {
            return NrdProductImportResult(false, message = "Nuvem do NRD indisponível.")
        }
        val email = FirebaseAuth.getInstance().currentUser?.email?.trim()?.lowercase()
        if (email != "admin@nrdlojas.com" && email != "mestre@nrdlojas.com") {
            return NrdProductImportResult(false, message = "Entre como Admin ou Mestre para adicionar ao NRD.")
        }

        val product = Product(
            code = normalizedCode,
            name = normalizedName,
            searchName = ProductStandards.searchNameFrom(normalizedName),
            category = normalizedCategories.first(),
            unit = unit.trim().ifBlank { "un" },
            categoryMemberships = encodeProductCategories(normalizedCategories)
        )

        categoryCache[normalizedCode] = normalizedCategories
        val result = createRemoteProduct(product, normalizedCategories)
        if (!result.success) {
            categoryCache.remove(normalizedCode)
            return result
        }
        FirebaseService.publishProductEvent("NEW_PRODUCT", product.name, null, product.code)
        return result
    }

    private suspend fun createRemoteProduct(product: Product, categories: List<String>): NrdProductImportResult {
        return try {
            val projectId = FirebaseApp.getInstance().options.projectId.orEmpty()
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token.orEmpty()
            if (projectId.isBlank() || token.isBlank()) {
                return NrdProductImportResult(false, message = "Sessão administrativa inválida. Entre novamente.")
            }

            val encodedCode = URLEncoder.encode(product.code, Charsets.UTF_8.name())
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            val categoryValues = JSONArray().apply {
                categories.forEach { put(JSONObject().put("stringValue", it)) }
            }
            val fields = JSONObject().apply {
                put("code", JSONObject().put("stringValue", product.code))
                put("name", JSONObject().put("stringValue", product.name))
                put("searchName", JSONObject().put("stringValue", product.searchName))
                put("category", JSONObject().put("stringValue", product.category))
                put("categories", JSONObject().put("arrayValue", JSONObject().put("values", categoryValues)))
                put("unit", JSONObject().put("stringValue", product.unit))
                put("searchCount", JSONObject().put("integerValue", "0"))
                put("createdAt", JSONObject().put("timestampValue", timestamp))
                put("updatedAt", JSONObject().put("timestampValue", timestamp))
            }
            val payload = JSONObject().put("fields", fields).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/products?documentId=$encodedCode")
                .post(payload)
                .addHeader("Authorization", "Bearer $token")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> NrdProductImportResult(true)
                        response.code == 409 || body.contains("ALREADY_EXISTS", ignoreCase = true) ->
                            NrdProductImportResult(false, duplicate = true, message = "Esse código já está cadastrado no NRD.")
                        response.code == 401 || response.code == 403 ->
                            NrdProductImportResult(false, message = "Sua sessão não tem permissão para adicionar produtos.")
                        response.code == 429 || body.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                            NrdProductImportResult(false, message = "A cota da nuvem está temporariamente esgotada. Tente novamente depois.")
                        else -> NrdProductImportResult(false, message = "Não foi possível adicionar o produto ao NRD (HTTP ${response.code}).")
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao adicionar produto da ACP", error)
            NrdProductImportResult(false, message = "Não foi possível adicionar o produto ao NRD. Verifique a conexão.")
        }
    }
}
