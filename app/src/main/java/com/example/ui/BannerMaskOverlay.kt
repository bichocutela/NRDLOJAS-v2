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
    shadeColor: Color = MaterialTheme.colorScheme.background
) {
    val safe = settings.normalized()
    if (!safe.hasVisibleShade()) return

    val edgeAlpha = safe.strength.coerceIn(0f, 1f)
    val depthMultiplier = when (safe.style) {
        BannerMaskSettings.STYLE_DEFINED -> 0.72f
        BannerMaskSettings.STYLE_DIFFUSE -> 1.28f
        else -> 1f
    }
    val bandFraction = (safe.depth * depthMultiplier).coerceIn(0.06f, 0.58f)
    val edge = shadeColor.copy(alpha = edgeAlpha)
    val middle = when (safe.style) {
        BannerMaskSettings.STYLE_DEFINED -> shadeColor.copy(alpha = edgeAlpha * 0.72f)
        BannerMaskSettings.STYLE_DIFFUSE -> shadeColor.copy(alpha = edgeAlpha * 0.30f)
        else -> shadeColor.copy(alpha = edgeAlpha * 0.46f)
    }
    val transparent = shadeColor.copy(alpha = 0f)

    Box(modifier = modifier) {
        if (safe.shadeTop) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(bandFraction)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(edge, middle, transparent)
                        )
                    )
            )
        }

        if (safe.shadeBottom) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(bandFraction)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(transparent, middle, edge)
                        )
                    )
            )
        }

        if (safe.shadeLeft) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(bandFraction)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(edge, middle, transparent)
                        )
                    )
            )
        }

        if (safe.shadeRight) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(bandFraction)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(transparent, middle, edge)
                        )
                    )
            )
        }
    }
}
