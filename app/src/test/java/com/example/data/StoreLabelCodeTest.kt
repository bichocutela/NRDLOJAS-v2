package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreLabelCodeTest {
    @Test fun extractsSweetPotatoCodeFromEan13() {
        assertEquals("250007", storeProductLookupCode("2250007000383"))
    }

    @Test fun extractsMiniPastelCodeFromExtendedLabel() {
        assertEquals("257895", storeProductLookupCode("22578950131740000000"))
    }

    @Test fun preservesOrdinaryBarcodesAndDirectProductCodes() {
        for (code in listOf("7891000409640", "250007", "257895", "21026")) {
            assertEquals(code, storeProductLookupCode(code))
        }
    }

    @Test fun rejectsIncompleteLabelsAndInvalidEanChecksums() {
        for (code in listOf("2250007", "225000700038", "2250007000384", "2257895013174000000")) {
            assertEquals(code, storeProductLookupCode(code))
        }
    }

    @Test fun preservesOtherPrefixesAndTextSearches() {
        for (query in listOf("downy#1l#", "batata doce", "2350007000380", "A22578950131740000000")) {
            assertEquals(query, storeProductLookupCode(query))
        }
        assertEquals("250007", storeProductLookupCode(" 2250007000383 "))
    }
}
