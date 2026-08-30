package com.example.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Operações administrativas que precisam de garantias extras de integridade.
 * As regras do Firestore continuam sendo a autoridade final de permissão.
 */
object CatalogAdminOperations {
    private const val TAG = "CatalogAdminOps"
    private const val MAX_TRANSACTION_PRODUCTS = 180
    private const val MAX_BATCH_PRODUCTS = 400

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

    suspend fun createProductIfAbsent(product: Product): CreateProductResult {
        if (!FirebaseService.isFirebaseConfigured() || product.code.isBlank()) {
            return CreateProductResult(false, message = "Nuvem indisponível ou código inválido.")
        }
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val ref = firestore.collection("products").document(product.code.trim())
            val created = firestore.runTransaction { transaction ->
                val current = transaction.get(ref)
                if (current.exists()) {
                    false
                } else {
                    transaction.set(ref, product.toRemoteMap())
                    true
                }
            }.await()
            if (created) CreateProductResult(true)
            else CreateProductResult(false, duplicate = true, message = "Código já cadastrado na nuvem.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar produto de forma atômica", e)
            CreateProductResult(false, message = e.message)
        }
    }

    suspend fun replaceProductCodeAtomically(oldCode: String, product: Product): CreateProductResult {
        if (!FirebaseService.isFirebaseConfigured() || oldCode.isBlank() || product.code.isBlank()) {
            return CreateProductResult(false, message = "Nuvem indisponível ou código inválido.")
        }
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val oldRef = firestore.collection("products").document(oldCode.trim())
            val newRef = firestore.collection("products").document(product.code.trim())
            val changed = firestore.runTransaction { transaction ->
                val oldSnapshot = transaction.get(oldRef)
                val newSnapshot = transaction.get(newRef)
                if (!oldSnapshot.exists()) {
                    throw IllegalStateException("O produto original não existe mais na nuvem.")
                }
                if (newSnapshot.exists()) {
                    false
                } else {
                    transaction.set(newRef, product.toRemoteMap())
                    transaction.delete(oldRef)
                    true
                }
            }.await()
            if (changed) CreateProductResult(true)
            else CreateProductResult(false, duplicate = true, message = "O novo código já está em uso na nuvem.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao trocar código de forma atômica", e)
            CreateProductResult(false, message = e.message)
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

    private fun Product.toRemoteMap(): Map<String, Any?> = mapOf(
        "code" to code.trim(),
        "name" to name,
        "searchName" to searchName,
        "category" to category,
        "unit" to unit,
        "imageUrl" to imageUrl,
        "searchCount" to searchCount,
        "timestamp" to System.currentTimeMillis(),
    )
}
