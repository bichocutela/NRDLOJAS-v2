package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GlassSoftShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(36.dp),
    extraLarge = RoundedCornerShape(44.dp)
)

@Immutable
data class ExpressiveStyle(
    val enabled: Boolean = false,
    val variant: String = "solid"
) {
    val isGlass: Boolean
        get() = enabled && variant == "glass"
}

val LocalExpressiveStyle = staticCompositionLocalOf { ExpressiveStyle() }

@Immutable
data class ExpressiveGlassStyle(
    val enabled: Boolean = false,
    val accentName: String = "multicolor",
    val transparency: Float = 0.58f,
    val fluidity: Float = 0.68f,
    val accent: Color = Color(0xFFC89300),
    val secondaryAccent: Color = Color(0xFF1976D2),
    val tertiaryAccent: Color = Color(0xFF2F9A50),
    val onAccent: Color = Color.White,
    val surfaceAlpha: Float = 0.58f,
    val strongSurfaceAlpha: Float = 0.68f,
    val borderColor: Color = Color.White.copy(alpha = 0.82f),
    val shadowElevation: Float = 11f,
    val shadowAlpha: Float = 0.20f,
    val isDark: Boolean = false
) {
    val surfaceBase: Color
        get() = if (isDark) Color(0xFF101721) else Color.White
}

val LocalExpressiveGlassStyle = staticCompositionLocalOf { ExpressiveGlassStyle() }

internal val ExpressiveGlassAccentNames = listOf("multicolor", "red", "green", "orange", "blue", "gold")

private fun normalizeExpressiveGlassAccentName(name: String): String =
    name.trim().lowercase().takeIf { it in ExpressiveGlassAccentNames } ?: "multicolor"

private fun expressiveGlassActionColors(name: String, isDark: Boolean): List<Color> {
    val normalized = normalizeExpressiveGlassAccentName(name)
    val light = mapOf(
        "multicolor" to listOf(Color(0xFFE7333F), Color(0xFF1976D2), Color(0xFFF2B705)),
        "red" to listOf(Color(0xFFE7333F), Color(0xFFFF7A59), Color(0xFFC2185B)),
        "green" to listOf(Color(0xFF1E9C55), Color(0xFF44C79A), Color(0xFF0F7A68)),
        "orange" to listOf(Color(0xFFF57C00), Color(0xFFFFB24A), Color(0xFFE94E1B)),
        "blue" to listOf(Color(0xFF1976D2), Color(0xFF42A5F5), Color(0xFF4B5FD6)),
        "gold" to listOf(Color(0xFFB8860B), Color(0xFFE6B93D), Color(0xFFFFD76A))
    )
    val dark = mapOf(
        "multicolor" to listOf(Color(0xFFFF7882), Color(0xFF78B9FF), Color(0xFFFFD76A)),
        "red" to listOf(Color(0xFFFF7882), Color(0xFFFFA083), Color(0xFFFF7FB5)),
        "green" to listOf(Color(0xFF72E2A5), Color(0xFF7CE5C6), Color(0xFF6FD6CB)),
        "orange" to listOf(Color(0xFFFFB567), Color(0xFFFFCA7A), Color(0xFFFF8A66)),
        "blue" to listOf(Color(0xFF79B8FF), Color(0xFF8FD3FF), Color(0xFFA6AEFF)),
        "gold" to listOf(Color(0xFFFFD76A), Color(0xFFFFE49B), Color(0xFFEAB84D))
    )
    return (if (isDark) dark else light).getValue(normalized)
}

internal fun expressiveGlassBackgroundColors(name: String, isDark: Boolean): List<Color> {
    val normalized = normalizeExpressiveGlassAccentName(name)
    val light = mapOf(
        "multicolor" to listOf(Color(0xFFFFE4E7), Color(0xFFE8F3FF), Color(0xFFFFF2CA), Color(0xFFE8F8F0), Color(0xFFF1E8FF)),
        "red" to listOf(Color(0xFFFFE4E7), Color(0xFFFFD9D5), Color(0xFFFFE9F1), Color(0xFFFFF1E8)),
        "green" to listOf(Color(0xFFDFF7E8), Color(0xFFD9F7EF), Color(0xFFE4F3FF), Color(0xFFF0F9E6)),
        "orange" to listOf(Color(0xFFFFE8D0), Color(0xFFFFF0D5), Color(0xFFFFE1D6), Color(0xFFFFF6E8)),
        "blue" to listOf(Color(0xFFDDEEFF), Color(0xFFE5F6FF), Color(0xFFE8E5FF), Color(0xFFE2F7F3)),
        "gold" to listOf(Color(0xFFFFF0BE), Color(0xFFFFE6A2), Color(0xFFFFF6D6), Color(0xFFF7ECD0))
    )
    val dark = mapOf(
        "multicolor" to listOf(Color(0xFF190F18), Color(0xFF0E1C30), Color(0xFF2B2110), Color(0xFF0F291F), Color(0xFF21172F)),
        "red" to listOf(Color(0xFF2A1116), Color(0xFF35161B), Color(0xFF2E1423), Color(0xFF2B1A12)),
        "green" to listOf(Color(0xFF10241A), Color(0xFF0E2D25), Color(0xFF102638), Color(0xFF1A2913)),
        "orange" to listOf(Color(0xFF2B1A0E), Color(0xFF3B2812), Color(0xFF321713), Color(0xFF2A2217)),
        "blue" to listOf(Color(0xFF0D1B2C), Color(0xFF102C42), Color(0xFF171A38), Color(0xFF102B2A)),
        "gold" to listOf(Color(0xFF2B220E), Color(0xFF3B2D11), Color(0xFF2A2619), Color(0xFF221D12))
    )
    return (if (isDark) dark else light).getValue(normalized)
}

internal fun resolveExpressiveGlassStyle(
    enabled: Boolean,
    isDark: Boolean,
    accentName: String = "multicolor",
    transparency: Float = 0.58f,
    fluidity: Float = 0.68f
): ExpressiveGlassStyle {
    if (!enabled) return ExpressiveGlassStyle()
    val safeTransparency = transparency.coerceIn(0.20f, 0.90f)
    val safeFluidity = fluidity.coerceIn(0f, 1f)
    val progress = ((safeTransparency - 0.20f) / 0.70f).coerceIn(0f, 1f)
    val actions = expressiveGlassActionColors(accentName, isDark)
    val surfaceAlpha = 0.82f - (0.28f * progress)
    val normalized = normalizeExpressiveGlassAccentName(accentName)
    return ExpressiveGlassStyle(
        enabled = true,
        accentName = normalized,
        transparency = safeTransparency,
        fluidity = safeFluidity,
        accent = actions[0],
        secondaryAccent = actions[1],
        tertiaryAccent = actions[2],
        onAccent = if (isDark || normalized in setOf("gold", "orange")) Color(0xFF17202A) else Color.White,
        surfaceAlpha = surfaceAlpha,
        strongSurfaceAlpha = (surfaceAlpha + 0.10f).coerceAtMost(0.92f),
        borderColor = Color.White.copy(alpha = (0.62f + 0.24f * safeFluidity).coerceAtMost(0.90f)),
        shadowElevation = 9f + (5f * safeFluidity),
        shadowAlpha = (if (isDark) 0.24f else 0.12f) + (0.08f * safeFluidity),
        isDark = isDark
    )
}

@Immutable
data class GlassSoftStyle(
    val enabled: Boolean = false,
    val type: String = "soft",
    val transparency: Float = 0.55f,
    val accentName: String = "multicolor",
    val accent: Color = Color(0xFF15548A),
    val secondaryAccent: Color = Color(0xFF115A35),
    val tertiaryAccent: Color = Color(0xFF5A3B91),
    val onAccent: Color = Color.White,
    val surfaceAlpha: Float = 1f,
    val strongSurfaceAlpha: Float = 1f,
    val borderAlpha: Float = 0f,
    val shadowElevation: Float = 0f,
    val shadowAlpha: Float = 0f,
    val isDark: Boolean = false
) {
    val surfaceBase: Color
        get() = if (isDark) Color(0xFF111A26) else Color.White

    val borderColor: Color
        get() = if (isDark) {
            Color.White.copy(alpha = (borderAlpha * 0.72f).coerceIn(0.28f, 0.72f))
        } else {
            Color.White.copy(alpha = borderAlpha)
        }
}

val LocalGlassSoftStyle = staticCompositionLocalOf { GlassSoftStyle() }

internal val GlassSoftAccentNames = listOf("multicolor", "blue", "green", "purple", "pink", "orange", "cyan")
internal val GlassSoftTypes = listOf("soft", "frosted", "crystal")

private fun normalizeGlassAccentName(name: String): String = when (name.trim().lowercase()) {
    "blue" -> "blue"
    "green" -> "green"
    "purple" -> "purple"
    "pink", "red" -> "pink"
    "orange", "gold" -> "orange"
    "cyan" -> "cyan"
    else -> "multicolor"
}

private fun glassSoftActionColors(name: String, isDark: Boolean): List<Color> {
    val normalized = normalizeGlassAccentName(name)
    val light = mapOf(
        "multicolor" to listOf(Color(0xFF15548A), Color(0xFF115A35), Color(0xFF5A3B91)),
        "blue" to listOf(Color(0xFF15548A), Color(0xFF115A35), Color(0xFF5A3B91)),
        "green" to listOf(Color(0xFF115A35), Color(0xFF15548A), Color(0xFF714000)),
        "purple" to listOf(Color(0xFF5A3B91), Color(0xFF892B5E), Color(0xFF15548A)),
        "pink" to listOf(Color(0xFF892B5E), Color(0xFF5A3B91), Color(0xFF15548A)),
        "orange" to listOf(Color(0xFF714000), Color(0xFF892B5E), Color(0xFF115A35)),
        "cyan" to listOf(Color(0xFF00585D), Color(0xFF15548A), Color(0xFF5A3B91))
    )
    val dark = mapOf(
        "multicolor" to listOf(Color(0xFF8CC7FF), Color(0xFF8FE0B4), Color(0xFFC5A8FF)),
        "blue" to listOf(Color(0xFF8CC7FF), Color(0xFF8FE0B4), Color(0xFFC5A8FF)),
        "green" to listOf(Color(0xFF8FE0B4), Color(0xFF8CC7FF), Color(0xFFF7BC78)),
        "purple" to listOf(Color(0xFFC5A8FF), Color(0xFFFF9FCB), Color(0xFF8CC7FF)),
        "pink" to listOf(Color(0xFFFF9FCB), Color(0xFFC5A8FF), Color(0xFF8CC7FF)),
        "orange" to listOf(Color(0xFFF7BC78), Color(0xFFFF9FCB), Color(0xFF8FE0B4)),
        "cyan" to listOf(Color(0xFF78D7DD), Color(0xFF8CC7FF), Color(0xFFC5A8FF))
    )
    return (if (isDark) dark else light).getValue(normalized)
}

fun glassSoftAccent(name: String, isDark: Boolean = false): Color =
    glassSoftActionColors(name, isDark).first()

internal fun glassSoftBackgroundColors(name: String, isDark: Boolean): List<Color> {
    val normalized = normalizeGlassAccentName(name)
    val light = mapOf(
        "multicolor" to listOf(Color(0xFFB9DEFA), Color(0xFFCBEFD9), Color(0xFFDCCBFF), Color(0xFFF8CFE7), Color(0xFFFFE2BB), Color(0xFFC6F0F1)),
        "blue" to listOf(Color(0xFFB9DEFA), Color(0xFFD8EEFF), Color(0xFFE7D8FF), Color(0xFFF8D8E9), Color(0xFFD5F3EE)),
        "green" to listOf(Color(0xFFBDE9CF), Color(0xFFDDF4E5), Color(0xFFCBE9FF), Color(0xFFE8DCFF), Color(0xFFF9E1D2)),
        "purple" to listOf(Color(0xFFCDBBFA), Color(0xFFE4DAFF), Color(0xFFF8D5EB), Color(0xFFCFE8FF), Color(0xFFD8F1EA)),
        "pink" to listOf(Color(0xFFF5B7D5), Color(0xFFFBDCEB), Color(0xFFE0D5FF), Color(0xFFCFE9FF), Color(0xFFFCE6CE)),
        "orange" to listOf(Color(0xFFF8CF96), Color(0xFFFFE8C8), Color(0xFFF7D8E8), Color(0xFFD6E9FF), Color(0xFFD9F2E7)),
        "cyan" to listOf(Color(0xFFABE5E7), Color(0xFFD5F3F2), Color(0xFFCFE6FF), Color(0xFFE7DCFF), Color(0xFFF7DDEC))
    )
    val dark = mapOf(
        "multicolor" to listOf(Color(0xFF0C1724), Color(0xFF173229), Color(0xFF29203D), Color(0xFF352233), Color(0xFF102D34)),
        "blue" to listOf(Color(0xFF0B1725), Color(0xFF12304C), Color(0xFF25203B), Color(0xFF302331), Color(0xFF102C30)),
        "green" to listOf(Color(0xFF0C1917), Color(0xFF15342A), Color(0xFF182A3C), Color(0xFF2A2038), Color(0xFF2E251D)),
        "purple" to listOf(Color(0xFF171326), Color(0xFF2D2148), Color(0xFF362137), Color(0xFF172C3D), Color(0xFF173029)),
        "pink" to listOf(Color(0xFF21131C), Color(0xFF3C2030), Color(0xFF2B2140), Color(0xFF172B3C), Color(0xFF30251C)),
        "orange" to listOf(Color(0xFF21180F), Color(0xFF3B2A18), Color(0xFF37202D), Color(0xFF172B3D), Color(0xFF173029)),
        "cyan" to listOf(Color(0xFF0C1B20), Color(0xFF12353A), Color(0xFF182D43), Color(0xFF29213F), Color(0xFF35202D))
    )
    return (if (isDark) dark else light).getValue(normalized)
}

internal fun resolveGlassSoftStyle(
    enabled: Boolean,
    type: String,
    transparency: Float,
    accentName: String,
    isDark: Boolean
): GlassSoftStyle {
    val safeTransparency = transparency.coerceIn(0.20f, 0.90f)
    val progress = ((safeTransparency - 0.20f) / 0.70f).coerceIn(0f, 1f)
    fun interpolate(moreSolid: Float, moreTransparent: Float): Float =
        moreSolid + (moreTransparent - moreSolid) * progress
    val surfaceAlpha = when (type) {
        "frosted" -> interpolate(0.92f, 0.78f)
        "crystal" -> interpolate(0.72f, 0.52f)
        else -> interpolate(0.82f, 0.62f)
    }
    val borderAlpha = when (type) {
        "frosted" -> 0.54f
        "crystal" -> 0.90f
        else -> 0.70f
    }
    val shadowElevation = when (type) {
        "frosted" -> 5f
        "crystal" -> 12f
        else -> 8f
    }
    val shadowAlpha = when (type) {
        "frosted" -> 0.14f
        "crystal" -> 0.22f
        else -> 0.18f
    }
    val actions = glassSoftActionColors(accentName, isDark)
    return GlassSoftStyle(
        enabled = enabled,
        type = type,
        transparency = safeTransparency,
        accentName = normalizeGlassAccentName(accentName),
        accent = actions[0],
        secondaryAccent = actions[1],
        tertiaryAccent = actions[2],
        onAccent = if (isDark) Color(0xFF0B1620) else Color.White,
        surfaceAlpha = surfaceAlpha,
        strongSurfaceAlpha = (surfaceAlpha + 0.08f).coerceAtMost(0.96f),
        borderAlpha = borderAlpha,
        shadowElevation = shadowElevation,
        shadowAlpha = shadowAlpha,
        isDark = isDark
    )
}

internal fun glassSoftColorScheme(style: GlassSoftStyle): androidx.compose.material3.ColorScheme {
    val base = if (style.isDark) DefaultDarkColorScheme else DefaultLightColorScheme
    val onSurface = if (style.isDark) Color(0xFFF4F7FA) else Color(0xFF18212B)
    val onSurfaceVariant = if (style.isDark) Color(0xFFC9D3DE) else Color(0xFF465465)
    val surface = style.surfaceBase
    val containerAlpha = if (style.isDark) 0.24f else 0.16f
    return base.copy(
        primary = style.accent,
        onPrimary = style.onAccent,
        primaryContainer = style.accent.copy(alpha = containerAlpha),
        onPrimaryContainer = onSurface,
        secondary = style.secondaryAccent,
        onSecondary = style.onAccent,
        secondaryContainer = style.secondaryAccent.copy(alpha = containerAlpha),
        onSecondaryContainer = onSurface,
        tertiary = style.tertiaryAccent,
        onTertiary = style.onAccent,
        tertiaryContainer = style.tertiaryAccent.copy(alpha = containerAlpha),
        onTertiaryContainer = onSurface,
        background = Color.Transparent,
        onBackground = onSurface,
        surface = surface.copy(alpha = style.surfaceAlpha),
        onSurface = onSurface,
        surfaceVariant = surface.copy(alpha = style.strongSurfaceAlpha),
        onSurfaceVariant = onSurfaceVariant,
        surfaceDim = surface.copy(alpha = style.strongSurfaceAlpha),
        surfaceBright = surface.copy(alpha = style.surfaceAlpha),
        surfaceContainerLowest = surface.copy(alpha = (style.surfaceAlpha - 0.06f).coerceAtLeast(0.46f)),
        surfaceContainerLow = surface.copy(alpha = style.surfaceAlpha),
        surfaceContainer = surface.copy(alpha = style.surfaceAlpha),
        surfaceContainerHigh = surface.copy(alpha = style.strongSurfaceAlpha),
        surfaceContainerHighest = surface.copy(alpha = (style.strongSurfaceAlpha + 0.04f).coerceAtMost(0.98f)),
        outline = if (style.isDark) Color.White.copy(alpha = 0.52f) else style.accent.copy(alpha = 0.62f),
        outlineVariant = style.borderColor,
        inverseSurface = if (style.isDark) Color(0xFFEAF0F6) else Color(0xFF26313D),
        inverseOnSurface = if (style.isDark) Color(0xFF17212C) else Color.White,
        surfaceTint = Color.Transparent
    )
}

fun Modifier.glassSoftShadow(
    shape: Shape,
    elevation: Dp? = null
): Modifier = composed {
    val style = LocalGlassSoftStyle.current
    if (!style.enabled) {
        this
    } else {
        shadow(
            elevation = elevation ?: style.shadowElevation.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = style.shadowAlpha),
            spotColor = style.accent.copy(alpha = (style.shadowAlpha + 0.06f).coerceAtMost(0.30f))
        )
    }
}


fun Modifier.expressiveLiquidGlass(
    shape: Shape,
    accent: Color? = null,
    intensity: Float = 1f,
    elevation: Dp? = null
): Modifier = composed {
    val style = LocalExpressiveGlassStyle.current
    if (!style.enabled) {
        this
    } else {
        val safeIntensity = intensity.coerceIn(0.45f, 1.40f)
        val fluidity = style.fluidity.coerceIn(0f, 1f)
        val tint = accent ?: style.accent
        val refraction = style.secondaryAccent
        val highlightAlpha = (0.46f + 0.30f * fluidity) * safeIntensity
        val borderWidthDp = 1.25f + 1.55f * fluidity

        this
            .shadow(
                elevation = elevation ?: style.shadowElevation.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = style.shadowAlpha * 0.72f),
                spotColor = tint.copy(alpha = (style.shadowAlpha + 0.10f).coerceAtMost(0.34f))
            )
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val maxDimension = maxOf(size.width, size.height).coerceAtLeast(1f)
                val baseBrush = Brush.linearGradient(
                    colors = listOf(
                        style.surfaceBase.copy(alpha = (style.strongSurfaceAlpha * 0.93f).coerceIn(0f, 1f)),
                        tint.copy(alpha = (0.08f + 0.10f * fluidity) * safeIntensity),
                        style.surfaceBase.copy(alpha = (style.surfaceAlpha * 0.88f).coerceIn(0f, 1f)),
                        refraction.copy(alpha = (0.05f + 0.07f * fluidity) * safeIntensity),
                        style.surfaceBase.copy(alpha = (style.strongSurfaceAlpha * 0.84f).coerceIn(0f, 1f))
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                val topLens = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = highlightAlpha.coerceAtMost(0.82f)),
                        Color.White.copy(alpha = 0.16f * safeIntensity),
                        Color.Transparent
                    ),
                    center = Offset(
                        x = size.width * (0.16f + 0.10f * fluidity),
                        y = size.height * (0.02f + 0.08f * fluidity)
                    ),
                    radius = maxDimension * (0.58f + 0.12f * fluidity)
                )
                val lowerRefraction = Brush.radialGradient(
                    colors = listOf(
                        tint.copy(alpha = (0.18f + 0.12f * fluidity) * safeIntensity),
                        refraction.copy(alpha = (0.08f + 0.08f * fluidity) * safeIntensity),
                        Color.Transparent
                    ),
                    center = Offset(
                        x = size.width * (0.82f - 0.08f * fluidity),
                        y = size.height * (0.90f - 0.08f * fluidity)
                    ),
                    radius = maxDimension * (0.46f + 0.16f * fluidity)
                )
                val specularEdge = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.90f * safeIntensity).coerceAtMost(0.96f)),
                        Color.White.copy(alpha = 0.18f),
                        tint.copy(alpha = (0.28f + 0.12f * fluidity) * safeIntensity),
                        Color.White.copy(alpha = 0.46f),
                        refraction.copy(alpha = (0.24f + 0.12f * fluidity) * safeIntensity),
                        Color.White.copy(alpha = (0.82f * safeIntensity).coerceAtMost(0.92f))
                    ),
                    start = Offset(0f, size.height * 0.08f),
                    end = Offset(size.width, size.height * 0.92f)
                )
                val innerGleam = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.26f * safeIntensity),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.10f * safeIntensity)
                    ),
                    start = Offset(size.width * 0.10f, 0f),
                    end = Offset(size.width * 0.90f, size.height)
                )

                onDrawWithContent {
                    drawOutline(outline = outline, brush = baseBrush)
                    drawOutline(outline = outline, brush = topLens)
                    drawOutline(outline = outline, brush = lowerRefraction)
                    drawOutline(outline = outline, brush = innerGleam)
                    drawContent()
                    drawOutline(
                        outline = outline,
                        brush = specularEdge,
                        style = Stroke(width = borderWidthDp.dp.toPx())
                    )
                    drawOutline(
                        outline = outline,
                        color = Color.White.copy(alpha = (0.18f + 0.16f * fluidity) * safeIntensity),
                        style = Stroke(width = 0.65.dp.toPx())
                    )
                }
            }
    }
}

fun Modifier.expressiveShadow(
    shape: Shape,
    elevation: Dp = 7.dp
): Modifier = composed {
    val expressive = LocalExpressiveStyle.current
    val expressiveGlass = LocalExpressiveGlassStyle.current
    if (!expressive.enabled) {
        this
    } else if (expressiveGlass.enabled) {
        shadow(
            elevation = maxOf(elevation.value, expressiveGlass.shadowElevation).dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = expressiveGlass.shadowAlpha),
            spotColor = expressiveGlass.accent.copy(alpha = (expressiveGlass.shadowAlpha + 0.08f).coerceAtMost(0.34f))
        )
    } else {
        val bg = MaterialTheme.colorScheme.background
        val dark = ((bg.red + bg.green + bg.blue) / 3f) < 0.35f
        shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = if (dark) {
                Color.Black.copy(alpha = 0.34f)
            } else {
                Color(0xFF49627D).copy(alpha = 0.10f)
            },
            spotColor = if (dark) {
                Color.Black.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            }
        )
    }
}

@Composable
fun GlassSoftBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val style = LocalGlassSoftStyle.current
    if (!style.enabled) {
        Box(modifier = modifier, content = { content() })
        return
    }
    val colors = glassSoftBackgroundColors(style.accentName, style.isDark)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors)),
        content = { content() }
    )
}


@Composable
fun NrdAppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val glass = LocalGlassSoftStyle.current
    val expressive = LocalExpressiveStyle.current
    val expressiveGlass = LocalExpressiveGlassStyle.current
    when {
        glass.enabled -> GlassSoftBackground(modifier = modifier, content = content)
        expressiveGlass.enabled -> {
            val colors = expressiveGlassBackgroundColors(
                expressiveGlass.accentName,
                expressiveGlass.isDark
            )
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors)),
                content = { content() }
            )
        }
        expressive.enabled -> {
            val dark = MaterialTheme.colorScheme.background.red < 0.2f
            val colors = if (dark) {
                listOf(
                    Color(0xFF101A2A),
                    Color(0xFF0D1420),
                    Color(0xFF172538)
                )
            } else {
                listOf(
                    Color(0xFFE9F6FF),
                    Color(0xFFF7FBFF),
                    Color(0xFFFFFCF2),
                    Color(0xFFF2F8FF)
                )
            }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors)),
                content = { content() }
            )
        }
        else -> Box(modifier = modifier, content = { content() })
    }
}

private val DefaultLightColorScheme = lightColorScheme(
    primary = NordestaoRed,
    onPrimary = Color.White,
    primaryContainer = NordestaoRedDark,
    onPrimaryContainer = Color.White,
    secondary = NordestaoYellow,
    onSecondary = TextPrimary,
    secondaryContainer = NordestaoYellowLight,
    onSecondaryContainer = TextPrimary,
    tertiary = NordestaoBlue,
    onTertiary = Color.White,
    background = BackgroundWhite,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = TextSecondary,
    outline = OutlineColor
)

private val DefaultDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF8585),
    onPrimary = Color(0xFF5F0000),
    primaryContainer = Color(0xFF8D1014),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFFFFC95C),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF604800),
    onSecondaryContainer = Color(0xFFFFE9BE),
    tertiary = Color(0xFF82B1FF),
    onTertiary = Color(0xFF00315C),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFF20242C),
    onSurfaceVariant = Color(0xFFC2C7D0),
    outline = Color(0xFF5C6470),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val SessionMulticolorPalette: List<Pair<Color, Color>> by lazy {
    MulticolorPalette.shuffled()
}

private fun expressiveColorScheme(darkTheme: Boolean) = if (darkTheme) {
    DefaultDarkColorScheme.copy(
        primary = Color(0xFFFFD45C),
        onPrimary = Color(0xFF2E2200),
        primaryContainer = Color(0xFF473700),
        onPrimaryContainer = Color(0xFFFFF0B4),
        secondary = Color(0xFF8FC7FF),
        onSecondary = Color(0xFF002B4D),
        secondaryContainer = Color(0xFF153A5C),
        onSecondaryContainer = Color(0xFFDCEEFF),
        tertiary = Color(0xFF88DEA3),
        onTertiary = Color(0xFF00391A),
        tertiaryContainer = Color(0xFF174A2C),
        onTertiaryContainer = Color(0xFFD5F8DE),
        background = Color(0xFF090F17),
        onBackground = Color(0xFFF4F7FC),
        surface = Color(0xFF101721),
        onSurface = Color(0xFFF4F7FC),
        surfaceVariant = Color(0xFF1A2430),
        onSurfaceVariant = Color(0xFFD2DCE9),
        surfaceDim = Color(0xFF0B1119),
        surfaceBright = Color(0xFF2A3542),
        surfaceContainerLowest = Color(0xFF080D14),
        surfaceContainerLow = Color(0xFF0E151F),
        surfaceContainer = Color(0xFF141D28),
        surfaceContainerHigh = Color(0xFF1B2632),
        surfaceContainerHighest = Color(0xFF24313F),
        outline = Color(0xFF91A0B1),
        outlineVariant = Color(0xFF44515F),
        inverseSurface = Color(0xFFE9EEF5),
        inverseOnSurface = Color(0xFF17202A),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
} else {
    DefaultLightColorScheme.copy(
        primary = Color(0xFFC89300),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE7A3),
        onPrimaryContainer = Color(0xFF463400),
        secondary = Color(0xFF1976D2),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD9EBFF),
        onSecondaryContainer = Color(0xFF0B3159),
        tertiary = Color(0xFF2F9A50),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD9F4DF),
        onTertiaryContainer = Color(0xFF103A1B),
        background = Color(0xFFF5FAFF),
        onBackground = Color(0xFF16203B),
        surface = Color(0xFFFFFEFF),
        onSurface = Color(0xFF16203B),
        surfaceVariant = Color(0xFFF1F6FC),
        onSurfaceVariant = Color(0xFF5C667A),
        surfaceContainerLow = Color(0xFFFAFCFF),
        surfaceContainer = Color(0xFFF3F8FE),
        surfaceContainerHigh = Color(0xFFECF3FB),
        surfaceContainerHighest = Color(0xFFE4EDF7),
        outline = Color(0xFF8390A3)
    )
}

internal fun expressiveGlassColorScheme(style: ExpressiveGlassStyle, darkTheme: Boolean): androidx.compose.material3.ColorScheme {
    val base = expressiveColorScheme(darkTheme)
    val onSurface = if (darkTheme) Color(0xFFF4F7FC) else Color(0xFF16203B)
    val onSurfaceVariant = if (darkTheme) Color(0xFFD2DCE9) else Color(0xFF536174)
    val surface = style.surfaceBase
    val containerAlpha = if (darkTheme) 0.24f else 0.18f
    return base.copy(
        primary = style.accent,
        onPrimary = style.onAccent,
        primaryContainer = style.accent.copy(alpha = containerAlpha),
        onPrimaryContainer = onSurface,
        secondary = style.secondaryAccent,
        onSecondary = style.onAccent,
        secondaryContainer = style.secondaryAccent.copy(alpha = containerAlpha),
        onSecondaryContainer = onSurface,
        tertiary = style.tertiaryAccent,
        onTertiary = style.onAccent,
        tertiaryContainer = style.tertiaryAccent.copy(alpha = containerAlpha),
        onTertiaryContainer = onSurface,
        background = Color.Transparent,
        onBackground = onSurface,
        surface = surface.copy(alpha = style.surfaceAlpha),
        onSurface = onSurface,
        surfaceVariant = surface.copy(alpha = style.strongSurfaceAlpha),
        onSurfaceVariant = onSurfaceVariant,
        surfaceDim = surface.copy(alpha = style.strongSurfaceAlpha),
        surfaceBright = surface.copy(alpha = style.surfaceAlpha),
        surfaceContainerLowest = surface.copy(alpha = (style.surfaceAlpha - 0.08f).coerceAtLeast(0.42f)),
        surfaceContainerLow = surface.copy(alpha = style.surfaceAlpha),
        surfaceContainer = surface.copy(alpha = style.surfaceAlpha),
        surfaceContainerHigh = surface.copy(alpha = style.strongSurfaceAlpha),
        surfaceContainerHighest = surface.copy(alpha = (style.strongSurfaceAlpha + 0.05f).coerceAtMost(0.94f)),
        outline = style.accent.copy(alpha = if (darkTheme) 0.62f else 0.52f),
        outlineVariant = style.borderColor,
        surfaceTint = Color.Transparent
    )
}

private fun getThemeColorScheme(themeName: String, darkTheme: Boolean) = when (themeName) {
    "multicolor" -> {
        val primary = SessionMulticolorPalette[0]
        val secondary = SessionMulticolorPalette[1]
        val tertiary = SessionMulticolorPalette[2]
        if (darkTheme) {
            DefaultDarkColorScheme.copy(
                primary = primary.first,
                onPrimary = primary.second,
                primaryContainer = primary.first,
                onPrimaryContainer = primary.second,
                secondary = secondary.first,
                onSecondary = secondary.second,
                secondaryContainer = secondary.first,
                onSecondaryContainer = secondary.second,
                tertiary = tertiary.first,
                onTertiary = tertiary.second,
                tertiaryContainer = tertiary.first,
                onTertiaryContainer = tertiary.second,
                error = Color(0xFFFF6B6B)
            )
        } else {
            DefaultLightColorScheme.copy(
                primary = primary.first,
                onPrimary = primary.second,
                primaryContainer = primary.first,
                onPrimaryContainer = primary.second,
                secondary = secondary.first,
                onSecondary = secondary.second,
                secondaryContainer = secondary.first,
                onSecondaryContainer = secondary.second,
                tertiary = tertiary.first,
                onTertiary = tertiary.second,
                tertiaryContainer = tertiary.first,
                onTertiaryContainer = tertiary.second,
                error = Color(0xFFE62325)
            )
        }
    }
    "gold" -> if (darkTheme) {
        DefaultDarkColorScheme.copy(
            primary = Color(0xFFF0C553),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF6B5200),
            onPrimaryContainer = Color.White
        )
    } else {
        DefaultLightColorScheme.copy(
            primary = Color(0xFFD4AF37),
            primaryContainer = Color(0xFFB8952B),
            onPrimary = Color.White,
            onPrimaryContainer = Color.White
        )
    }
    "green" -> if (darkTheme) {
        DefaultDarkColorScheme.copy(
            primary = Color(0xFF6DCE70),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF0F5B22),
            onPrimaryContainer = Color.White
        )
    } else {
        DefaultLightColorScheme.copy(
            primary = Color(0xFF388E3C),
            primaryContainer = Color(0xFF2E7D32),
            onPrimary = Color.White,
            onPrimaryContainer = Color.White
        )
    }
    "blue" -> if (darkTheme) {
        DefaultDarkColorScheme.copy(
            primary = Color(0xFF82B1FF),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF0B4F92),
            onPrimaryContainer = Color.White
        )
    } else {
        DefaultLightColorScheme.copy(
            primary = Color(0xFF1976D2),
            primaryContainer = Color(0xFF1565C0),
            onPrimary = Color.White,
            onPrimaryContainer = Color.White
        )
    }
    "orange" -> if (darkTheme) {
        DefaultDarkColorScheme.copy(
            primary = Color(0xFFFFB74D),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF7A3E00),
            onPrimaryContainer = Color.White
        )
    } else {
        DefaultLightColorScheme.copy(
            primary = Color(0xFFFF9800),
            primaryContainer = Color(0xFFF57C00),
            onPrimary = Color.White,
            onPrimaryContainer = Color.White
        )
    }
    else -> DefaultLightColorScheme.takeIf { !darkTheme } ?: DefaultDarkColorScheme
}

@Composable
fun MyApplicationTheme(
    appTheme: String = "multicolor",
    appearanceMode: String = "system",
    glassAccentColor: String = "multicolor",
    glassTransparency: Float = 0.55f,
    glassType: String = "soft",
    expressiveStyle: String = "solid",
    expressiveGlassAccentColor: String = "multicolor",
    expressiveGlassTransparency: Float = 0.58f,
    expressiveGlassFluidity: Float = 0.68f,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearanceMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val normalizedExpressiveStyle = expressiveStyle.takeIf { it in setOf("solid", "glass") } ?: "solid"
    val isExpressive = appTheme == "expressive"
    val isGlassSoft = appTheme == "glass"
    val isExpressiveGlass = isExpressive && normalizedExpressiveStyle == "glass"

    // Glass Soft e Glass Expressivo são independentes.
    val glassStyle = if (isGlassSoft) {
        resolveGlassSoftStyle(true, glassType, glassTransparency, glassAccentColor, darkTheme)
    } else {
        GlassSoftStyle()
    }
    val expressive = ExpressiveStyle(enabled = isExpressive, variant = normalizedExpressiveStyle)
    val expressiveGlassStyle = resolveExpressiveGlassStyle(
        enabled = isExpressiveGlass,
        isDark = darkTheme,
        accentName = expressiveGlassAccentColor,
        transparency = expressiveGlassTransparency,
        fluidity = expressiveGlassFluidity
    )
    val colorScheme = when {
        isGlassSoft -> glassSoftColorScheme(glassStyle)
        isExpressiveGlass -> expressiveGlassColorScheme(expressiveGlassStyle, darkTheme)
        isExpressive -> expressiveColorScheme(darkTheme)
        else -> getThemeColorScheme(appTheme, darkTheme)
    }
    val shapes = when {
        isExpressive -> ExpressiveShapes
        isGlassSoft -> GlassSoftShapes
        else -> MaterialTheme.shapes
    }

    CompositionLocalProvider(
        LocalGlassSoftStyle provides glassStyle,
        LocalExpressiveStyle provides expressive,
        LocalExpressiveGlassStyle provides expressiveGlassStyle
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (isExpressive) ExpressiveTypography else Typography,
            shapes = shapes,
            content = content
        )
    }
}

val MulticolorPalette = listOf(
    Pair(Color(0xFFE62325), Color.White),
    Pair(Color(0xFF388E3C), Color.White),
    Pair(Color(0xFF1976D2), Color.White),
    Pair(Color(0xFFF57C00), Color.White),
    Pair(Color(0xFFB8860B), Color.White)
)

fun getDynamicThemeColor(index: Int, appTheme: String, defaultColor: Color, defaultOnColor: Color): Pair<Color, Color> {
    if (appTheme == "multicolor") {
        return SessionMulticolorPalette[index % SessionMulticolorPalette.size]
    }
    return Pair(defaultColor, defaultOnColor)
}
