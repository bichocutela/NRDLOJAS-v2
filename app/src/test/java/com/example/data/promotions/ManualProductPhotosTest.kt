package com.example.data.promotions

import org.junit.Assert.*
import org.junit.Test

class ManualProductPhotosTest {
    @Test fun barcodePhotoIsSharedWithInternalCodeViews() {
        assertEquals("https://example.com/manual.jpg", ManualProductPhotos.selected(
            mapOf("7891033777358" to "https://example.com/manual.jpg"), "123456", "07891033777358"))
    }

    @Test fun removalSuppressesOldPhotoAndAllowsOfficialFallback() {
        assertEquals("", ManualProductPhotos.selected(
            mapOf("7891033777358" to "", "123456" to "https://example.com/stale.jpg"),
            "123456", "7891033777358"))
    }

    @Test fun unknownProductDoesNotBorrowAnotherPhoto() {
        assertNull(ManualProductPhotos.selected(mapOf("123" to "https://example.com/a.jpg"), "456"))
    }
}
