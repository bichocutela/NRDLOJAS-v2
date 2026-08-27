package com.example.data
import okhttp3.MediaType.Companion.toMediaType

import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.BuildConfig
import java.util.UUID
import android.webkit.MimeTypeMap
import okhttp3.RequestBody.Companion.asRequestBody

import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import android.util.Log

object FirebaseService {
    var lastError: String? = null
    private var appContext: android.content.Context? = null
    suspend fun publishProductEvent(type: String, productName: String, oldName: String? = null, productCode: String) {
        if (!isFirebaseConfigured()) {
            Log.w("FirebaseService", "Evento FCM ignorado: Firebase não configurado; type=$type")
            return
        }
        try {
            Log.d("FirebaseService", "Iniciando publicação do evento FCM: type=$type, productCode=$productCode")
            val supabaseUrl = BuildConfig.SUPABASE_URL
            val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
                Log.e("FirebaseService", "Supabase not configured, cannot send FCM")
                return
            }

            
            if (type != "NEW_PRODUCT" && type != "CODE_CHANGED") {
                Log.d("FirebaseService", "Evento FCM ignorado: tipo não suportado ($type)")
                return
            }

            val title = if (type == "NEW_PRODUCT") "Produto adicionado" else "Código alterado"
            val text = productName

            val json = org.json.JSONObject()

            json.put("title", title)
            json.put("body", text)
            json.put("topic", "products")

            val requestBody = okhttp3.RequestBody.create("application/json".toMediaType(), json.toString())
            val firebaseToken = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
            if (firebaseToken.isNullOrBlank()) {
                Log.w("FirebaseService", "FCM não enviado: usuário Firebase não autenticado ou Firebase ID Token indisponível")
                return
            }

            Log.d("FirebaseService", "Firebase ID Token obtido para enviar FCM")
            val request = okhttp3.Request.Builder()
                .url("$supabaseUrl/functions/v1/send-fcm")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .addHeader("x-firebase-token", firebaseToken)
                .build()

            Log.d("FirebaseService", "Chamada send-fcm iniciada: type=$type")
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    Log.d("FirebaseService", "send-fcm HTTP status: ${response.code}; type=$type")
                    if (!response.isSuccessful) {
                        Log.e("FirebaseService", "send-fcm falhou: HTTP ${response.code} ${response.message}; corpo: $responseBody")
                    } else {
                        Log.d("FirebaseService", "FCM enviado com sucesso: type=$type")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Erro ao publicar evento FCM: type=$type", e)
        }
    }

    private suspend fun publishSuggestionFixedEvent(topic: String, suggestionText: String) {
        if (topic.isBlank() || !isFirebaseConfigured()) return
        try {
            val supabaseUrl = BuildConfig.SUPABASE_URL
            val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            val firebaseToken = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?.getIdToken(false)?.await()?.token
            if (supabaseUrl.isBlank() || supabaseKey.isBlank() || firebaseToken.isNullOrBlank()) {
                Log.w("FirebaseService", "Push de sugestão não enviado: configuração ou token ausente")
                return
            }
            val payload = org.json.JSONObject().apply {
                put("title", "Sugestão corrigida")
                put("body", "Sua sugestão foi corrigida: $suggestionText")
                put("topic", topic)
            }
            val request = okhttp3.Request.Builder()
                .url("$supabaseUrl/functions/v1/send-fcm")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $supabaseKey")
                .addHeader("x-firebase-token", firebaseToken)
                .build()
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.e("FirebaseService", "Push de sugestão falhou: HTTP ${response.code}; corpo=$responseBody")
                    } else {
                        Log.d("FirebaseService", "Push de sugestão corrigida enviado ao tópico privado")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Erro ao enviar push de sugestão corrigida", e)
        }
    }

    suspend fun productExists(code: String): Boolean {
        if (!isFirebaseConfigured()) return false
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("products").document(code).get().await()
            doc.exists()
        } catch (e: Exception) {
            Log.e("ProductSync", "Erro ao verificar existência do produto: $code", e)
            false
        }
    }

        private suspend fun ensureAuthenticated() {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null) {
                val email = user.email
                if (email == "admin@nrdlojas.com" || email == "mestre@nrdlojas.com") {
                    return
                }
            }
            Log.w("FirebaseService", "Usuário não autenticado para operação restrita")
        } catch (e: Exception) {
            Log.e("FirebaseService", "Auth falhou", e)
        }
    }

    suspend fun saveProduct(product: com.example.data.Product): Boolean {
        ensureAuthenticated()
        if (!isFirebaseConfigured()) return false
        return try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("products").document(product.code)
                .set(mapOf(
                    "code" to product.code,
                    "name" to product.name,
                    "searchName" to product.searchName,
                    "category" to product.category,
                    "unit" to product.unit,
                    "imageUrl" to product.imageUrl,
                    "searchCount" to product.searchCount,
                    "timestamp" to System.currentTimeMillis()
                )).await()
            Log.d("ProductSync", "Produto salvo no Firestore: ${product.code}")
            true
        } catch (e: Exception) {
            Log.e("ProductSync", "Erro ao salvar no Firestore: ${product.code}", e)
            false
        }
    }
        
    suspend fun deleteProduct(code: String): Boolean {
        ensureAuthenticated()
        if (!isFirebaseConfigured()) return false
        return try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("products").document(code).delete().await()
            Log.d("ProductSync", "Produto excluído do Firestore: $code")
            true
        } catch (e: Exception) {
            Log.e("ProductSync", "Erro ao excluir do Firestore: $code", e)
            false
        }
    }


    suspend fun syncAllProducts(products: List<com.example.data.Product>) {
        ensureAuthenticated()
        if (!isFirebaseConfigured()) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            products.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { product ->
                    val docRef = firestore.collection("products").document(product.code)
                    batch.set(docRef, mapOf(
                        "code" to product.code,
                        "name" to product.name,
                        "searchName" to product.searchName,
                        "category" to product.category,
                        "unit" to product.unit,
                        "imageUrl" to product.imageUrl,
                        "searchCount" to product.searchCount,
                        "timestamp" to System.currentTimeMillis()
                    ))
                }
                batch.commit().await()
            }
            
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error in syncAllProducts", e)
        }
    }
    suspend fun getAllProducts(): List<com.example.data.Product> {
        if (!isFirebaseConfigured()) return emptyList()
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("products").get().await()
            snapshot.documents.mapNotNull { doc ->
                val code = doc.getString("code") ?: return@mapNotNull null
                val name = doc.getString("name") ?: ""
                val searchName = doc.getString("searchName") ?: ""
                val category = doc.getString("category") ?: ""
                val unit = doc.getString("unit") ?: "un"
                val imageUrl = doc.getString("imageUrl")
                val searchCount = doc.getLong("searchCount")?.toInt() ?: 0
                com.example.data.Product(
                    code = code,
                    name = name,
                    searchName = searchName,
                    category = category,
                    unit = unit,
                    imageUrl = imageUrl,
                    searchCount = searchCount
                )
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error fetching products", e)
            emptyList()
        }
    }

    suspend fun getMaintenanceSummary(
        localProductCount: Int,
        localCategoryCounts: List<CategoryCount>
    ): MaintenanceSummary {
        if (!isFirebaseConfigured()) {
            return MaintenanceSummary(
                localProductCount = localProductCount,
                localCategoryCounts = localCategoryCounts,
                checkedAt = System.currentTimeMillis(),
                remoteAvailable = false
            )
        }
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val productSnapshot = firestore.collection("products").get().await()
            val categoryCounts = productSnapshot.documents
                .map { it.getString("category").orEmpty().ifBlank { "Sem categoria" } }
                .groupingBy { it }
                .eachCount()
                .map { CategoryCount(it.key, it.value) }
                .sortedByDescending { it.count }
            val dynamicTabCount = firestore.collection("dynamic_tabs").get().await().size()
            val pendingSuggestionCount = firestore.collection("suggestions")
                .whereEqualTo("status", ProductSuggestion.STATUS_PENDING)
                .get()
                .await()
                .size()
            MaintenanceSummary(
                localProductCount = localProductCount,
                remoteProductCount = productSnapshot.size(),
                localCategoryCounts = localCategoryCounts,
                remoteCategoryCounts = categoryCounts,
                dynamicTabCount = dynamicTabCount,
                pendingSuggestionCount = pendingSuggestionCount,
                lastRemoteProductUpdate = productSnapshot.documents
                    .mapNotNull { it.getLong("timestamp") }
                    .maxOrNull(),
                checkedAt = System.currentTimeMillis(),
                remoteAvailable = true
            )
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao gerar diagnóstico de manutenção", e)
            MaintenanceSummary(
                localProductCount = localProductCount,
                localCategoryCounts = localCategoryCounts,
                checkedAt = System.currentTimeMillis(),
                remoteAvailable = false
            )
        }
    }

    suspend fun createCatalogSnapshot(reason: String = "manual"): CatalogSnapshot? {
        if (!isFirebaseConfigured() || !hasManagementAccess()) return null
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val productSnapshot = firestore.collection("products").get().await()
            val products = productSnapshot.documents.mapNotNull { productFromDocument(it) }
            if (products.size != productSnapshot.documents.size) {
                lastError = "O catálogo remoto contém documentos inválidos."
                return null
            }
            createCatalogSnapshotFromProducts(firestore, products, reason)?.also {
                runCatching { pruneCatalogHistory(firestore) }
                    .onFailure { Log.w("FirebaseService", "Não foi possível podar snapshots antigos", it) }
            }
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao criar snapshot do catálogo", e)
            null
        }
    }

    suspend fun getCatalogSnapshots(): List<CatalogSnapshot> {
        if (!isFirebaseConfigured() || !hasManagementAccess()) return emptyList()
        return try {
            FirebaseFirestore.getInstance()
                .collection("catalog_history")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_CATALOG_HISTORY_ITEMS.toLong())
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    val createdAt = document.getLong("createdAt") ?: return@mapNotNull null
                    CatalogSnapshot(
                        id = document.id,
                        createdAt = createdAt,
                        productCount = document.getLong("productCount")?.toInt() ?: 0,
                        createdBy = document.getString("createdBy") ?: "desconhecido",
                        reason = document.getString("reason") ?: "manual",
                        restoredAt = document.getLong("restoredAt")
                    )
                }
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao listar histórico do catálogo", e)
            emptyList()
        }
    }

    suspend fun restoreCatalogSnapshot(snapshotId: String): CatalogRestoreResult {
        if (!isFirebaseConfigured() || !hasManagementAccess()) {
            return CatalogRestoreResult(false, message = "A restauração exige acesso de Mestre ou Admin.")
        }
        if (snapshotId.isBlank()) {
            return CatalogRestoreResult(false, message = "Snapshot inválido.")
        }
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshotRef = firestore.collection("catalog_history").document(snapshotId)
            val metadata = snapshotRef.get().await()
            if (!metadata.exists()) {
                return CatalogRestoreResult(false, message = "Histórico não encontrado.")
            }
            val expectedCount = metadata.getLong("productCount")?.toInt() ?: -1
            if (expectedCount !in 0..MAX_CATALOG_HISTORY_PRODUCTS) {
                return CatalogRestoreResult(false, message = "Histórico fora do limite seguro de restauração.")
            }
            val targetProducts = snapshotRef.collection("products").get().await()
                .documents.mapNotNull { productFromDocument(it) }
            if (targetProducts.size != expectedCount || targetProducts.map { it.code }.toSet().size != targetProducts.size) {
                return CatalogRestoreResult(false, message = "Histórico incompleto ou com códigos duplicados. Nenhuma alteração foi feita.")
            }

            val currentSnapshot = firestore.collection("products").get().await()
            val currentProducts = currentSnapshot.documents.mapNotNull { productFromDocument(it) }
            if (currentProducts.size != currentSnapshot.documents.size ||
                currentProducts.size > MAX_CATALOG_HISTORY_PRODUCTS ||
                currentProducts.map { it.code }.toSet().size != currentProducts.size
            ) {
                return CatalogRestoreResult(false, message = "O catálogo atual contém dados fora do limite seguro. Nenhuma alteração foi feita.")
            }

            val safetySnapshot = createCatalogSnapshotFromProducts(firestore, currentProducts, "pre_restoration")
            if (safetySnapshot == null) {
                return CatalogRestoreResult(false, message = "Não foi possível criar um backup de segurança. Nenhuma alteração foi feita.")
            }

            val targetCodes = targetProducts.map { it.code }.toSet()
            currentSnapshot.documents
                .filter { document -> document.getString("code") !in targetCodes }
                .chunked(400)
                .forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            targetProducts.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { product ->
                    batch.set(
                        firestore.collection("products").document(product.code),
                        productToMap(product)
                    )
                }
                batch.commit().await()
            }
            val restoredAt = System.currentTimeMillis()
            snapshotRef.update(
                mapOf(
                    "restoredAt" to restoredAt,
                    "restoredBy" to com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email.orEmpty()
                )
            ).await()
            runCatching { pruneCatalogHistory(firestore, setOf(snapshotRef.id, safetySnapshot.id)) }
                .onFailure { Log.w("FirebaseService", "Não foi possível podar snapshots antigos após restauração", it) }
            CatalogRestoreResult(
                success = true,
                restoredProductCount = targetProducts.size,
                message = "Catálogo restaurado com ${targetProducts.size} produto(s). Backup de segurança criado.",
                restoredProducts = targetProducts
            )
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao restaurar snapshot do catálogo", e)
            CatalogRestoreResult(false, message = "Falha na restauração. Os dados podem exigir nova sincronização.")
        }
    }

    private suspend fun createCatalogSnapshotFromProducts(
        firestore: FirebaseFirestore,
        products: List<Product>,
        reason: String
    ): CatalogSnapshot? {
        if (products.size > MAX_CATALOG_HISTORY_PRODUCTS) {
            lastError = "O catálogo excede o limite seguro de $MAX_CATALOG_HISTORY_PRODUCTS produtos."
            return null
        }
        val createdAt = System.currentTimeMillis()
        val snapshotRef = firestore.collection("catalog_history").document(UUID.randomUUID().toString())
        val createdBy = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        return try {
            snapshotRef.set(
                mapOf(
                    "createdAt" to createdAt,
                    "productCount" to products.size,
                    "createdBy" to createdBy,
                    "reason" to reason
                )
            ).await()
            products.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { product ->
                    batch.set(snapshotRef.collection("products").document(), productToMap(product))
                }
                batch.commit().await()
            }
            CatalogSnapshot(
                id = snapshotRef.id,
                createdAt = createdAt,
                productCount = products.size,
                createdBy = createdBy,
                reason = reason
            )
        } catch (e: Exception) {
            runCatching { snapshotRef.delete().await() }
            throw e
        }
    }

    private suspend fun pruneCatalogHistory(
        firestore: FirebaseFirestore,
        protectedIds: Set<String> = emptySet()
    ) {
        val documents = firestore.collection("catalog_history")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .get()
            .await()
            .documents
        val excessCount = (documents.size - MAX_CATALOG_HISTORY_ITEMS).coerceAtLeast(0)
        documents.filterNot { it.id in protectedIds }.take(excessCount).forEach { document ->
            document.reference.collection("products").get().await().documents
                .chunked(400)
                .forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            document.reference.delete().await()
        }
    }

    private fun productFromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Product? {
        val code = document.getString("code")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = document.getString("name") ?: return null
        return Product(
            code = code,
            name = name,
            searchName = document.getString("searchName") ?: name,
            category = document.getString("category") ?: "",
            unit = document.getString("unit") ?: "un",
            imageUrl = document.getString("imageUrl"),
            searchCount = document.getLong("searchCount")?.toInt() ?: 0
        )
    }

    private fun productToMap(product: Product): Map<String, Any?> = mapOf(
        "code" to product.code,
        "name" to product.name,
        "searchName" to product.searchName,
        "category" to product.category,
        "unit" to product.unit,
        "imageUrl" to product.imageUrl,
        "searchCount" to product.searchCount,
        "timestamp" to System.currentTimeMillis()
    )

    fun observeProducts(): Flow<List<com.example.data.Product>> = callbackFlow {
        if (!isFirebaseConfigured()) {
            close()
            return@callbackFlow
        }
        val firestore = FirebaseFirestore.getInstance()
        val registration = firestore.collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Error in observeProducts", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val products = snapshot.documents.mapNotNull { doc ->
                        val code = doc.getString("code") ?: return@mapNotNull null
                        val name = doc.getString("name") ?: ""
                        val searchName = doc.getString("searchName") ?: ""
                        val category = doc.getString("category") ?: ""
                        val unit = doc.getString("unit") ?: "un"
                        val imageUrl = doc.getString("imageUrl")
                        val searchCount = doc.getLong("searchCount")?.toInt() ?: 0
                        com.example.data.Product(
                            code = code,
                            name = name,
                            searchName = searchName,
                            category = category,
                            unit = unit,
                            imageUrl = imageUrl,
                            searchCount = searchCount
                        )
                    }
                    trySend(products)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeLatestProduct(): Flow<Map<String, Any>?> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val firestore = FirebaseFirestore.getInstance()
        val registration = firestore.collection("latest_product").document("latest")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Error in observeProducts", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.data)
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }
    
    fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun initialize(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private val okHttpClient = OkHttpClient()

    private fun getMimeType(context: android.content.Context, uri: android.net.Uri): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: context.contentResolver.getType(uri) ?: "image/jpeg"
    }

    suspend fun uploadImageToStorage(uri: android.net.Uri, path: String): String? = withContext(Dispatchers.IO) {
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        
        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
            lastError = "Supabase não configurado"
            return@withContext null
        }
        
        val firebaseToken = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: ""

        try {
            val ctx = appContext ?: return@withContext null
            val contentResolver = ctx.contentResolver
            val mimeType = getMimeType(ctx, uri)
            
            // Limit check (50MB)
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                if (pfd.statSize > 50 * 1024 * 1024) {
                    lastError = "Imagem muito grande (máx 50MB)"
                    return@withContext null
                }
            }
            
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = inputStream.readBytes()
            inputStream.close()
            
            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("path", path)
                .addFormDataPart("file", "image.jpg", bytes.toRequestBody(mimeType.toMediaType()))
                .build()
            
            val url = "$supabaseUrl/functions/v1/upload-image"
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody) 
                .addHeader("Authorization", "Bearer $supabaseKey")
                .addHeader("x-firebase-token", firebaseToken)
                .build()
                
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseStr = response.body?.string()
                var parsedUrl: String? = null
                try {
                    if (!responseStr.isNullOrBlank()) {
                        val json = org.json.JSONObject(responseStr)
                        if (json.has("url")) {
                            parsedUrl = json.getString("url")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseStorage", "Error parsing response", e)
                }
                
                if (parsedUrl.isNullOrBlank() || (!parsedUrl.startsWith("http://") && !parsedUrl.startsWith("https://")) || parsedUrl.contains("null", ignoreCase = true)) {
                    lastError = "Resposta inválida do servidor de upload."
                    return@withContext null
                }
                
                return@withContext parsedUrl
            } else {
                Log.e("SupabaseStorage", "Error uploading: ${response.code} ${response.message} ${response.body?.string()}")
                lastError = "Falha no upload da imagem."
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "Exception uploading", e)
            lastError = e.message
            return@withContext null
        }
    }

    suspend fun uploadBanner(uri: android.net.Uri): String? {
        return try {
            val ctx = appContext ?: return null
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(getMimeType(ctx, uri)) ?: "jpg"
            val path = "banners/hero_banner_${System.currentTimeMillis()}.$extension"
            val downloadUrl = uploadImageToStorage(uri, path)
            if (downloadUrl != null) {
                if (isFirebaseConfigured()) {
                    val firestore = FirebaseFirestore.getInstance()
                    firestore.collection("config").document("appSettings")
                        .set(mapOf("bannerUrl" to downloadUrl)).await()
                }
            }
            downloadUrl
        } catch (e: Exception) {
            lastError = e.message
            Log.e("SupabaseStorage", "Error uploading banner", e)
            null
        }
    }

    suspend fun setBannerUrlDirectly(url: String): String? {
        if (!isFirebaseConfigured()) {
            lastError = "Firebase not configured"
            return url
        }
        return try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("config").document("appSettings")
                .set(mapOf("bannerUrl" to url)).await()
            url
        } catch (e: Exception) {
            lastError = e.message
            url
        }
    }
    suspend fun getBannerUrl(): String? {
        if (!isFirebaseConfigured()) return null
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("config").document("appSettings").get().await()
            snapshot.getString("bannerUrl")
        } catch (e: Exception) {
            null
        }
    }
    fun observeBannerUrl(): Flow<String?> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        
        val firestore = FirebaseFirestore.getInstance()
        val registration = firestore.collection("config").document("appSettings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Error in observeProducts", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val url = snapshot.getString("bannerUrl")
                    trySend(url)
                } else {
                    trySend(null)
                }
            }
            
        awaitClose { registration.remove() }
    }

    fun observeHomeSettings(): Flow<RemoteHomeSettings> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(RemoteHomeSettings())
            close()
            return@callbackFlow
        }

        val firestore = FirebaseFirestore.getInstance()
        val registration = firestore.collection("config").document("appSettings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Erro ao observar configurações da Home", error)
                    return@addSnapshotListener
                }
                trySend(
                    RemoteHomeSettings(
                        showCategories = snapshot?.getBoolean("homeShowCategories"),
                        showMostUsed = snapshot?.getBoolean("homeShowMostUsed"),
                        showHistory = snapshot?.getBoolean("homeShowHistory"),
                        showFavorites = snapshot?.getBoolean("homeShowFavorites"),
                        mostUsedLimit = snapshot?.getLong("homeMostUsedLimit")?.toInt(),
                        carouselIntervalSeconds = snapshot?.getLong("homeCarouselIntervalSeconds")?.toInt()
                    )
                )
            }

        awaitClose { registration.remove() }
    }

    suspend fun saveHomeSettings(settings: HomeSettings): Boolean {
        if (!isFirebaseConfigured() || !hasManagementAccess()) return false
        return try {
            FirebaseFirestore.getInstance()
                .collection("config")
                .document("appSettings")
                .set(
                    mapOf(
                        "homeShowCategories" to settings.showCategories,
                        "homeShowMostUsed" to settings.showMostUsed,
                        "homeShowHistory" to settings.showHistory,
                        "homeShowFavorites" to settings.showFavorites,
                        "homeMostUsedLimit" to settings.mostUsedLimit.coerceIn(1, 50),
                        "homeCarouselIntervalSeconds" to settings.carouselIntervalSeconds.coerceIn(3, 30)
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao salvar configurações da Home", e)
            false
        }
    }

    private fun hasManagementAccess(): Boolean {
        val email = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.lowercase()
        return email == "admin@nrdlojas.com" || email == "mestre@nrdlojas.com"
    }

    private fun parseThemeBackgrounds(raw: Any?): Map<String, List<ThemeBackground>> {
        val rawMap = raw as? Map<*, *> ?: return emptyMap()
        return SupportedThemeKeys.mapNotNull { themeKey ->
            val items = (rawMap[themeKey] as? List<*>)
                ?.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val id = map["id"] as? String ?: return@mapNotNull null
                    val url = (map["url"] as? String)
                        ?.trim()
                        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?: return@mapNotNull null
                    ThemeBackground(
                        id = id,
                        label = (map["label"] as? String)?.trim().orEmpty().ifBlank { "Fundo personalizado" },
                        url = url,
                        isActive = map["isActive"] as? Boolean ?: false
                    )
                }
                .orEmpty()
                .take(5)
            if (items.isNullOrEmpty()) null else themeKey to items
        }.toMap()
    }

    fun observeAppearanceSettings(): Flow<AppearanceSettings> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(AppearanceSettings())
            close()
            return@callbackFlow
        }

        val registration = FirebaseFirestore.getInstance()
            .collection("config")
            .document("appSettings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Erro ao observar aparência", error)
                    return@addSnapshotListener
                }
                trySend(
                    AppearanceSettings(
                        overrideLocalTheme = snapshot?.getBoolean("appearanceOverrideLocalTheme") ?: false,
                        theme = snapshot?.getString("appearanceTheme")
                            ?.takeIf { it in setOf("multicolor", "red", "gold", "green", "blue", "orange") }
                            ?: "multicolor",
                        appearanceMode = snapshot?.getString("appearanceMode")
                            ?.takeIf { it in setOf("system", "light", "dark") }
                            ?: "system",
                        themeBackgrounds = parseThemeBackgrounds(snapshot?.get("appearanceThemeBackgrounds"))
                    )
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveAppearanceSettings(settings: AppearanceSettings): Boolean {
        if (!isFirebaseConfigured() || !hasManagementAccess()) return false
        val safeTheme = settings.theme.takeIf {
            it in setOf("multicolor", "red", "gold", "green", "blue", "orange")
        } ?: "multicolor"
                val safeMode = settings.appearanceMode.takeIf {
            it in setOf("system", "light", "dark")
        } ?: "system"
        val safeBackgrounds = SupportedThemeKeys.associateWith { themeKey ->
            var activeFound = false
            settings.themeBackgrounds[themeKey]
                .orEmpty()
                .filter { background ->
                    background.url.startsWith("https://") || background.url.startsWith("http://")
                }
                .take(5)
                .map { background ->
                    val isActive = background.isActive && !activeFound
                    if (isActive) activeFound = true
                    mapOf(
                        "id" to background.id.ifBlank { UUID.randomUUID().toString() },
                        "label" to background.label.trim().take(80).ifBlank { "Fundo personalizado" },
                        "url" to background.url.trim(),
                        "isActive" to isActive
                    )
                }
        }
        return try {

            FirebaseFirestore.getInstance()
                .collection("config")
                .document("appSettings")
                .set(
                    mapOf(
                        "appearanceOverrideLocalTheme" to settings.overrideLocalTheme,
                        "appearanceTheme" to safeTheme,
                        "appearanceMode" to safeMode,
                        "appearanceThemeBackgrounds" to safeBackgrounds
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao salvar aparência", e)
            false
        }
    }

    fun observeNotificationSettings(): Flow<NotificationSettings> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(NotificationSettings())
            close()
            return@callbackFlow
        }

        val registration = FirebaseFirestore.getInstance()
            .collection("config")
            .document("appSettings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Erro ao observar notificações", error)
                    return@addSnapshotListener
                }
                trySend(
                    NotificationSettings(
                        enabled = snapshot?.getBoolean("notificationsEnabled") ?: true,
                        productAddedEnabled = snapshot?.getBoolean("notificationsProductAddedEnabled") ?: true,
                        codeChangedEnabled = snapshot?.getBoolean("notificationsCodeChangedEnabled") ?: true,
                        suggestionFixedEnabled = snapshot?.getBoolean("notificationsSuggestionFixedEnabled") ?: true,
                        appUpdateEnabled = snapshot?.getBoolean("notificationsAppUpdateEnabled") ?: true,
                        promotionUpdatedEnabled = snapshot?.getBoolean("notificationsPromotionUpdatedEnabled") ?: true
                    )
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun getNotificationSettings(): NotificationSettings {
        if (!isFirebaseConfigured()) return NotificationSettings()
        return try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("config")
                .document("appSettings")
                .get()
                .await()
            NotificationSettings(
                enabled = snapshot.getBoolean("notificationsEnabled") ?: true,
                productAddedEnabled = snapshot.getBoolean("notificationsProductAddedEnabled") ?: true,
                codeChangedEnabled = snapshot.getBoolean("notificationsCodeChangedEnabled") ?: true,
                suggestionFixedEnabled = snapshot.getBoolean("notificationsSuggestionFixedEnabled") ?: true,
                appUpdateEnabled = snapshot.getBoolean("notificationsAppUpdateEnabled") ?: true,
                promotionUpdatedEnabled = snapshot.getBoolean("notificationsPromotionUpdatedEnabled") ?: true
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "Erro ao ler política de notificações", e)
            NotificationSettings()
        }
    }

    suspend fun saveNotificationSettings(settings: NotificationSettings): Boolean {
        if (!isFirebaseConfigured() || !hasManagementAccess()) return false
        return try {
            FirebaseFirestore.getInstance()
                .collection("config")
                .document("appSettings")
                .set(
                    mapOf(
                        "notificationsEnabled" to settings.enabled,
                        "notificationsProductAddedEnabled" to settings.productAddedEnabled,
                        "notificationsCodeChangedEnabled" to settings.codeChangedEnabled,
                        "notificationsSuggestionFixedEnabled" to settings.suggestionFixedEnabled,
                        "notificationsAppUpdateEnabled" to settings.appUpdateEnabled,
                        "notificationsPromotionUpdatedEnabled" to settings.promotionUpdatedEnabled
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao salvar política de notificações", e)
            false
        }
    }

    fun observeAssistantSettings(): Flow<AssistantSettings> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(AssistantSettings())
            close()
            return@callbackFlow
        }

        val registration = FirebaseFirestore.getInstance()
            .collection("config")
            .document("appSettings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Erro ao observar configurações do Assistente", error)
                    return@addSnapshotListener
                }
                trySend(
                    AssistantSettings(
                        enabled = snapshot?.getBoolean("assistantEnabled") ?: true,
                        catalogOnly = snapshot?.getBoolean("assistantCatalogOnly") ?: true,
                        welcomeMessage = snapshot?.getString("assistantWelcomeMessage")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: AssistantSettings().welcomeMessage,
                        maxContextProducts = snapshot?.getLong("assistantMaxContextProducts")
                            ?.toInt()
                            ?.coerceIn(5, 50)
                            ?: 25
                    )
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun getAssistantSettings(): AssistantSettings {
        if (!isFirebaseConfigured()) return AssistantSettings()
        return try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("config")
                .document("appSettings")
                .get()
                .await()
            AssistantSettings(
                enabled = snapshot.getBoolean("assistantEnabled") ?: true,
                catalogOnly = snapshot.getBoolean("assistantCatalogOnly") ?: true,
                welcomeMessage = snapshot.getString("assistantWelcomeMessage")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: AssistantSettings().welcomeMessage,
                maxContextProducts = snapshot.getLong("assistantMaxContextProducts")
                    ?.toInt()
                    ?.coerceIn(5, 50)
                    ?: 25
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "Erro ao ler configurações do Assistente", e)
            AssistantSettings()
        }
    }

    suspend fun saveAssistantSettings(settings: AssistantSettings): Boolean {
        if (!isFirebaseConfigured() || !hasManagementAccess()) return false
        return try {
            FirebaseFirestore.getInstance()
                .collection("config")
                .document("appSettings")
                .set(
                    mapOf(
                        "assistantEnabled" to settings.enabled,
                        "assistantCatalogOnly" to settings.catalogOnly,
                        "assistantWelcomeMessage" to settings.welcomeMessage.trim().take(160),
                        "assistantMaxContextProducts" to settings.maxContextProducts.coerceIn(5, 50)
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao salvar configurações do Assistente", e)
            false
        }
    }

    fun observeCategories(): Flow<List<CategoryDefinition>> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(CategoryDefinition.defaults)
            close()
            return@callbackFlow
        }

        val firestore = FirebaseFirestore.getInstance()
        val registration = firestore.collection("config").document("appSettings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Erro ao observar categorias", error)
                    return@addSnapshotListener
                }

                val rawCategories = snapshot?.get("categories") as? List<*> ?: emptyList<Any?>()
                val parsed = rawCategories.mapNotNull { raw ->
                    val map = raw as? Map<*, *> ?: return@mapNotNull null
                    val id = map["id"] as? String ?: return@mapNotNull null
                    val name = map["name"] as? String ?: return@mapNotNull null
                    if (name.isBlank()) return@mapNotNull null
                    CategoryDefinition(
                        id = id,
                        name = name.trim(),
                        displayOrder = (map["displayOrder"] as? Number)?.toInt() ?: 0,
                        isActive = map["isActive"] as? Boolean ?: true
                    )
                }.sortedWith(compareBy<CategoryDefinition> { it.displayOrder }.thenBy { it.name })

                trySend(parsed.ifEmpty { CategoryDefinition.defaults })
            }

        awaitClose { registration.remove() }
    }

    suspend fun saveCategories(categories: List<CategoryDefinition>): Boolean {
        if (!isFirebaseConfigured() || !hasManagementAccess()) return false
        val normalized = categories
            .filter { it.name.isNotBlank() }
            .mapIndexed { index, category ->
                mapOf(
                    "id" to category.id,
                    "name" to category.name.trim(),
                    "displayOrder" to index,
                    "isActive" to category.isActive
                )
            }
        if (normalized.isEmpty()) return false
        return try {
            FirebaseFirestore.getInstance()
                .collection("config")
                .document("appSettings")
                .set(
                    mapOf("categories" to normalized),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao salvar categorias", e)
            false
        }
    }

    suspend fun renameProductsCategory(oldName: String, newName: String): Boolean {
        if (!isFirebaseConfigured() || !hasManagementAccess()) return false
        if (oldName.trim().equals(newName.trim(), ignoreCase = true)) return true
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val products = firestore.collection("products")
                .whereEqualTo("category", oldName.trim())
                .get()
                .await()
            products.documents.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { document ->
                    batch.update(document.reference, "category", newName.trim())
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao renomear categoria nos produtos", e)
            false
        }
    }

    suspend fun updateProductsCategory(codes: List<String>, category: String): Boolean {
        if (!isFirebaseConfigured() || !hasManagementAccess() || codes.isEmpty() || category.isBlank()) return false
        return try {
            val firestore = FirebaseFirestore.getInstance()
            codes.map { it.trim() }.filter { it.isNotBlank() }.distinct().chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { code ->
                    batch.update(firestore.collection("products").document(code), "category", category.trim())
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao alterar categorias em lote", e)
            false
        }
    }

    suspend fun deleteProducts(codes: List<String>): Boolean {
        if (!isFirebaseConfigured() || !hasManagementAccess() || codes.isEmpty()) return false
        return try {
            val firestore = FirebaseFirestore.getInstance()
            codes.map { it.trim() }.filter { it.isNotBlank() }.distinct().chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { code ->
                    batch.delete(firestore.collection("products").document(code))
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao excluir produtos em lote", e)
            false
        }
    }

    suspend fun saveProductsBatch(products: List<com.example.data.Product>): Boolean {
        if (!isFirebaseConfigured() || !hasManagementAccess() || products.isEmpty()) return false
        return try {
            val firestore = FirebaseFirestore.getInstance()
            products.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { product ->
                    batch.set(
                        firestore.collection("products").document(product.code),
                        mapOf(
                            "code" to product.code,
                            "name" to product.name,
                            "searchName" to product.searchName,
                            "category" to product.category,
                            "unit" to product.unit,
                            "imageUrl" to product.imageUrl,
                            "searchCount" to product.searchCount,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseService", "Erro ao importar produtos em lote", e)
            false
        }
    }


    suspend fun syncAllDynamicTabs(tabs: List<com.example.data.DynamicTab>) {
        if (!isFirebaseConfigured()) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val batch = firestore.batch()
            tabs.forEach { tab ->
                val docRef = firestore.collection("dynamic_tabs").document(tab.id.toString())
                batch.set(docRef, mapOf(
                    "id" to tab.id,
                    "title" to tab.title,
                    "type" to tab.type,
                    "content" to tab.content,
                    "displayOrder" to tab.displayOrder
                ))
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error syncing dynamic tabs", e)
        }
    }

    suspend fun deleteDynamicTab(tab: com.example.data.DynamicTab) {
        if (!isFirebaseConfigured()) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("dynamic_tabs").document(tab.id.toString())
                .delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error deleting dynamic tab", e)
        }
    }

    suspend fun submitSuggestion(text: String, installationId: String): Boolean {
        val cleanText = text.trim()
        if (cleanText.isBlank() || !isFirebaseConfigured()) return false
        return try {
            val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val submittedBy = authUser?.email ?: "Usuário do aplicativo"
            val submittedByUid = authUser?.uid.orEmpty()
            val suggestionRef = FirebaseFirestore.getInstance().collection("suggestions").document()
            suggestionRef.set(mapOf(
                "text" to cleanText,
                "submittedBy" to submittedBy,
                "submittedByUid" to submittedByUid,
                "installationId" to installationId,
                "appVersion" to BuildConfig.VERSION_NAME,
                "createdAt" to System.currentTimeMillis(),
                "status" to ProductSuggestion.STATUS_PENDING
            )).await()
            Log.d("FirebaseService", "Sugestão enviada: ${suggestionRef.id}")
            true
        } catch (e: Exception) {
            Log.e("FirebaseService", "Erro ao enviar sugestão", e)
            false
        }
    }

    fun observeSuggestions(): Flow<List<ProductSuggestion>> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = FirebaseFirestore.getInstance()
            .collection("suggestions")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Erro ao observar sugestões", error)
                    return@addSnapshotListener
                }
                val suggestions = snapshot?.documents?.map { doc ->
                    ProductSuggestion(
                        id = doc.id,
                        text = doc.getString("text") ?: "",
                        submittedBy = doc.getString("submittedBy") ?: "Usuário do aplicativo",
                        submittedByUid = doc.getString("submittedByUid") ?: "",
                        appVersion = doc.getString("appVersion") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        status = doc.getString("status") ?: ProductSuggestion.STATUS_PENDING
                    )
                }.orEmpty()
                trySend(suggestions)
            }
        awaitClose { registration.remove() }
    }

    fun observePublicSuggestions(): Flow<List<ProductSuggestion>> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = FirebaseFirestore.getInstance()
            .collection("suggestions")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Erro ao observar histórico público de sugestões", error)
                    return@addSnapshotListener
                }
                val suggestions = snapshot?.documents?.map { doc ->
                    ProductSuggestion(
                        id = doc.id,
                        text = doc.getString("text") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        status = doc.getString("status") ?: ProductSuggestion.STATUS_PENDING
                    )
                }.orEmpty()
                trySend(suggestions)
            }
        awaitClose { registration.remove() }
    }

    fun observeUserSuggestions(installationId: String): Flow<List<ProductSuggestion>> = callbackFlow {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (!isFirebaseConfigured() || (uid.isNullOrBlank() && installationId.isBlank())) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val suggestionsQuery = FirebaseFirestore.getInstance()
            .collection("suggestions")
            .let { collection ->
                if (!uid.isNullOrBlank()) {
                    collection.whereEqualTo("submittedByUid", uid)
                } else {
                    collection.whereEqualTo("installationId", installationId)
                }
            }
        val registration = suggestionsQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirebaseService", "Erro ao observar sugestões do usuário", error)
                return@addSnapshotListener
            }
            val suggestions = snapshot?.documents?.map { doc ->
                ProductSuggestion(
                    id = doc.id,
                    text = doc.getString("text") ?: "",
                    submittedBy = doc.getString("submittedBy") ?: "Usuário do aplicativo",
                    submittedByUid = doc.getString("submittedByUid") ?: "",
                    installationId = doc.getString("installationId") ?: "",
                    appVersion = doc.getString("appVersion") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    status = doc.getString("status") ?: ProductSuggestion.STATUS_PENDING
                )
            }.orEmpty().sortedByDescending { it.createdAt }
            trySend(suggestions)
        }
        awaitClose { registration.remove() }
    }

    suspend fun deleteSuggestion(id: String, installationId: String): Boolean {
        if (id.isBlank() || !isFirebaseConfigured()) return false
        return try {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val ref = FirebaseFirestore.getInstance().collection("suggestions").document(id)
            val snapshot = ref.get().await()
            val ownerMatches = if (!uid.isNullOrBlank()) {
                snapshot.getString("submittedByUid") == uid
            } else {
                snapshot.getString("installationId") == installationId
            }
            if (!snapshot.exists() || !ownerMatches || snapshot.getString("status") != ProductSuggestion.STATUS_FIXED) {
                Log.w("FirebaseService", "Exclusão de sugestão rejeitada: $id")
                return false
            }
            ref.delete().await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseService", "Erro ao excluir sugestão: $id", e)
            false
        }
    }

    suspend fun updateSuggestionStatus(id: String, status: String): Boolean {
        if (id.isBlank() || status !in setOf(ProductSuggestion.STATUS_PENDING, ProductSuggestion.STATUS_FIXED) || !isFirebaseConfigured()) return false
        return try {
            val suggestionRef = FirebaseFirestore.getInstance().collection("suggestions").document(id)
            val previous = suggestionRef.get().await()
            val previousStatus = previous.getString("status") ?: ProductSuggestion.STATUS_PENDING
            suggestionRef.update("status", status).await()
            if (status == ProductSuggestion.STATUS_FIXED && previousStatus != ProductSuggestion.STATUS_FIXED) {
                val installationId = previous.getString("installationId").orEmpty()
                val topic = com.example.util.FcmTopicSubscription.suggestionTopicForInstallation(installationId)
                publishSuggestionFixedEvent(topic, previous.getString("text").orEmpty())
            }
            true
        } catch (e: Exception) {
            Log.e("FirebaseService", "Erro ao atualizar status da sugestão: $id", e)
            false
        }
    }

    fun observeDynamicTabs(): Flow<List<com.example.data.DynamicTab>> = callbackFlow {
        if (!isFirebaseConfigured()) {
            close()
            return@callbackFlow
        }
        val firestore = FirebaseFirestore.getInstance()
        val registration = firestore.collection("dynamic_tabs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseService", "Error in observeProducts", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val tabs = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getLong("id")?.toInt() ?: return@mapNotNull null
                        val title = doc.getString("title") ?: ""
                        val type = doc.getString("type") ?: ""
                        val content = doc.getString("content") ?: ""
                        val displayOrder = doc.getLong("displayOrder")?.toInt() ?: 0
                        com.example.data.DynamicTab(
                            id = id,
                            title = title,
                            type = type,
                            content = content,
                            displayOrder = displayOrder
                        )
                    }
                    trySend(tabs)
                }
            }
        awaitClose { registration.remove() }
    }
}
