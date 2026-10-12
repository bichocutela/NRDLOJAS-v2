package com.example.util

import org.junit.Assert.*
import org.junit.Test

class ProductNotificationContentTest {
    @Test fun internalCodeIsPreservedWithoutInventingEan() {
        val content = ProductNotificationContent.from(" QUEIJO REINO ", " 251783 ", null)
        assertEquals("251783", content.code)
        assertEquals("251783", content.barcode)
        assertEquals("QUEIJO REINO\nCódigo: 251783\nCódigo de barras: 251783", content.detail)
    }
    @Test fun explicitBarcodeIsPreserved() {
        val content = ProductNotificationContent.from("Vinho", "2012568001", "5604885098906")
        assertEquals("2012568001", content.code)
        assertEquals("5604885098906", content.barcode)
    }
    @Test fun legacyPayloadHasNoFakeCodeOrBarcode() {
        val content = ProductNotificationContent.from("Produto", null, null)
        assertEquals("Produto", content.detail)
        assertTrue(content.code.isEmpty())
        assertTrue(content.barcode.isEmpty())
    }
}
