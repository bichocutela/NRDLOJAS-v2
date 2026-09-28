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
    val backgroundBrush = remember(base, accent, style.isDark, style.surfaceAlpha, style.glassFinish, style.toneIntensity) {
        val surfaceAlpha = style.surfaceAlpha.coerceIn(0.42f, 0.98f)
        val accentStrength = when (style.glassFinish) {
            "frosted" -> 0.10f
            "crystal" -> 0.055f
            else -> 0.08f
        } * (0.75f + style.toneIntensity * 0.5f)
        val highlight = when (style.glassFinish) {
            "frosted" -> 0.22f
            "crystal" -> 0.42f
            else -> 0.30f
        }
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (highlight * surfaceAlpha).coerceIn(0f, 1f)),
                accent.copy(alpha = accentStrength.coerceIn(0f, 0.22f)),
                base.copy(alpha = surfaceAlpha)
            )
        )
    }
    val rimBrush = remember(accent, style.isDark, style.glassFinish, style.reflectionOpacity) {
        val rim = when (style.glassFinish) { "frosted" -> 0.52f; "crystal" -> 0.92f; else -> 0.70f }
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = rim * (0.45f + 0.55f * style.reflectionOpacity.coerceIn(0f, 1f))),
                accent.copy(alpha = if (style.isDark) 0.28f else 0.22f)
            )
        )
    }

    this
        .shadow(
            elevation = style.shadowElevation.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = style.shadowAlpha),
            spotColor = accent.copy(alpha = style.shadowAlpha)
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
