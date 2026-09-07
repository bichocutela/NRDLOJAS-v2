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
        const val TYPE_AUDIO = "audio"
        const val TYPE_PDF = "pdf"
        val supportedTypes = setOf(TYPE_TEXT, TYPE_IMAGE, TYPE_VIDEO, TYPE_AUDIO, TYPE_PDF)
    }
}

@Serializable
data class DynamicPageDocument(
    val version: Int = 3,
    val enabled: Boolean = true,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val mode: String = MODE_PAGE,
    val blocks: List<DynamicPageBlock> = emptyList()
) {
    companion object {
        const val MODE_PAGE = "page"
        const val MODE_COURSE = "course"
    }

    fun isVisibleAt(now: Long = System.currentTimeMillis()): Boolean {
        if (!enabled) return false
        if (startAt != null && now < startAt) return false
        if (endAt != null && now > endAt) return false
        return true
    }
}

object DynamicPageCodec {
    private const val PREFIX = "NRD_PAGE_V2:"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isPage(content: String): Boolean = content.startsWith(PREFIX)

    fun encode(
        blocks: List<DynamicPageBlock>,
        enabled: Boolean = true,
        startAt: Long? = null,
        endAt: Long? = null,
        mode: String = DynamicPageDocument.MODE_PAGE
    ): String {
        val normalized = blocks.mapIndexed { index, block ->
            block.copy(displayOrder = index)
        }
        return PREFIX + json.encodeToString(
            DynamicPageDocument(
                enabled = enabled,
                startAt = startAt,
                endAt = endAt,
                mode = mode,
                blocks = normalized
            )
        )
    }

    fun decodeDocument(content: String): DynamicPageDocument? {
        if (!isPage(content)) return null
        return runCatching {
            json.decodeFromString<DynamicPageDocument>(content.removePrefix(PREFIX))
        }.getOrNull()
    }

    fun decode(content: String): List<DynamicPageBlock> {
        return decodeDocument(content)
            ?.blocks
            ?.filter { it.type in DynamicPageBlock.supportedTypes && it.value.isNotBlank() }
            ?.sortedWith(compareBy<DynamicPageBlock> { it.displayOrder }.thenBy { it.id })
            .orEmpty()
    }

    fun isVisible(tab: DynamicTab, now: Long = System.currentTimeMillis()): Boolean {
        val document = decodeDocument(tab.content) ?: return true
        return document.isVisibleAt(now)
    }

    fun blocksFor(tab: DynamicTab): List<DynamicPageBlock> {
        if (isPage(tab.content)) return decode(tab.content)
        if (tab.content.isBlank()) return emptyList()
        val legacyType = when (tab.type) {
            DynamicPageBlock.TYPE_IMAGE -> DynamicPageBlock.TYPE_IMAGE
            DynamicPageBlock.TYPE_VIDEO -> DynamicPageBlock.TYPE_VIDEO
            DynamicPageBlock.TYPE_AUDIO -> DynamicPageBlock.TYPE_AUDIO
            DynamicPageBlock.TYPE_PDF -> DynamicPageBlock.TYPE_PDF
            else -> DynamicPageBlock.TYPE_TEXT
        }
        return listOf(DynamicPageBlock(type = legacyType, value = tab.content))
    }
}
