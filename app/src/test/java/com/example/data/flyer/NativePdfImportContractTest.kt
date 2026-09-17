package com.example.data.flyer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePdfImportContractTest {
    @Test
    fun flyerTextBlocks_keepNativeTableIdentifiersIntact() {
        val block = FlyerTextBlock(
            page = 1,
            text = "202896700 7891008121629 CHOC TAB GAROTO TALENTO TB 85G DOCE LEITE 13,99 8,49 16/09/2026 21/09/2026",
            left = 0,
            top = 0,
            right = 1000,
            bottom = 20,
            pageWidth = 1000,
            pageHeight = 10000
        )

        assertEquals(1, block.page)
        assertTrue(block.text.contains("7891008121629"))
        assertTrue(block.text.contains("13,99"))
        assertTrue(block.text.contains("16/09/2026"))
    }
}
