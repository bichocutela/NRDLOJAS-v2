package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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

    Box(modifier = modifier) {
        ThemeBanner(
            appTheme = appTheme,
            backgroundUrl = backgroundUrl,
            imageScale = imageScale,
            imageOffsetX = imageOffsetX,
            imageOffsetY = imageOffsetY,
            imageStretchX = imageStretchX,
            imageStretchY = imageStretchY,
            modifier = Modifier.fillMaxSize()
        )
        BannerMaskOverlay(
            settings = mask,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun BannerMaskOverlay(
    settings: BannerMaskSettings,
    modifier: Modifier = Modifier,
    shadeColor: Color = Color.Unspecified
) {
    val safe = settings.normalized()
    if (!safe.hasVisibleShade()) return

    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val adaptiveFadeColor = if (shadeColor != Color.Unspecified) {
        shadeColor
    } else {
        val surfaceMix = if (backgroundColor.luminance() >= 0.5f) 0.78f else 0.34f
        lerp(backgroundColor, surfaceColor, surfaceMix)
    }

    val edgeAlpha = safe.strength.coerceIn(0f, 1f)
    val depthMultiplier = when (safe.style) {
        BannerMaskSettings.STYLE_DEFINED -> 0.78f
        BannerMaskSettings.STYLE_DIFFUSE -> 1.38f
        else -> 1.16f
    }
    val bandFraction = (safe.depth * depthMultiplier).coerceIn(0.08f, 0.62f)

    fun alpha(multiplier: Float): Color =
        adaptiveFadeColor.copy(alpha = (edgeAlpha * multiplier).coerceIn(0f, 1f))

    val edge = alpha(1f)
    val nearEdge = when (safe.style) {
        BannerMaskSettings.STYLE_DEFINED -> alpha(0.86f)
        BannerMaskSettings.STYLE_DIFFUSE -> alpha(0.72f)
        else -> alpha(0.80f)
    }
    val middle = when (safe.style) {
        BannerMaskSettings.STYLE_DEFINED -> alpha(0.58f)
        BannerMaskSettings.STYLE_DIFFUSE -> alpha(0.43f)
        else -> alpha(0.50f)
    }
    val feather = when (safe.style) {
        BannerMaskSettings.STYLE_DEFINED -> alpha(0.18f)
        BannerMaskSettings.STYLE_DIFFUSE -> alpha(0.12f)
        else -> alpha(0.15f)
    }
    val transparent = adaptiveFadeColor.copy(alpha = 0f)

    val outwardStops = arrayOf(
        0.00f to edge,
        0.16f to nearEdge,
        0.42f to middle,
        0.74f to feather,
        1.00f to transparent
    )
    val inwardStops = arrayOf(
        0.00f to transparent,
        0.26f to feather,
        0.58f to middle,
        0.84f to nearEdge,
        1.00f to edge
    )

    Box(modifier = modifier) {
        if (safe.shadeTop) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(bandFraction)
                    .background(Brush.verticalGradient(colorStops = outwardStops))
            )
        }

        if (safe.shadeBottom) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(bandFraction)
                    .background(Brush.verticalGradient(colorStops = inwardStops))
            )
        }

        if (safe.shadeLeft) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(bandFraction)
                    .background(Brush.horizontalGradient(colorStops = outwardStops))
            )
        }

        if (safe.shadeRight) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(bandFraction)
                    .background(Brush.horizontalGradient(colorStops = inwardStops))
            )
        }
    }
}
