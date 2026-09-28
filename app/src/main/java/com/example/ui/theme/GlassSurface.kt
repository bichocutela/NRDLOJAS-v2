package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.composed
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
    val backgroundBrush = remember(base, accent, style.isDark, transparency) {
        Brush.linearGradient(
            colors = listOf(
                base.copy(alpha = if (style.isDark) 0.50f - 0.16f * transparency else 0.30f - 0.15f * transparency),
                accent.copy(alpha = if (style.isDark) 0.26f - 0.12f * transparency else 0.19f - 0.10f * transparency),
                base.copy(alpha = if (style.isDark) 0.60f - 0.18f * transparency else 0.40f - 0.18f * transparency)
            )
        )
    }
    val rimBrush = remember(accent, style.isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (style.isDark) 0.72f else 0.82f),
                accent.copy(alpha = if (style.isDark) 0.42f else 0.32f)
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
