package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest

internal data class ProductPhotoTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    fun update(zoom: Float, pan: Offset, viewport: IntSize): ProductPhotoTransform {
        val next = if (zoom.isFinite() && zoom > 0f) (scale * zoom).coerceIn(1f, 5f) else scale
        if (next == 1f) return ProductPhotoTransform()
        val xLimit = viewport.width * (next - 1f) / 2f
        val yLimit = viewport.height * (next - 1f) / 2f
        return ProductPhotoTransform(next, Offset(
            (offset.x + if (pan.x.isFinite()) pan.x else 0f).coerceIn(-xLimit, xLimit),
            (offset.y + if (pan.y.isFinite()) pan.y else 0f).coerceIn(-yLimit, yLimit)
        ))
    }
}

/** The image tap is consumed here; tapping the rest of a product card keeps its existing action. */
@Composable
internal fun Modifier.expandProductPhoto(url: String?, name: String): Modifier {
    var expanded by remember(url) { mutableStateOf(false) }
    if (url.isNullOrBlank()) return this
    if (expanded) ProductPhotoViewerDialog(url, name) { expanded = false }
    return clickable(role = Role.Button, onClickLabel = "Ampliar foto de $name") { expanded = true }
}

@Composable
internal fun ProductPhotoViewerDialog(url: String, name: String, onDismiss: () -> Unit) {
    var transform by remember(url) { mutableStateOf(ProductPhotoTransform()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val gesture = rememberTransformableState { zoom, pan, _ ->
        transform = transform.update(zoom, pan, viewport)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp).testTag("product-photo-viewer"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fechar imagem") }
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1f)
                        .clipToBounds().onSizeChanged { viewport = it }.transformable(gesture)
                        .semantics { stateDescription = "Zoom ${(transform.scale * 100).toInt()}%" }
                        .testTag("product-photo-canvas"),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(url).size(1200, 1200).build(),
                        contentDescription = "Foto ampliada de $name",
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = transform.scale; scaleY = transform.scale
                            translationX = transform.offset.x; translationY = transform.offset.y
                        },
                        contentScale = ContentScale.Fit
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Use dois dedos para ampliar e arraste para mover.",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { transform = ProductPhotoTransform() }, enabled = transform.scale != 1f) {
                        Text("Redefinir")
                    }
                }
            }
        }
    }
}
