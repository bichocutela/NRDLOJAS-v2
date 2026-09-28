package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Static, translucent glass surface for repeated list items.
 * It deliberately does not read LocalExpressiveGlassMotion or install gesture handlers.
 */
fun Modifier.glassSurface(
    shape: Shape,
    accent: Color,
    elevation: androidx.compose.ui.unit.Dp = 5.dp
): Modifier = composed {
    val style = LocalExpressiveGlassStyle.current
    val base = if (style.enabled) style.surfaceBase else Color.White
    val transparency = ((style.transparency - 0.20f) / 0.70f).coerceIn(0f, 1f)
    val backgroundBrush = remember(base, accent, style.isDark, transparency, style.glassFinish, style.glassOpacity, style.toneIntensity) {
        val finishDensity = when (style.glassFinish) { "frosted" -> 1.24f; "crystal" -> 0.60f; else -> 1f }
        val toneWash = 0.72f + style.toneIntensity * 0.56f
        val opacity = style.glassOpacity * finishDensity
        val clearBase = if (style.isDark) 0.22f - 0.06f * transparency else 0.08f - 0.03f * transparency
        val accentWash = (if (style.isDark) 0.10f - 0.04f * transparency else 0.07f - 0.03f * transparency) * opacity * toneWash
        val lowerBase = (if (style.isDark) 0.25f - 0.07f * transparency else 0.05f - 0.02f * transparency) * opacity
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (if (style.isDark) 0.10f else 0.08f) * opacity),
                accent.copy(alpha = accentWash),
                base.copy(alpha = lowerBase.coerceAtLeast(clearBase * opacity))
            )
        )
    }
    val rimBrush = remember(accent, style.isDark, style.glassFinish, style.reflectionOpacity) {
        val rim = when (style.glassFinish) { "frosted" -> 0.52f; "crystal" -> 0.92f; else -> 0.66f }
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = rim * style.reflectionOpacity.coerceIn(0f, 1f)),
                accent.copy(alpha = if (style.isDark) 0.28f else 0.22f)
            )
        )
    }

    this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (style.isDark) 0.18f else 0.09f),
            spotColor = accent.copy(alpha = if (style.isDark) 0.20f else 0.14f)
        )
        .background(backgroundBrush, shape)
        .border(width = 1.dp, brush = rimBrush, shape = shape)
}

/** Wrapper form for static glass surfaces; children are always drawn above the glass. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    accent: Color,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.glassSurface(shape = shape, accent = accent),
        content = content
    )
}
