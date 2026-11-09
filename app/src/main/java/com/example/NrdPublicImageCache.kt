package com.example

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cache only immutable, public media names already present in historic NRD installations.
 * Mutable manifests, user photos, authenticated requests and offer JSON keep server TTLs.
 */
internal object NrdPublicImageCache {
    private val folders = setOf(
        "theme_backgrounds", "benefit_card_backgrounds", "consultation_backgrounds",
        "offer_banners", "banners", "glass_particles", "dynamic-pages", "products"
    )
    private val immutableName = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    )

    fun canKeepForWeek(url: HttpUrl): Boolean {
        if (url.scheme != "https" || url.host != "kkayksyzksexoarpfxyj.supabase.co") return false
        val segments = url.pathSegments
        if (segments.size < 8 || segments.take(6) != listOf("storage","v1","object","public","nrdlojas-images",segments[5])) return false
        if (segments[5] !in folders) return false
        val file = segments.last()
        if (file.endsWith(".json", true) || file.endsWith(".mp4", true) || file.endsWith(".pdf", true)) return false
        return immutableName.containsMatchIn(file) &&
            listOf(".jpg", ".jpeg", ".png", ".webp").any { file.endsWith(it,true) }
    }
}

/**
 * Requests for versioned assets remain cacheable for seven days on this device.
 * Coil's own disk cache stores bytes; no second OkHttp file cache is installed.
 */
internal class NrdPublicImageCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request=chain.request()
        val response=chain.proceed(request)
        if (request.method != "GET" || !response.isSuccessful ||
            !NrdPublicImageCache.canKeepForWeek(request.url) ||
            response.header("Cache-Control")?.contains("no-store", true) == true) return response
        return response.newBuilder().header("Cache-Control", "public, max-age=604800").build()
    }
}
