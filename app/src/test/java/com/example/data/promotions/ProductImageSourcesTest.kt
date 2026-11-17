package com.example.data.promotions

import org.junit.Assert.*
import org.junit.Test

class ProductImageSourcesTest {
    @Test fun defaultsPreserveRequestedOrder() {
        assertEquals(listOf("promofarma", "raia", "mercadolivre", "cosmos"), ProductImageSources.Policy().order)
        assertEquals(ProductImageSources.defaults.toSet(), ProductImageSources.Policy().enabled)
    }
    @Test fun remoteOrderAndDisabledSourcesAreRespected() {
        val policy = ProductImageSources.parse(listOf("mercadolivre", "raia", "promofarma"), listOf("raia", "promofarma"))
        assertEquals(listOf("raia", "promofarma"), policy.order.filter { it in policy.enabled })
        assertTrue(ProductImageSources.parse(null, emptyList<String>()).enabled.isEmpty())
    }
    @Test fun unknownProvidersAndDuplicateEntriesCannotChangeLookupStrategy() {
        val policy = ProductImageSources.parse(listOf("raia", "raia", "https://example.com"), listOf("raia", "untrusted"))
        assertEquals(listOf("raia"), policy.order)
        assertEquals(setOf("raia"), policy.enabled)
        assertEquals(ProductImageSources.Policy(), ProductImageSources.parse("malformed", "malformed"))
    }
    @Test fun absentProductsAndBlockedMarketContinueToCosmosInOrder() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<String>()
        val shown = mutableListOf<String>()
        val result = ProductImageSources.select(ProductImageSources.Policy(), "7891033777358", lookup = { source, code ->
            assertEquals("7891033777358", code)
            requests += source
            when (source) {
                "mercadolivre" -> PromofarmaImageRepository.Lookup(null, 300_000)
                "cosmos" -> PromofarmaImageRepository.Lookup("https://cdn-cosmos.bluesoft.com.br/products/$code")
                else -> PromofarmaImageRepository.Lookup(null)
            }
        }, display = { shown += it; true })
        assertEquals(ProductImageSources.defaults, requests)
        assertEquals(listOf("https://cdn-cosmos.bluesoft.com.br/products/7891033777358"), shown)
        assertTrue(result.displayed)
    }

    @Test fun firstValidImageStopsLookupAndDisabledSourcesAreNotQueried() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<String>()
        val policy = ProductImageSources.Policy(enabled = setOf("raia", "cosmos"))
        val result = ProductImageSources.select(policy, "7891033777358", lookup = { source, _ ->
            requests += source
            PromofarmaImageRepository.Lookup("https://product-data.raiadrogasil.io/images/3483834.webp")
        }, display = { true })
        assertTrue(result.displayed)
        assertEquals(listOf("raia"), requests)
    }

    @Test fun failedProviderDoesNotPreventLaterPhotoAndInvalidEanNeverStartsLookup() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<String>()
        val policy = ProductImageSources.Policy(order = listOf("mercadolivre", "cosmos"))
        val result = ProductImageSources.select(policy, "7891033777358", lookup = { source, _ ->
            requests += source
            if (source == "mercadolivre") throw java.io.IOException("unavailable")
            PromofarmaImageRepository.Lookup("https://cdn-cosmos.bluesoft.com.br/products/7891033777358")
        }, display = { true })
        assertTrue(result.displayed)
        assertEquals(listOf("mercadolivre", "cosmos"), requests)
        requests.clear()
        val invalid = ProductImageSources.select(policy, "250090", lookup = { source, _ ->
            requests += source; PromofarmaImageRepository.Lookup(null)
        }, display = { true })
        assertFalse(invalid.displayed)
        assertTrue(requests.isEmpty())
    }

}
