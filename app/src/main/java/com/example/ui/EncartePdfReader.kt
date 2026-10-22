package com.example.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

internal data class EncarteTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    fun update(zoom: Float, pan: Offset, centroid: Offset, viewport: IntSize, image: IntSize): EncarteTransform {
        if (viewport.width <= 0 || viewport.height <= 0 || image.width <= 0 || image.height <= 0) return this
        val next = if (zoom.isFinite() && zoom > 0f) (scale * zoom).coerceIn(1f, 6f) else scale
        if (next == 1f) return EncarteTransform()
        val fit = min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
        val xLimit = ((image.width * fit * next - viewport.width) / 2f).coerceAtLeast(0f)
        val yLimit = ((image.height * fit * next - viewport.height) / 2f).coerceAtLeast(0f)
        val focus = if (centroid.x.isFinite() && centroid.y.isFinite())
            centroid - Offset(viewport.width / 2f, viewport.height / 2f) else Offset.Zero
        val moved = (offset - focus) * (next / scale) + focus + Offset(
            pan.x.takeIf { it.isFinite() } ?: 0f, pan.y.takeIf { it.isFinite() } ?: 0f)
        return EncarteTransform(next, Offset(moved.x.coerceIn(-xLimit, xLimit), moved.y.coerceIn(-yLimit, yLimit)))
    }
}

/** Full remaining viewport. Page is fitted completely before pinch/pan; no capped card. */
@Composable
internal fun EncartePageCanvas(bitmap: Bitmap, page: Int, modifier: Modifier = Modifier) {
    var transform by remember(bitmap, page) { mutableStateOf(EncarteTransform()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewport = it }
            .pointerInput(bitmap, page) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    transform = transform.update(zoom, pan, centroid, viewport, IntSize(bitmap.width, bitmap.height))
                }
            }.pointerInput(bitmap, page) {
                detectTapGestures(onDoubleTap = { position ->
                    transform = if (transform.scale > 1f) EncarteTransform() else
                        transform.update(2.5f, Offset.Zero, position, viewport, IntSize(bitmap.width, bitmap.height))
                })
            }.semantics { stateDescription = "Zoom ${(transform.scale * 100).toInt()}%" }
            .testTag("encarte-page-canvas"), contentAlignment = Alignment.Center) {
            Image(bitmap.asImageBitmap(), contentDescription = "Página ${page + 1} do encarte",
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = transform.scale; scaleY = transform.scale
                    translationX = transform.offset.x; translationY = transform.offset.y
                }, contentScale = ContentScale.Fit)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Use a pinça ou dois toques para ampliar. Arraste para mover.", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { transform = EncarteTransform() }, enabled = transform.scale != 1f) { Text("Ajustar") }
        }
    }
}

@Composable
internal fun EncartePdfReader(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var file by remember(url) { mutableStateOf<File?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }
    LaunchedEffect(url, retry) {
        loading = true; failed = false
        try {
            file = withContext(Dispatchers.IO) { loadValidatedPdfIntoCache(context.cacheDir, context, url) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator()
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Não foi possível abrir o encarte.")
                Button(onClick = { retry++ }) { Text("Tentar novamente") }
            }
            file != null -> EncartePdfPages(file!!, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun EncartePdfPages(file: File, modifier: Modifier) {
    var renderer by remember(file) { mutableStateOf<PdfRenderer?>(null) }
    var page by remember(file) { mutableIntStateOf(0) }
    var bitmap by remember(file, page) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file, page) { mutableStateOf(false) }
    var retry by remember(file) { mutableIntStateOf(0) }
    DisposableEffect(file, retry) {
        var descriptor: ParcelFileDescriptor? = null
        var opened: PdfRenderer? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            opened = PdfRenderer(descriptor)
            renderer = opened
            failed = opened.pageCount == 0
        } catch (_: Exception) { failed = true }
        onDispose {
            renderer = null
            opened?.let { synchronized(it) { it.close() } }
            descriptor?.close()
        }
    }
    val active = renderer
    LaunchedEffect(active, page, retry) {
        if (active == null) return@LaunchedEffect
        try {
            bitmap = withContext(Dispatchers.IO) {
                synchronized(active) {
                    active.openPage(page).use { pdfPage ->
                        // One bounded page at a time: zoom never allocates a full document bitmap.
                        val factor = min(2f, 2400f / maxOf(pdfPage.width, pdfPage.height))
                        Bitmap.createBitmap((pdfPage.width * factor).toInt().coerceAtLeast(1),
                            (pdfPage.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            pdfPage.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Column(modifier) {
        val image = bitmap
        when {
            failed -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Não foi possível exibir esta página.")
                    TextButton(onClick = { failed = false; retry++ }) { Text("Tentar novamente") }
                }
            }
            image != null -> EncartePageCanvas(image, page, Modifier.weight(1f).fillMaxWidth())
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        val count = active?.pageCount ?: 0
        if (count > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { page-- }, enabled = page > 0) { Text("Anterior") }
            Text("${page + 1} de $count")
            TextButton(onClick = { page++ }, enabled = page + 1 < count) { Text("Próxima") }
        }
    }
}
