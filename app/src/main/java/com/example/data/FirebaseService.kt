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
