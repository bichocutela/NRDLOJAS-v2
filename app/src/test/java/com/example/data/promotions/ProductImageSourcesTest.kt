package com.example.data.promotions

import org.junit.Assert.*
import org.junit.Test

class ProductImageSourcesTest {
    @Test fun defaultsPreserveRequestedOrder() {
        assertEquals(listOf("promofarma", "raia", "mercadolivre"), ProductImageSources.Policy().order)
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
}
