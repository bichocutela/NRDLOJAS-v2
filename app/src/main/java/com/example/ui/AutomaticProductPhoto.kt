package com.example.ui

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
    val registered = remember(existingUrl) {
        existingUrl?.trim()?.takeIf { it.isNotBlank() }
            ?.let(ImageUrlHelper::normalizeUrl)
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
    var imageUrl by remember(code, barcode, name, registered) { mutableStateOf(registered) }
    LaunchedEffect(code, barcode, name, registered) {
        if (registered != null) return@LaunchedEffect
        try {
            val found = StoreProductImageRepository.shared.find(code, name, barcode)
            if (found != null) {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(found).size(512, 512).build()
                )
                // Do not expose a broken remote URL; leave the current visual fallback.
                if (result is SuccessResult) imageUrl = found
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // No extra messages for products with no official photo.
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
