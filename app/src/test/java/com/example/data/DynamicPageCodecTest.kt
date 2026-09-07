package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicPageCodecTest {

    @Test
    fun `curso multimidia preserva URLs e ordem ao serializar`() {
        val blocks = listOf(
            DynamicPageBlock(type = DynamicPageBlock.TYPE_TEXT, value = "Módulo: Operação"),
            DynamicPageBlock(type = DynamicPageBlock.TYPE_TEXT, value = "Aula 1\nIntrodução"),
            DynamicPageBlock(type = DynamicPageBlock.TYPE_IMAGE, value = "https://example.supabase.co/storage/v1/object/public/nrdlojas-images/dynamic-pages/aula.webp"),
            DynamicPageBlock(type = DynamicPageBlock.TYPE_VIDEO, value = "https://example.supabase.co/storage/v1/object/public/nrdlojas-images/dynamic-pages/aula.mp4"),
            DynamicPageBlock(type = DynamicPageBlock.TYPE_AUDIO, value = "https://example.supabase.co/storage/v1/object/public/nrdlojas-images/dynamic-pages/aula.mp3"),
            DynamicPageBlock(type = DynamicPageBlock.TYPE_PDF, value = "https://example.supabase.co/storage/v1/object/public/nrdlojas-images/dynamic-pages/apostila.pdf")
        )

        val encoded = DynamicPageCodec.encode(
            blocks = blocks,
            enabled = true,
            startAt = 1_000L,
            endAt = 2_000L,
            mode = DynamicPageDocument.MODE_COURSE
        )
        val document = requireNotNull(DynamicPageCodec.decodeDocument(encoded))
        val decoded = DynamicPageCodec.decode(encoded)

        assertEquals(DynamicPageDocument.MODE_COURSE, document.mode)
        assertTrue(document.enabled)
        assertEquals(1_000L, document.startAt)
        assertEquals(2_000L, document.endAt)
        assertEquals(blocks.map { it.type }, decoded.map { it.type })
        assertEquals(blocks.map { it.value }, decoded.map { it.value })
        assertEquals(decoded.indices.toList(), decoded.map { it.displayOrder })
    }

    @Test
    fun `pagina desativada continua decodificavel sem ficar visivel`() {
        val encoded = DynamicPageCodec.encode(
            blocks = listOf(DynamicPageBlock(type = DynamicPageBlock.TYPE_TEXT, value = "Aviso")),
            enabled = false,
            mode = DynamicPageDocument.MODE_PAGE
        )
        val tab = DynamicTab(title = "Aviso", type = "text", content = encoded)

        assertEquals("Aviso", DynamicPageCodec.blocksFor(tab).single().value)
        assertTrue(!DynamicPageCodec.isVisible(tab))
    }
}
