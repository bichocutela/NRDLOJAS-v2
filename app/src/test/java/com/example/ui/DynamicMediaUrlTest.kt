package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicMediaUrlTest {

    @Test
    fun `converte link compartilhado do Drive para download direto`() {
        val input = "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view?usp=sharing"

        assertEquals(
            "https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrStUvWxYz",
            normalizeRemoteMediaUrl(input)
        )
    }

    @Test
    fun `converte link do Drive com id em query`() {
        val input = "https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz"

        assertEquals(
            "https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrStUvWxYz",
            normalizeRemoteMediaUrl(input)
        )
    }

    @Test
    fun `preserva url comum sem alteracao`() {
        val input = "https://cdn.exemplo.com/curso/aula01.mp4"
        assertEquals(input, normalizeRemoteMediaUrl(input))
    }

    @Test
    fun `remove espacos externos da url`() {
        assertEquals(
            "https://cdn.exemplo.com/manual.pdf",
            normalizeRemoteMediaUrl("  https://cdn.exemplo.com/manual.pdf  ")
        )
    }
}
