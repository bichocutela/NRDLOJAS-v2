package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreCatalogTest {
    @Test
    fun knownStoreCodeUsesFriendlyNameAndKeepsCodeAvailable() {
        assertEquals("Cidade Jardim", StoreCatalog.nameFor("0012"))
        assertEquals("Ponta Negra (0039)", StoreCatalog.labelFor("0039"))
    }

    @Test
    fun unknownStoreCodeUsesSafeFallback() {
        assertEquals("Loja 0099", StoreCatalog.nameFor("99"))
        assertEquals("Loja não informada", StoreCatalog.labelFor(""))
    }
}
