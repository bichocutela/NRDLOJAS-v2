package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

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
    val mostUsed: Flow<List<Product>> = dao.getMostUsed()
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

    suspend fun searchProductsSync(query: String): List<Product> {
        val products = dao.getAllProductsSync()
        // The chat assistant uses searchProductsSync("") to build its full context.
        return if (query.isBlank()) products else rankProductsByRelevance(products, query)
    }
    
    fun getProductsByCategory(category: String): Flow<List<Product>> {
        return dao.getProductsByCategory(category)
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
        val searchName = product.searchName.unaccent().lowercase(Locale.ROOT).trim()
            .ifBlank { normalizedName }
        val normalizedCode = product.code.trim().lowercase(Locale.ROOT)
        val category = product.category.unaccent().lowercase(Locale.ROOT).trim()
        val nameWords = normalizedName.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        val relevance = when {
            normalizedCode == normalizedQuery -> 0
            normalizedCode.startsWith(normalizedQuery) -> 1
            normalizedCode.contains(normalizedQuery) -> 2
            normalizedName == normalizedQuery -> 3
            normalizedName.startsWith(normalizedQuery) -> 4
            nameWords.any { it.startsWith(normalizedQuery) } -> 5
            normalizedName.contains(normalizedQuery) -> 6
            tokens.all { token -> searchName.contains(token) } -> 7
            tokens.all { token -> isTypoMatch(token, searchName) } -> 8
            tokens.all { token -> category.contains(token) } -> 9
            tokens.all { token -> searchName.contains(token) || category.contains(token) } -> 7
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

fun isTypoMatch(token: String, target: String): Boolean {
    if (token.length <= 2) return target.contains(token)
    
    val targetWords = target.split("\\s+".toRegex())
    return targetWords.any { word ->
        if (word.contains(token) || token.contains(word)) return@any true
        
        // Allow up to 1 typo for words of length 3-4, and 2 typos for longer words
        val allowedTypos = if (token.length <= 4) 1 else 2
        val distance = levenshtein(token, word)
        distance <= allowedTypos || hasAdjacentTransposition(token, word)
    }
}

private fun hasAdjacentTransposition(lhs: String, rhs: String): Boolean {
    if (lhs.length != rhs.length || lhs.length < 2) return false
    for (index in 0 until lhs.lastIndex) {
        if (lhs[index] != rhs[index] || lhs[index + 1] != rhs[index + 1]) {
            return lhs[index] == rhs[index + 1] && lhs[index + 1] == rhs[index]
        }
    }
    return false
}

fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
    if (lhs == rhs) return 0
    if (lhs.isEmpty()) return rhs.length
    if (rhs.isEmpty()) return lhs.length

    val lhsLength = lhs.length + 1
    val rhsLength = rhs.length + 1

    var cost = IntArray(lhsLength)
    var newCost = IntArray(lhsLength)

    for (i in 0 until lhsLength) cost[i] = i

    for (j in 1 until rhsLength) {
        newCost[0] = j
        for (i in 1 until lhsLength) {
            val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
            val costReplace = cost[i - 1] + match
            val costInsert = cost[i] + 1
            val costDelete = newCost[i - 1] + 1
            newCost[i] = min(min(costInsert, costDelete), costReplace)
        }
        val swap = cost
        cost = newCost
        newCost = swap
    }
    return cost[lhsLength - 1]
}
