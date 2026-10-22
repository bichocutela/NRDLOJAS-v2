package com.example.data.promotions

import android.content.Context
import androidx.room.*
import com.example.data.Promotion
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Entity(tableName = "acp_rows")
internal data class CachedAcpRow(@PrimaryKey val id: String, val hash: String, val payload: String)
@Entity(tableName = "offers")
internal data class CachedOffer(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "promotion_metadata")
internal data class PromotionMetadata(@PrimaryKey val key: String, val value: String)

@Dao
internal interface PromotionDao {
    @Query("SELECT * FROM offers ORDER BY id") fun observeOffers(): Flow<List<CachedOffer>>
    @Query("SELECT * FROM offers ORDER BY id") suspend fun offers(): List<CachedOffer>
    @Query("SELECT * FROM acp_rows ORDER BY id") suspend fun rows(): List<CachedAcpRow>
    @Query("SELECT value FROM promotion_metadata WHERE `key` = :key") suspend fun metadata(key: String): String?
    @Query("SELECT * FROM promotion_metadata WHERE `key` = 'latest_added'") fun observeLatestAdded(): Flow<List<PromotionMetadata>>
    @Query("SELECT * FROM promotion_metadata WHERE `key` = 'initialized'") fun observeInitialized(): Flow<List<PromotionMetadata>>
    @Query("SELECT * FROM promotion_metadata WHERE `key` LIKE 'outbox_%' ORDER BY `key`") suspend fun outbox(): List<PromotionMetadata>
    @Query("DELETE FROM promotion_metadata WHERE `key` = :key") suspend fun deleteMetadata(key: String)
    @Upsert suspend fun upsertOffers(rows: List<CachedOffer>)
    @Upsert suspend fun upsertRows(rows: List<CachedAcpRow>)
    @Upsert suspend fun put(value: PromotionMetadata)
    @Query("DELETE FROM acp_rows WHERE id IN (:ids)") suspend fun deleteRows(ids: List<String>)
    @Query("DELETE FROM offers WHERE id IN (:ids)") suspend fun deleteOffers(ids: List<String>)
}

@Database(entities = [CachedAcpRow::class, CachedOffer::class, PromotionMetadata::class], version = 1, exportSchema = false)
internal abstract class PromotionDatabase : RoomDatabase() {
    abstract fun promotions(): PromotionDao
    companion object {
        @Volatile private var instance: PromotionDatabase? = null
        fun get(context: Context): PromotionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PromotionDatabase::class.java, "promotions.db")
                .build().also { instance = it }
        }
    }
}

internal object PromotionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(offer: Promotion): String = json.encodeToString(offer)
    fun decode(raw: String): Promotion = json.decodeFromString(raw)
    fun records(raw: String): List<StorePromotionRecord> = json.decodeFromString(raw)
    fun records(records: List<StorePromotionRecord>): String = json.encodeToString(records)
}
