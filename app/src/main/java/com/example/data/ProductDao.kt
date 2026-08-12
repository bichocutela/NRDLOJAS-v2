package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

data class CategoryCount(
    val category: String,
    val count: Int
)

@Dao
interface ProductDao {
    @Query("SELECT category, COUNT(*) as count FROM products GROUP BY category ORDER BY count DESC")
    fun getProductsCountByCategory(): Flow<List<CategoryCount>>

    @Query("SELECT * FROM products WHERE code = :code LIMIT 1")
    suspend fun getProductByCodeSync(code: String): Product?

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsSync(): List<Product>

    @Query("SELECT * FROM products WHERE searchName LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%' ORDER BY searchCount DESC, name ASC")
    fun searchProducts(query: String): Flow<List<Product>>
    
    @Query("SELECT * FROM products WHERE searchName LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%' ORDER BY searchCount DESC, name ASC")
    suspend fun searchProductsSync(query: String): List<Product>

    @Query("SELECT * FROM products WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY searchCount DESC LIMIT 8")
    fun getMostUsed(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE lastSearchedAt > 0 ORDER BY lastSearchedAt DESC LIMIT 10")
    fun getHistory(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE category = :category ORDER BY name ASC")
    fun getProductsByCategory(category: String): Flow<List<Product>>

    @Update
    suspend fun updateProduct(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Delete
    suspend fun deleteProducts(products: List<Product>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)
    
    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("DELETE FROM products WHERE id NOT IN (SELECT MIN(id) FROM products GROUP BY code)")
    suspend fun deleteDuplicates()

    @Query("SELECT * FROM products ORDER BY id DESC LIMIT 1")
    fun getLatestProduct(): Flow<Product?>
}
