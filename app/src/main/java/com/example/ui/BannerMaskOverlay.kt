package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BannerMaskSettings
import com.example.data.BannerMaskStore

@Composable
internal fun rememberBannerMaskSettings(
    themeKey: String,
    backgroundUrl: String?
): BannerMaskSettings {
    val flow = remember(themeKey, backgroundUrl) {
        BannerMaskStore.observe(themeKey, backgroundUrl)
    }
    val settings by flow.collectAsStateWithLifecycle(initialValue = BannerMaskSettings())
    return settings.normalized()
}

@Composable
fun MaskedThemeBanner(
    appTheme: String,
    backgroundUrl: String? = null,
    imageScale: Float = 1f,
    imageOffsetX: Float = 0f,
    imageOffsetY: Float = 0f,
    imageStretchX: Float = 1f,
    imageStretchY: Float = 1f,
    maskSettingsOverride: BannerMaskSettings? = null,
    modifier: Modifier = Modifier
) {
    val storedMask = rememberBannerMaskSettings(appTheme, backgroundUrl)
    val mask = (maskSettingsOverride ?: storedMask).normalized()

    ThemeBanner(
        appTheme = appTheme,
        backgroundUrl = backgroundUrl,
        imageScale = imageScale,
        imageOffsetX = imageOffsetX,
        imageOffsetY = imageOffsetY,
        imageStretchX = imageStretchX,
        imageStretchY = imageStretchY,
        modifier = modifier.bannerAlphaMask(mask)
    )
}

/**
 * Aplica a máscara diretamente no canal alfa do banner.
 *
 * Diferente de um overlay colorido, esta máscara torna a própria imagem
 * gradualmente transparente nas bordas selecionadas. Assim o fundo real da
 * Home aparece por trás do banner, inclusive em temas claros, escuros e Glass.
 */
private fun Modifier.bannerAlphaMask(settings: BannerMaskSettings): Modifier {
    val safe = settings.normalized()
    if (!safe.hasVisibleShade()) return this

    val depthMultiplier = when (safe.style) {
        BannerMaskSettings.STYLE_DEFINED -> 0.78f
        BannerMaskSettings.STYLE_DIFFUSE -> 1.38f
        else -> 1.16f
    }
    val bandFraction = (safe.depth * depthMultiplier).coerceIn(0.08f, 0.62f)

    // strength = 0 -> imagem totalmente opaca; strength = 1 -> borda transparente.
    val edgeRetention = (1f - safe.strength).coerceIn(0f, 1f)

    fun retention(progress: Float): Float =
        edgeRetention + ((1f - edgeRetention) * progress.coerceIn(0f, 1f))

    val fadeStops = when (safe.style) {
        BannerMaskSettings.STYLE_DEFINED -> arrayOf(
            0.00f to Color.Black.copy(alpha = edgeRetention),
            0.24f to Color.Black.copy(alpha = retention(0.12f)),
            0.52f to Color.Black.copy(alpha = retention(0.48f)),
            0.78f to Color.Black.copy(alpha = retention(0.88f)),
            1.00f to Color.Black
        )
        BannerMaskSettings.STYLE_DIFFUSE -> arrayOf(
            0.00f to Color.Black.copy(alpha = edgeRetention),
            0.16f to Color.Black.copy(alpha = retention(0.18f)),
            0.38f to Color.Black.copy(alpha = retention(0.42f)),
            0.68f to Color.Black.copy(alpha = retention(0.76f)),
            1.00f to Color.Black
        )
        else -> arrayOf(
            0.00f to Color.Black.copy(alpha = edgeRetention),
            0.18f to Color.Black.copy(alpha = retention(0.16f)),
            0.44f to Color.Black.copy(alpha = retention(0.45f)),
            0.72f to Color.Black.copy(alpha = retention(0.80f)),
            1.00f to Color.Black
        )
    }
    val reverseFadeStops = fadeStops
        .map { (position, color) -> (1f - position) to color }
        .sortedBy { it.first }
        .toTypedArray()

    return this
        .graphicsLayer {
            // Necessário para o DstIn recortar somente o banner, sem afetar a Home.
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithContent {
            drawContent()

            val verticalBand = size.height * bandFraction
            val horizontalBand = size.width * bandFraction

            if (safe.shadeTop && verticalBand > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = fadeStops,
                        startY = 0f,
                        endY = verticalBand
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, verticalBand),
                    blendMode = BlendMode.DstIn
                )
            }

            if (safe.shadeBottom && verticalBand > 0f) {
                val startY = size.height - verticalBand
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = reverseFadeStops,
                        startY = startY,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, startY),
                    size = Size(size.width, verticalBand),
                    blendMode = BlendMode.DstIn
                )
            }

            if (safe.shadeLeft && horizontalBand > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = fadeStops,
                        startX = 0f,
                        endX = horizontalBand
                    ),
                    topLeft = Offset.Zero,
                    size = Size(horizontalBand, size.height),
                    blendMode = BlendMode.DstIn
                )
            }

            if (safe.shadeRight && horizontalBand > 0f) {
                val startX = size.width - horizontalBand
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = reverseFadeStops,
                        startX = startX,
                        endX = size.width
                    ),
                    topLeft = Offset(startX, 0f),
                    size = Size(horizontalBand, size.height),
                    blendMode = BlendMode.DstIn
                )
            }
        }
}
