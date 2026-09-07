package com.example.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class DynamicPageBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val value: String,
    val displayOrder: Int = 0
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val TYPE_PDF = "pdf"
        val supportedTypes = setOf(TYPE_TEXT, TYPE_IMAGE, TYPE_VIDEO, TYPE_PDF)
    }
}

@Serializable
data class DynamicPageDocument(
    val version: Int = 2,
    val blocks: List<DynamicPageBlock> = emptyList()
)

object DynamicPageCodec {
    private const val PREFIX = "NRD_PAGE_V2:"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isPage(content: String): Boolean = content.startsWith(PREFIX)

    fun encode(blocks: List<DynamicPageBlock>): String {
        val normalized = blocks.mapIndexed { index, block ->
            block.copy(displayOrder = index)
        }
        return PREFIX + json.encodeToString(DynamicPageDocument(blocks = normalized))
    }

    fun decode(content: String): List<DynamicPageBlock> {
        if (!isPage(content)) return emptyList()
        return runCatching {
            json.decodeFromString<DynamicPageDocument>(content.removePrefix(PREFIX))
                .blocks
                .filter { it.type in DynamicPageBlock.supportedTypes && it.value.isNotBlank() }
                .sortedWith(compareBy<DynamicPageBlock> { it.displayOrder }.thenBy { it.id })
        }.getOrDefault(emptyList())
    }

    fun blocksFor(tab: DynamicTab): List<DynamicPageBlock> {
        if (isPage(tab.content)) return decode(tab.content)
        if (tab.content.isBlank()) return emptyList()
        val legacyType = when (tab.type) {
            DynamicPageBlock.TYPE_IMAGE -> DynamicPageBlock.TYPE_IMAGE
            DynamicPageBlock.TYPE_VIDEO -> DynamicPageBlock.TYPE_VIDEO
            DynamicPageBlock.TYPE_PDF -> DynamicPageBlock.TYPE_PDF
            else -> DynamicPageBlock.TYPE_TEXT
        }
        return listOf(DynamicPageBlock(type = legacyType, value = tab.content))
    }
}
