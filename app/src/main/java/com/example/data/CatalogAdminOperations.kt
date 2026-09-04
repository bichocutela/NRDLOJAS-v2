package com.example.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Operações administrativas que precisam de garantias extras de integridade.
 * As regras do Firestore continuam sendo a autoridade final de permissão.
 */
object CatalogAdminOperations {
    private const val TAG = "CatalogAdminOps"
    private const val MAX_TRANSACTION_PRODUCTS = 180
    private const val MAX_BATCH_PRODUCTS = 400
    private val httpClient = OkHttpClient()

    data class CreateProductResult(
        val success: Boolean,
        val duplicate: Boolean = false,
        val message: String? = null,
    )

    data class ImportProductsResult(
        val savedProducts: List<Product>,
        val duplicateCodes: Set<String>,
        val failed: Boolean,
        val message: String? = null,
    )

    /**
     * Cria um produto sem fazer leitura prévia do documento.
     *
     * O SDK do Firestore não expõe create(document) no Android. A implementação
     * anterior usava transação apenas para garantir "criar se não existir", o que
     * consumia uma leitura antes de cada cadastro e deixava o botão de adicionar
     * dependente da cota de leituras. A API REST createDocument mantém a mesma
     * garantia: retorna ALREADY_EXISTS quando o código já existe, sem consulta
     * prévia feita pelo aplicativo.
     *
     * O Firebase ID Token do Admin/Mestre é enviado no request, portanto as mesmas
     * Security Rules do Firestore continuam valendo para a criação.
     */
    suspend fun createProductIfAbsent(product: Product): CreateProductResult {
        if (!FirebaseService.isFirebaseConfigured() || product.code.isBlank()) {
            return CreateProductResult(false, message = "Nuvem indisponível ou código inválido.")
        }

        return try {
            val projectId = FirebaseApp.getInstance().options.projectId.orEmpty()
            val token = FirebaseAuth.getInstance().currentUser
                ?.getIdToken(false)
                ?.await()
                ?.token
                .orEmpty()
            if (projectId.isBlank() || token.isBlank()) {
                return CreateProductResult(false, message = "Sessão administrativa inválida. Entre novamente e tente de novo.")
            }

            val encodedCode = URLEncoder.encode(product.code.trim(), Charsets.UTF_8.name())
            val endpoint = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/products?documentId=$encodedCode"
            val now = firestoreTimestampNow()
            val fields = JSONObject().apply {
                put("code", firestoreString(product.code.trim()))
                put("name", firestoreString(product.name))
                put("searchName", firestoreString(product.searchName))
                put("category", firestoreString(product.category))
                put("unit", firestoreString(product.unit))
                product.imageUrl?.takeIf { it.isNotBlank() }?.let { put("imageUrl", firestoreString(it)) }
                put("searchCount", JSONObject().put("integerValue", product.searchCount.toString()))
                put("createdAt", JSONObject().put("timestampValue", now))
                put("updatedAt", JSONObject().put("timestampValue", now))
            }
            val body = JSONObject().put("fields", fields).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .addHeader("Authorization", "Bearer $token")
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> CreateProductResult(true)
                        response.code == 409 || responseBody.contains("ALREADY_EXISTS", ignoreCase = true) ->
                            CreateProductResult(false, duplicate = true, message = "Código já cadastrado na nuvem.")
                        response.code == 429 || responseBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                            responseBody.contains("Quota exceeded", ignoreCase = true) ->
                            CreateProductResult(false, message = "A cota do Firebase está temporariamente esgotada. Aguarde a liberação da cota e tente novamente.")
                        response.code == 401 || response.code == 403 ->
                            CreateProductResult(false, message = "A sessão administrativa não tem permissão para criar produtos. Entre novamente.")
                        else -> {
                            Log.e(TAG, "Falha REST ao criar produto: HTTP ${response.code}; $responseBody")
                            CreateProductResult(false, message = "Falha da nuvem (HTTP ${response.code}).")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar produto sem leitura prévia", e)
            val message = e.message.orEmpty()
            CreateProductResult(
                false,
                message = if (
                    message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                    message.contains("Quota exceeded", ignoreCase = true) ||
                    message.contains("429")
                ) {
                    "A cota do Firebase está temporariamente esgotada. Aguarde a liberação da cota e tente novamente."
                } else {
                    message.ifBlank { "Não foi possível criar o produto na nuvem." }
                }
            )
        }
    }

    /**
     * Troca o código em um único Commit REST, sem ler os documentos antes.
     *
     * As precondições garantem atomicamente que o código novo ainda não exista
     * e que o documento antigo exista. Assim a operação não depende da cota de
     * leituras do Firestore e não corre o risco de sobrescrever outro produto.
     */
    suspend fun replaceProductCodeAtomically(oldCode: String, product: Product): CreateProductResult {
        if (!FirebaseService.isFirebaseConfigured() || oldCode.isBlank() || product.code.isBlank()) {
            return CreateProductResult(false, message = "Nuvem indisponível ou código inválido.")
        }

        return try {
            val projectId = FirebaseApp.getInstance().options.projectId.orEmpty()
            val token = FirebaseAuth.getInstance().currentUser
                ?.getIdToken(false)
                ?.await()
                ?.token
                .orEmpty()
            if (projectId.isBlank() || token.isBlank()) {
                return CreateProductResult(false, message = "Sessão administrativa inválida. Entre novamente e tente de novo.")
            }

            val databaseRoot = "projects/$projectId/databases/(default)/documents"
            val oldDocumentName = "$databaseRoot/products/${oldCode.trim()}"
            val newDocumentName = "$databaseRoot/products/${product.code.trim()}"
            val now = firestoreTimestampNow()
            val fields = JSONObject().apply {
                put("code", firestoreString(product.code.trim()))
                put("name", firestoreString(product.name))
                put("searchName", firestoreString(product.searchName))
                put("category", firestoreString(product.category))
                put("unit", firestoreString(product.unit))
                product.imageUrl?.takeIf { it.isNotBlank() }?.let { put("imageUrl", firestoreString(it)) }
                put("searchCount", JSONObject().put("integerValue", product.searchCount.toString()))
                put("updatedAt", JSONObject().put("timestampValue", now))
            }

            val createNewWrite = JSONObject().apply {
                put(
                    "update",
                    JSONObject().apply {
                        put("name", newDocumentName)
                        put("fields", fields)
                    }
                )
                put("currentDocument", JSONObject().put("exists", false))
            }
            val deleteOldWrite = JSONObject().apply {
                put("delete", oldDocumentName)
                put("currentDocument", JSONObject().put("exists", true))
            }
            val body = JSONObject()
                .put("writes", org.json.JSONArray().put(createNewWrite).put(deleteOldWrite))
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents:commit")
                .post(body)
                .addHeader("Authorization", "Bearer $token")
                .build()

            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> CreateProductResult(true)
                        response.code == 429 || responseBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                            responseBody.contains("Quota exceeded", ignoreCase = true) ->
                            CreateProductResult(
                                false,
                                message = "A cota de gravação do Firebase está temporariamente esgotada. Aguarde a liberação da cota e tente novamente."
                            )
                        response.code == 401 || response.code == 403 ||
                            responseBody.contains("UNAUTHENTICATED", ignoreCase = true) ||
                            responseBody.contains("PERMISSION_DENIED", ignoreCase = true) ->
                            CreateProductResult(false, message = "A sessão administrativa não tem permissão para alterar códigos. Entre novamente.")
                        response.code == 409 ||
                            responseBody.contains("ALREADY_EXISTS", ignoreCase = true) ||
                            responseBody.contains("already exists", ignoreCase = true) ->
                            CreateProductResult(false, duplicate = true, message = "O novo código já está em uso na nuvem.")
                        responseBody.contains("NOT_FOUND", ignoreCase = true) ->
                            CreateProductResult(false, message = "O produto original não existe mais na nuvem. Atualize a tela e tente novamente.")
                        responseBody.contains("FAILED_PRECONDITION", ignoreCase = true) ->
                            CreateProductResult(
                                false,
                                message = "A troca não pôde ser concluída porque o código novo já existe ou o produto original mudou. Atualize a tela e tente novamente."
                            )
                        else -> {
                            Log.e(TAG, "Falha REST ao trocar código: HTTP ${response.code}; $responseBody")
                            CreateProductResult(false, message = "Falha da nuvem (HTTP ${response.code}).")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao trocar código sem leitura prévia", e)
            val message = e.message.orEmpty()
            CreateProductResult(
                false,
                message = if (
                    message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                    message.contains("Quota exceeded", ignoreCase = true) ||
                    message.contains("429")
                ) {
                    "A cota de gravação do Firebase está temporariamente esgotada. Aguarde a liberação da cota e tente novamente."
                } else {
                    message.ifBlank { "Não foi possível alterar o código na nuvem." }
                }
            )
        }
    }

    suspend fun saveImportedProductsIfAbsent(products: List<Product>): ImportProductsResult {
        if (products.isEmpty()) return ImportProductsResult(emptyList(), emptySet(), failed = false)
        if (!FirebaseService.isFirebaseConfigured()) {
            return ImportProductsResult(emptyList(), emptySet(), failed = true, message = "Nuvem indisponível.")
        }

        val firestore = FirebaseFirestore.getInstance()
        val saved = mutableListOf<Product>()
        val duplicates = mutableSetOf<String>()
        return try {
            products.chunked(MAX_TRANSACTION_PRODUCTS).forEach { chunk ->
                val result = firestore.runTransaction { transaction ->
                    val existingCodes = mutableSetOf<String>()
                    val refs = chunk.associateWith { product ->
                        firestore.collection("products").document(product.code.trim())
                    }
                    refs.forEach { (product, ref) ->
                        if (transaction.get(ref).exists()) existingCodes += product.code.trim()
                    }
                    refs.forEach { (product, ref) ->
                        if (product.code.trim() !in existingCodes) {
                            transaction.set(ref, product.toRemoteMap())
                        }
                    }
                    existingCodes
                }.await()
                duplicates += result
                saved += chunk.filter { it.code.trim() !in result }
            }
            ImportProductsResult(saved, duplicates, failed = false)
        } catch (e: Exception) {
            Log.e(TAG, "Erro na importação atômica", e)
            ImportProductsResult(saved, duplicates, failed = true, message = e.message)
        }
    }

    /**
     * Renomeia os produtos por lotes e desfaz os lotes já confirmados se algum falhar.
     * A configuração da categoria deve ser salva somente depois deste método retornar true.
     */
    suspend fun renameProductsCategorySafely(oldName: String, newName: String): Boolean {
        if (!FirebaseService.isFirebaseConfigured()) return false
        if (oldName.trim().equals(newName.trim(), ignoreCase = true)) return true

        val firestore = FirebaseFirestore.getInstance()
        val committedRefs = mutableListOf<com.google.firebase.firestore.DocumentReference>()
        return try {
            val documents = firestore.collection("products")
                .whereEqualTo("category", oldName.trim())
                .get()
                .await()
                .documents

            documents.chunked(MAX_BATCH_PRODUCTS).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { document ->
                    batch.update(document.reference, "category", newName.trim())
                }
                batch.commit().await()
                committedRefs += chunk.map { it.reference }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao renomear categoria; iniciando rollback", e)
            runCatching {
                committedRefs.chunked(MAX_BATCH_PRODUCTS).forEach { chunk ->
                    val rollback = firestore.batch()
                    chunk.forEach { ref -> rollback.update(ref, "category", oldName.trim()) }
                    rollback.commit().await()
                }
            }.onFailure { rollbackError ->
                Log.e(TAG, "Rollback da categoria também falhou", rollbackError)
            }
            false
        }
    }

    suspend fun rollbackProductsCategory(currentName: String, previousName: String): Boolean =
        renameProductsCategorySafely(currentName, previousName)

    private fun Product.toRemoteMap(
        searchCountValue: Long = searchCount.toLong(),
        createdAtValue: Any = FieldValue.serverTimestamp(),
        lastViewedAtValue: Any? = null,
    ): Map<String, Any?> = mutableMapOf<String, Any?>(
        "code" to code.trim(),
        "name" to name,
        "searchName" to searchName,
        "category" to category,
        "unit" to unit,
        "imageUrl" to imageUrl,
        "searchCount" to searchCountValue,
        "createdAt" to createdAtValue,
        "updatedAt" to FieldValue.serverTimestamp(),
    ).apply {
        if (lastViewedAtValue != null) {
            this["lastViewedAt"] = lastViewedAtValue
        }
    }

    private fun firestoreString(value: String): JSONObject = JSONObject().put("stringValue", value)

    private fun firestoreTimestampNow(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.US
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
}
