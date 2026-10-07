package com.example.data.promotions

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.Promotion
import com.example.data.PromotionProduct
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PromotionDatabaseTest {
    @Test fun failedTransactionNeverPublishesOfferWithoutMatchingRevision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, PromotionDatabase::class.java).build()
        try {
            try {
                database.withTransaction {
                    database.promotions().upsertOffers(listOf(CachedOffer("0012|1", "{}")))
                    database.promotions().put(PromotionMetadata("acp_revision", "next"))
                    error("Simulated interruption before complete snapshot")
                }
            } catch (_: IllegalStateException) { }
            assertTrue(database.promotions().offers().isEmpty())
            assertNull(database.promotions().metadata("acp_revision"))
            assertNull(database.promotions().metadata("initialized"))
        } finally { database.close() }
    }
    @Test fun reopeningDatabaseRestoresFullCommercialDetailsWithoutNetwork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "promotion-reopen-test.db"
        context.deleteDatabase(name)
        val offer = Promotion("0012|1", "De/Por", "Bebidas", null, "2026-10-01", "2026-10-31",
            listOf(PromotionProduct("1", "Água mineral", "R$ 2,00", "R$ 3,00", "33%", "0012", barcode = "3017620422003",
                detailsJson = "{\"stockQuantity\":20,\"dueDate\":\"2027-01-01\"}")))
        var database = Room.databaseBuilder(context, PromotionDatabase::class.java, name).build()
        database.withTransaction {
            database.promotions().upsertOffers(listOf(CachedOffer(offer.id, PromotionCodec.encode(offer))))
            database.promotions().put(PromotionMetadata("initialized", "1"))
        }
        database.close()
        database = Room.databaseBuilder(context, PromotionDatabase::class.java, name).build()
        try {
            assertEquals(offer, PromotionCodec.decode(database.promotions().offers().single().payload))
            assertEquals("1", database.promotions().metadata("initialized"))
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
