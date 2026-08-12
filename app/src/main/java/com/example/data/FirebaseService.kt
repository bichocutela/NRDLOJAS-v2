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
        if (!isFirebaseConfigured()) return
        try {
            val supabaseUrl = BuildConfig.SUPABASE_URL
            val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
                Log.e("FirebaseService", "Supabase not configured, cannot send FCM")
                return
            }

            
            if (type != "NEW_PRODUCT" && type != "CODE_CHANGED") {
                // As per instructions: "Alterações somente de nome, categoria, foto ou outros campos NÃO devem disparar "Código alterado"."
                // We'll skip sending push for those to respect "Implemente SOMENTE o sistema de notificações de produtos."
                return
            }

            val title = if (type == "NEW_PRODUCT") "Produto adicionado" else "Código alterado"
            val text = productName

            val json = org.json.JSONObject()

            json.put("title", title)
            json.put("body", text)
            json.put("topic", "products")

            val requestBody = okhttp3.RequestBody.create("application/json".toMediaType(), json.toString())
            val firebaseToken = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: ""
            val request = okhttp3.Request.Builder()
                .url("$supabaseUrl/functions/v1/send-fcm")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .addHeader("x-firebase-token", firebaseToken)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("FirebaseService", "Error calling send-fcm: ${response.code} ${response.message} ${response.body?.string()}")
            } else {
                Log.d("FirebaseService", "FCM sent successfully")
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error publishing product event via FCM", e)
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
