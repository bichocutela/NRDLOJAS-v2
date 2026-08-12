package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Normalizer
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

    fun searchProducts(query: String): Flow<List<Product>> {
        val normalizedQuery = query.unaccent().lowercase().trim()
        val tokens = normalizedQuery.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        
        return dao.getAllProducts().map { products ->
            if (tokens.isEmpty()) return@map emptyList()
            
            products.filter { product ->
                val searchName = product.searchName
                val code = product.code
                val category = product.category.unaccent().lowercase()
                
                if (code.contains(normalizedQuery)) return@filter true
                
                tokens.all { token ->
                    searchName.contains(token) || isTypoMatch(token, searchName) || category.contains(token)
                }
            }.sortedWith(compareByDescending<Product> { it.searchCount }.thenBy { it.name })
        }
    }

    suspend fun getAllProductsSync() = dao.getAllProductsSync()
    suspend fun getProductByCodeSync(code: String) = dao.getProductByCodeSync(code)
    suspend fun cleanDuplicates() = dao.deleteDuplicates()

    suspend fun searchProductsSync(query: String): List<Product> {
        val normalizedQuery = query.unaccent().lowercase().trim()
        val tokens = normalizedQuery.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        
        val products = dao.getAllProductsSync()
        if (tokens.isEmpty()) return products
        
        return products.filter { product ->
            val searchName = product.searchName
            val code = product.code
            val category = product.category.unaccent().lowercase()
            
            if (code.contains(normalizedQuery)) return@filter true
            
            tokens.all { token ->
                searchName.contains(token) || isTypoMatch(token, searchName) || category.contains(token)
            }
        }.sortedWith(compareByDescending<Product> { it.searchCount }.thenBy { it.name })
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
        distance <= allowedTypos
    }
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
