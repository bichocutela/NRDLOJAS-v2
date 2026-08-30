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
import org.json.JSONArray
import org.json.JSONObject

object CatalogHistoryBackend {
    private val client = OkHttpClient()

    private suspend fun call(action: String, extra: JSONObject = JSONObject()): JSONObject? {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        if (baseUrl.isBlank() || anonKey.isBlank()) {
            FirebaseService.lastError = "Backend administrativo não configurado."
            return null
        }

        val token = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (_: Exception) {
            null
        }
        if (token.isNullOrBlank()) {
            FirebaseService.lastError = "Sessão do Mestre expirada. Saia e entre novamente."
            return null
        }

        val payload = JSONObject(extra.toString()).apply { put("action", action) }
        val request = Request.Builder()
            .url("$baseUrl/functions/v1/catalog-admin")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("apikey", anonKey)
            .addHeader("x-firebase-token", token)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(raw) }.getOrNull()
                    if (!response.isSuccessful || json?.optBoolean("ok") != true) {
                        FirebaseService.lastError = json?.optString("error")?.takeIf { it.isNotBlank() }
                            ?: "Falha no backend administrativo (HTTP ${response.code})."
                        null
                    } else {
                        FirebaseService.lastError = null
                        json
                    }
                }
            }
        } catch (e: Exception) {
            FirebaseService.lastError = e.message ?: "Falha de conexão com o backend administrativo."
            null
        }
    }

    private fun productsJson(products: List<Product>): JSONArray = JSONArray().apply {
        products.forEach { product ->
            put(JSONObject().apply {
                put("code", product.code)
                put("name", product.name)
                put("searchName", product.searchName)
                put("category", product.category)
                put("unit", product.unit)
                put("imageUrl", product.imageUrl ?: JSONObject.NULL)
                put("searchCount", product.searchCount)
            })
        }
    }

    suspend fun getMaintenanceSummary(
        localProductCount: Int,
        localCategoryCounts: List<CategoryCount>
    ): MaintenanceSummary {
        val json = call("DIAGNOSE") ?: return MaintenanceSummary(
            localProductCount = localProductCount,
            localCategoryCounts = localCategoryCounts,
            checkedAt = System.currentTimeMillis(),
            remoteAvailable = false
        )

        val remoteCategories = buildList {
            val array = json.optJSONArray("remoteCategoryCounts") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val category = item.optString("category").trim()
                if (category.isNotBlank()) add(CategoryCount(category, item.optInt("count", 0)))
            }
        }.sortedByDescending { it.count }

        return MaintenanceSummary(
            localProductCount = localProductCount,
            remoteProductCount = json.optInt("remoteProductCount", 0),
            localCategoryCounts = localCategoryCounts,
            remoteCategoryCounts = remoteCategories,
            dynamicTabCount = json.optInt("dynamicTabCount", 0),
            pendingSuggestionCount = json.optInt("pendingSuggestionCount", 0),
            lastRemoteProductUpdate = if (json.isNull("lastRemoteProductUpdate")) null else json.optLong("lastRemoteProductUpdate"),
            checkedAt = json.optLong("checkedAt", System.currentTimeMillis()),
            remoteAvailable = true
        )
    }

    suspend fun listSnapshots(): List<CatalogSnapshot> {
        val json = call("LIST") ?: return emptyList()
        val array = json.optJSONArray("snapshots") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    CatalogSnapshot(
                        id = item.optString("id"),
                        createdAt = item.optLong("createdAt"),
                        productCount = item.optInt("productCount"),
                        createdBy = item.optString("createdBy", "desconhecido"),
                        reason = item.optString("reason", "manual"),
                        restoredAt = if (item.isNull("restoredAt")) null else item.optLong("restoredAt")
                    )
                )
            }
        }
    }

    suspend fun createSnapshot(products: List<Product>): CatalogSnapshot? {
        val json = call("CREATE", JSONObject().put("products", productsJson(products))) ?: return null
        val item = json.optJSONObject("snapshot") ?: return null
        return CatalogSnapshot(
            id = item.optString("id"),
            createdAt = item.optLong("createdAt"),
            productCount = item.optInt("productCount"),
            createdBy = item.optString("createdBy", "desconhecido"),
            reason = item.optString("reason", "manual"),
            restoredAt = if (item.isNull("restoredAt")) null else item.optLong("restoredAt")
        )
    }

    suspend fun restoreSnapshot(snapshotId: String, currentProducts: List<Product>): CatalogRestoreResult {
        val payload = JSONObject()
            .put("snapshotId", snapshotId)
            .put("currentProducts", productsJson(currentProducts))
        val json = call("RESTORE", payload)
            ?: return CatalogRestoreResult(
                success = false,
                message = FirebaseService.lastError ?: "Não foi possível restaurar o backup."
            )

        val productsArray = json.optJSONArray("products")
        val products = if (productsArray == null) emptyList() else buildList {
            for (index in 0 until productsArray.length()) {
                val item = productsArray.optJSONObject(index) ?: continue
                add(
                    Product(
                        code = item.optString("code"),
                        name = item.optString("name"),
                        searchName = item.optString("searchName"),
                        category = item.optString("category"),
                        unit = item.optString("unit", "UN"),
                        imageUrl = item.optString("imageUrl").takeIf { !item.isNull("imageUrl") && it.isNotBlank() },
                        searchCount = item.optInt("searchCount", 0)
                    )
                )
            }
        }

        return CatalogRestoreResult(
            success = true,
            restoredProductCount = json.optInt("count", products.size),
            message = json.optString("message", "Backup restaurado com sucesso."),
            restoredProducts = products
        )
    }
}
