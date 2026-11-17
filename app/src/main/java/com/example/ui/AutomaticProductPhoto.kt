package com.example.ui

import androidx.compose.runtime.collectAsState
import com.example.data.promotions.ManualProductPhotos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.promotions.StoreProductImageRepository
import com.example.util.ImageUrlHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * An image for display only. Explicitly registered NRD pictures keep priority;
 * official storefront photos are never written into the local or cloud catalog.
 * The repository caches positive/negative results, and only composed cards do lookups.
 */
@Composable
internal fun rememberOfficialProductImage(
    code: String,
    name: String,
    barcode: String = "",
    existingUrl: String? = null
): String? {
    val context = LocalContext.current
    val photos by remember { ManualProductPhotos.updates }.collectAsState(initial = emptyMap())
    val policy by remember { com.example.data.promotions.ProductImageSources.updates }.collectAsState(initial = com.example.data.promotions.ProductImageSources.Policy(enabled = emptySet()))
    var resolvedEan by remember(code, barcode) { mutableStateOf(barcode) }
    val manual = ManualProductPhotos.selected(photos, code, resolvedEan)
    LaunchedEffect(code, name, barcode, manual) {
        if (manual == null && barcode.isBlank()) {
            try {
                StoreProductImageRepository.shared.lookup(code, name).ean?.let { resolvedEan = it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The image resolver retains its own retry policy. */ }
        }
    }
    val candidate = if (manual != null) manual else existingUrl
    val registered = remember(candidate) {
        candidate?.trim()?.takeIf { it.isNotBlank() }
            ?.let(ImageUrlHelper::normalizeUrl)
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
    var imageUrl by remember(code, barcode, name, registered, policy) { mutableStateOf(registered) }
    LaunchedEffect(code, barcode, name, registered, policy) {
        if (registered != null) return@LaunchedEffect
        while (isActive) {
            try {
                val result = StoreProductImageRepository.shared.lookup(code, name, barcode)
                if (!result.ean.isNullOrBlank()) resolvedEan = result.ean
                suspend fun display(url: String?): Boolean {
                    if (url == null) return false
                    val loaded = context.imageLoader.execute(
                        ImageRequest.Builder(context).data(url).size(512, 512).build()
                    )
                    if (loaded !is SuccessResult) return false
                    imageUrl = url
                    return true
                }
                if (display(result.url)) break
                val ean = listOf(barcode, result.ean.orEmpty(), code)
                    .firstOrNull(com.example.data.promotions.Gtin::valid)
                val retries = mutableListOf<Long>()
                result.retryAt?.let(retries::add)
                if (result.url != null) retries += System.currentTimeMillis() + 60_000
                val secondary = com.example.data.promotions.ProductImageSources.select(
                    policy, ean,
                    lookup = { source, exactEan ->
                        if (source == "promofarma") com.example.data.promotions.PromofarmaImageRepository.get(context).lookup(exactEan)
                        else com.example.data.promotions.StructuredEanImageRepository.get(context).lookup(source, exactEan)
                    },
                    display = { display(it) }
                )
                if (secondary.displayed) break
                secondary.retryAt?.let(retries::add)
                val retryAt = retries.minOrNull() ?: break
                delay((retryAt - System.currentTimeMillis()).coerceIn(1_000L, 60_000L))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                delay(60_000)
            }
        }
    }
    return imageUrl
}

/** Optional thumbnail: preserves the surrounding layout when no photo is available. */
@Composable
internal fun AutomaticProductThumbnail(
    code: String,
    name: String,
    barcode: String = "",
    existingUrl: String? = null,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val photo = rememberOfficialProductImage(code, name, barcode, existingUrl)
    var failed by remember(photo) { mutableStateOf(false) }
    if (photo != null && !failed) {
        coil.compose.AsyncImage(
            model = photo,
            contentDescription = "Foto de $name",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = modifier.expandProductPhoto(photo, name),
            onError = { failed = true }
        )
    }
}
