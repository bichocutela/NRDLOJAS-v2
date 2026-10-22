package com.example.data.promotions

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProductImageCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("ean_images_v1", Context.MODE_PRIVATE)
    private val ean = "3017620422003"

    @Before fun clearCache() { preferences.edit().clear().commit() }

    private fun cache(url: String, expires: Long = System.currentTimeMillis() + 60_000) {
        preferences.edit().putString("FOOD:$ean", JSONObject()
            .put("url", url).put("source", "catalog").put("credit", "credit")
            .put("expires", expires).toString()).commit()
    }

    @Test fun savedPhotoIsAvailableWhenCardIsRecreated() {
        cache("https://example.com/product.jpg")
        assertEquals("https://example.com/product.jpg",
            ProductImageRepository(context).cachedImage(ean, "Bebidas")?.url)
    }

    @Test fun cachedHitAndCachedMissBypassTheNetworkQueue() = runBlocking {
        val repository = ProductImageRepository(context)
        val field = repository.javaClass.getDeclaredField("gate").apply { isAccessible = true }
        val gate = field.get(repository) as kotlinx.coroutines.sync.Mutex
        gate.lock()
        try {
            cache("https://example.com/product.jpg")
            assertNotNull(withTimeout(2_000) { repository.find(ean, "Bebidas") })
            cache("")
            assertNull(withTimeout(2_000) { repository.find(ean, "Bebidas") })
        } finally { gate.unlock() }
    }

    @Test fun expiredWrongFamilyAndInvalidBarcodeDoNotReusePhotos() {
        val repository = ProductImageRepository(context)
        cache("https://example.com/product.jpg", 1L)
        assertNull(repository.cachedImage(ean, "Bebidas"))
        cache("https://example.com/product.jpg")
        assertNull(repository.cachedImage(ean, "Higiene e beleza"))
        assertNull(repository.cachedImage("3017620422004", "Bebidas"))
    }
}
