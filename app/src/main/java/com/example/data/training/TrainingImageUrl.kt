package com.example.data.training

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Keep the share link in the saved configuration; resolve only for image loading. */
object TrainingImageUrl {
    fun normalize(raw: String): String {
        val value = raw.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return value
        if (!uri.scheme.equals("https", true) || uri.host?.lowercase() !in setOf("drive.google.com", "www.drive.google.com", "docs.google.com")) return value
        val query = runCatching { uri.rawQuery.orEmpty().split('&').associate { part ->
            val pair = part.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        } }.getOrDefault(emptyMap())
        val id = Regex("/(?:file/)?d/([A-Za-z0-9_-]{10,})(?:/|$)").find(uri.path.orEmpty())?.groupValues?.get(1)
            ?: query["id"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,}")) }
            ?: return value
        val resourceKey = query["resourcekey"]?.takeIf { it.isNotBlank() }
        return if (resourceKey == null) "https://lh3.googleusercontent.com/d/$id"
        else "https://drive.google.com/thumbnail?id=$id&sz=w1200&resourcekey=${URLEncoder.encode(resourceKey, "UTF-8")}"
    }
}
