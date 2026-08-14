package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Normalizer
import java.util.Locale

class ProductRepository(
    private val dao: ProductDao,
    private val dynamicTabDao: DynamicTabDao? = null
) {
    fun getAllTabs() = dynamicTabDao?.getAllTabs() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun insertTab(tab: DynamicTab) { dynamicTabDao?.insertTab(tab) }
    suspend fun updateTab(tab: DynamicTab) { dynamicTabDao?.updateTab(tab) }
    suspend fun deleteTab(tab: DynamicTab) { dynamicTabDao?.deleteTab(tab) }

    val allProducts: Flow<List<Product>> = dao.getAllProducts()
    val favorites: Flow<List<Product>> = dao.getFavorites()
    fun mostUsed(limit: Int): Flow<List<Product>> = dao.getMostUsed(limit)
    val history: Flow<List<Product>> = dao.getHistory()
    val productsCountByCategory: Flow<List<CategoryCount>> = dao.getProductsCountByCategory()
    val latestProductLocal = dao.getLatestProduct()

    fun searchProducts(query: String): Flow<List<Product>> =
        dao.getAllProducts().map { products ->
            if (query.isBlank()) emptyList() else rankProductsByRelevance(products, query)
        }

    suspend fun getAllProductsSync() = dao.getAllProductsSync()
    suspend fun getProductByCodeSync(code: String) = dao.getProductByCodeSync(code)
    suspend fun cleanDuplicates() = dao.deleteDuplicates()
    suspend fun clearHistory() = dao.clearHistory()

    suspend fun searchProductsSync(query: String): List<Product> {
        val products = dao.getAllProductsSync()
        // The chat assistant uses searchProductsSync("") to build its full context.
        return if (query.isBlank()) products else rankProductsByRelevance(products, query)
    }
    
    fun getProductsByCategory(category: String): Flow<List<Product>> {
        return dao.getProductsByCategory(category)
    }

    fun searchProductsByCategory(category: String, query: String): Flow<List<Product>> =
        dao.getProductsByCategory(category).map { products ->
            if (query.isBlank()) products else rankProductsByRelevance(products, query)
        }

    suspend fun toggleFavorite(product: Product) {
        dao.updateProduct(product.copy(isFavorite = !product.isFavorite))
    }

    suspend fun registerSearch(product: Product) {
        dao.updateProduct(
            product.copy(
                searchCount = product.searchCount + 1,
                lastSearchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun insertProducts(products: List<Product>) {
        val existingProducts = dao.getAllProductsSync().associateBy { it.code }
        val updatedProducts = products.map { remote ->
            val local = existingProducts[remote.code]
            if (local != null) {
                remote.copy(
                    id = local.id,
                    isFavorite = local.isFavorite,
                    searchCount = local.searchCount,
                    lastSearchedAt = local.lastSearchedAt
                )
            } else {
                remote
            }
        }
        dao.insertProducts(updatedProducts)
    }

    suspend fun deleteProduct(product: Product) {
        dao.deleteProduct(product)
    }

    suspend fun deleteProducts(products: List<Product>) {
        dao.deleteProducts(products)
    }

    suspend fun insertProduct(product: Product) {
        dao.insertProduct(product)
    }

    suspend fun updateProduct(product: Product) {
        dao.updateProduct(product)
    }

    suspend fun populateInitialDataIfNeeded() {
        // Removido para forçar o download da nuvem (instalação nova)
    }
}

private data class SearchMatch(
    val product: Product,
    val relevance: Int,
    val normalizedName: String,
    val normalizedCode: String
)

internal fun rankProductsByRelevance(products: List<Product>, query: String): List<Product> {
    val normalizedQuery = query.unaccent().lowercase(Locale.ROOT).trim()
    if (normalizedQuery.isEmpty()) return emptyList()

    val tokens = normalizedQuery.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val ranked = products.mapNotNull { product ->
        val normalizedName = product.name.unaccent().lowercase(Locale.ROOT).trim()
        val normalizedCode = product.code.trim().lowercase(Locale.ROOT)
        val nameWords = normalizedName.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        val relevance = when {
            normalizedCode == normalizedQuery -> 0
            normalizedCode.startsWith(normalizedQuery) -> 1
            normalizedCode.contains(normalizedQuery) -> 2
            normalizedName == normalizedQuery -> 3
            normalizedName.startsWith(normalizedQuery) -> 4
            nameWords.any { it.startsWith(normalizedQuery) } -> 5
            normalizedName.contains(normalizedQuery) -> 6
            tokens.all { token -> normalizedName.contains(token) } -> 7
            else -> null
        } ?: return@mapNotNull null

        SearchMatch(product, relevance, normalizedName, normalizedCode)
    }

    return ranked.sortedWith(
        compareBy<SearchMatch> { it.relevance }
            .thenBy { it.normalizedName }
            .thenBy { it.product.name }
            .thenBy { it.normalizedCode }
            .thenByDescending { it.product.searchCount }
    ).map { it.product }
}

fun String.unaccent(): String {
    val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
    return regex.replace(temp, "")
}
