package com.example.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

private val GlassSoftShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 10.dp,
        bottomEnd = 16.dp,
        bottomStart = 12.dp
    ),
    small = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 14.dp,
        bottomEnd = 22.dp,
        bottomStart = 16.dp
    ),
    medium = RoundedCornerShape(
        topStart = 32.dp,
        topEnd = 20.dp,
        bottomEnd = 30.dp,
        bottomStart = 22.dp
    ),
    large = RoundedCornerShape(
        topStart = 42.dp,
        topEnd = 26.dp,
        bottomEnd = 38.dp,
        bottomStart = 30.dp
    ),
    extraLarge = RoundedCornerShape(
        topStart = 54.dp,
        topEnd = 32.dp,
        bottomEnd = 48.dp,
        bottomStart = 36.dp
    )
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

/**
 * Fonte única para claro/escuro dentro de toda a árvore do app.
 * Não inferimos mais pelo background porque Glass usa background transparente.
 */
val LocalNrdDarkMode = staticCompositionLocalOf { false }

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
    val bubbleSpeed: Float = 1f,
    val bubbleMotion: String = "random",
    val bubbleSize: Float = 1f,
    val bubbleExtraCount: Int = 0,
    val bubbleBrightness: Float = 1f,
    val bubbleOutline: Boolean = true,
    val isDark: Boolean = false
) {
    val surfaceBase: Color
        get() = if (isDark) Color(0xFF101721) else Color.White
}

val LocalExpressiveGlassStyle = staticCompositionLocalOf { ExpressiveGlassStyle() }

/** Shared 60 Hz clock for all expressive liquid surfaces. */
val LocalExpressiveGlassMotion = staticCompositionLocalOf<MutableState<Float>?> { null }

/** Shared scroll signal so Glass Expressivo can suspend ambient animation during Home gestures. */
val LocalNrdDrawerIsOpen = staticCompositionLocalOf<MutableStateFlow<Boolean>?> { null }
val LocalDevicePerformanceTier = staticCompositionLocalOf {
    DevicePerformanceTier(
        isEntryLevel = false,
        maxBackgroundBubbles = 16,
        enableComplexShaders = true,
        pausePhysicsOnScroll = false,
        pausePhysicsOnDrawer = true
    )
}

internal val ExpressiveGlassAccentNames = listOf("multicolor", "red", "green", "orange", "blue", "gold")

private fun normalizeExpressiveGlassAccentName(name: String): String =
    name.trim().lowercase().takeIf { it in ExpressiveGlassAccentNames } ?: "multicolor"

private fun expressiveGlassContentColor(background: Color): Color {
    val dark = Color(0xFF17202A)
    val backgroundLuminance = background.luminance()
    val whiteContrast = 1.05f / (backgroundLuminance + 0.05f)
    val darkContrast = (backgroundLuminance + 0.05f) / (dark.luminance() + 0.05f)
    return if (whiteContrast >= darkContrast) Color.White else dark
}

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
    // The light Glass Expressivo theme uses one consistent clear-water base;
    // accent selection remains visible in the controls and bubble refractions.
    val light = listOf(
        Color(0xFFE1F5FE), Color(0xFF81D4FA), Color(0xFFE0F7FA), Color(0xFFB2EBF2)
    )
    val dark = mapOf(
        "multicolor" to listOf(Color(0xFF190F18), Color(0xFF0E1C30), Color(0xFF2B2110), Color(0xFF0F291F), Color(0xFF21172F)),
        "red" to listOf(Color(0xFF2A1116), Color(0xFF35161B), Color(0xFF2E1423), Color(0xFF2B1A12)),
        "green" to listOf(Color(0xFF10241A), Color(0xFF0E2D25), Color(0xFF102638), Color(0xFF1A2913)),
        "orange" to listOf(Color(0xFF2B1A0E), Color(0xFF3B2812), Color(0xFF321713), Color(0xFF2A2217)),
        "blue" to listOf(Color(0xFF0D1B2C), Color(0xFF102C42), Color(0xFF171A38), Color(0xFF102B2A)),
        "gold" to listOf(Color(0xFF2B220E), Color(0xFF3B2D11), Color(0xFF2A2619), Color(0xFF221D12))
    )
    return if (isDark) dark.getValue(normalized) else light
}

internal fun resolveExpressiveGlassStyle(
    enabled: Boolean,
    isDark: Boolean,
    accentName: String = "multicolor",
    transparency: Float = 0.58f,
    fluidity: Float = 0.68f,
    bubbleSpeed: Float = 1f,
    bubbleMotion: String = "random",
    bubbleSize: Float = 1f,
    bubbleExtraCount: Int = 0,
    bubbleBrightness: Float = 1f,
    bubbleOutline: Boolean = true
): ExpressiveGlassStyle {
    if (!enabled) return ExpressiveGlassStyle()
    val safeTransparency = transparency.coerceIn(0.20f, 0.90f)
    val safeFluidity = fluidity.coerceIn(0f, 1f)
    val progress = ((safeTransparency - 0.20f) / 0.70f).coerceIn(0f, 1f)
    val actions = expressiveGlassActionColors(accentName, isDark)
    // O vidro precisa deixar o fundo e as refrações atravessarem a superfície.
    // Antes o preenchimento branco/dourado ficava dominante e transformava o
    // efeito em cartões sólidos, principalmente no preset gold.
    val surfaceAlpha = 0.64f - (0.30f * progress)
    val normalized = normalizeExpressiveGlassAccentName(accentName)
    return ExpressiveGlassStyle(
        enabled = true,
        accentName = normalized,
        transparency = safeTransparency,
        fluidity = safeFluidity,
        accent = actions[0],
        secondaryAccent = actions[1],
        tertiaryAccent = actions[2],
        onAccent = expressiveGlassContentColor(actions[0]),
        surfaceAlpha = surfaceAlpha,
        strongSurfaceAlpha = (surfaceAlpha + 0.10f).coerceAtMost(0.82f),
        borderColor = Color.White.copy(alpha = (0.62f + 0.24f * safeFluidity).coerceAtMost(0.90f)),
        shadowElevation = 9f + (5f * safeFluidity),
        shadowAlpha = (if (isDark) 0.24f else 0.12f) + (0.08f * safeFluidity),
        bubbleSpeed = bubbleSpeed.coerceIn(0.25f, 2.5f),
        bubbleMotion = bubbleMotion.takeIf { it in setOf("random", "circular", "rise", "drift") } ?: "random",
        bubbleSize = bubbleSize.coerceIn(0.65f, 1.8f),
        bubbleExtraCount = bubbleExtraCount.coerceIn(0, 18),
        bubbleBrightness = bubbleBrightness.coerceIn(0.25f, 2f),
        bubbleOutline = bubbleOutline,
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
    secondaryAccent: Color? = null,
    intensity: Float = 1f,
    elevation: Dp? = null,
    animated: Boolean = false,
    waves: Boolean = false,
    bubbleSeed: Int = 0,
    lightweight: Boolean = false
): Modifier = composed {
    val style = LocalExpressiveGlassStyle.current
    if (!style.enabled) {
        this
    } else {
        val safeIntensity = intensity.coerceIn(0.45f, 1.40f)
        val fluidity = style.fluidity.coerceIn(0f, 1f)
        val tint = accent ?: style.accent
        val secondaryTint = secondaryAccent ?: style.secondaryAccent
        if (lightweight) {
            return@composed this.expressiveLightweightLiquidGlass(
                shape = shape,
                tint = tint,
                secondaryTint = secondaryTint,
                intensity = safeIntensity,
                waves = waves,
                bubbleSeed = bubbleSeed
            )
        }
        val motionState = if (animated || waves) {
            LocalExpressiveGlassMotion.current
        } else {
            null
        }
        val refraction = secondaryTint
        val highlightAlpha = (0.54f + 0.30f * fluidity) * safeIntensity
        val borderWidthDp = 1.85f + 1.85f * fluidity
        val ripple = remember { Animatable(1f) }
        var rippleCenter by remember { mutableStateOf(Offset.Zero) }
        val rippleScope = rememberCoroutineScope()

        this
            .shadow(
                elevation = elevation ?: style.shadowElevation.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = style.shadowAlpha * 0.72f),
                spotColor = tint.copy(alpha = (style.shadowAlpha + 0.10f).coerceAtMost(0.34f))
            )
            .clip(shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (up != null) {
                        rippleCenter = up.position
                        rippleScope.launch {
                            ripple.snapTo(0f)
                            ripple.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 900, easing = LinearEasing)
                            )
                        }
                    }
                }
            }
            .drawWithCache {
                // Read animated state during drawing so moving highlights invalidate the draw layer, not the whole card composition.
                val outline = shape.createOutline(size, layoutDirection, this)
                val outlinePath = Path().apply {
                    when (outline) {
                        is androidx.compose.ui.graphics.Outline.Rectangle -> addRect(outline.rect)
                        is androidx.compose.ui.graphics.Outline.Rounded -> addRoundRect(outline.roundRect)
                        is androidx.compose.ui.graphics.Outline.Generic -> addPath(outline.path)
                    }
                }
                val maxDimension = maxOf(size.width, size.height).coerceAtLeast(1f)
                val minDimension = minOf(size.width, size.height).coerceAtLeast(1f)
                val baseBrush = Brush.linearGradient(
                    colors = listOf(
                        style.surfaceBase.copy(alpha = (style.strongSurfaceAlpha * 0.40f).coerceIn(0f, 1f)),
                        tint.copy(alpha = (0.34f + 0.20f * fluidity) * safeIntensity),
                        style.surfaceBase.copy(alpha = (style.surfaceAlpha * 0.30f).coerceIn(0f, 1f)),
                        refraction.copy(alpha = (0.20f + 0.14f * fluidity) * safeIntensity),
                        style.surfaceBase.copy(alpha = (style.strongSurfaceAlpha * 0.36f).coerceIn(0f, 1f))
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                val topLens = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = highlightAlpha.coerceAtMost(0.82f)),
                        Color.White.copy(alpha = 0.22f * safeIntensity),
                        Color.Transparent
                    ),
                    center = Offset(
                        x = size.width * (0.21f + 0.06f * fluidity),
                        y = size.height * (0.04f + 0.07f * fluidity)
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
                        x = size.width * (0.80f - 0.06f * fluidity),
                        y = size.height * (0.895f - 0.07f * fluidity)
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
                    start = Offset(size.width * 0.13f, size.height * 0.04f),
                    end = Offset(size.width * 0.88f, size.height * 0.96f)
                )
                val iridescentRim = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color(0xFF80D8FF).copy(alpha = 0.58f),
                        Color(0xFFFF80AB).copy(alpha = 0.42f),
                        Color(0xFFFFD54F).copy(alpha = 0.52f),
                        Color.White.copy(alpha = 0.90f)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.5f)
                )
                val innerGleam = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f * safeIntensity),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.12f * safeIntensity)
                    ),
                    start = Offset(size.width * 0.10f, 0f),
                    end = Offset(size.width * 0.90f, size.height)
                )
                // Difusão óptica suave: cria a leitura de backdrop blur/frost sem borrar o conteúdo.
                val diffusionA = Brush.radialGradient(
                    colors = listOf(
                        style.surfaceBase.copy(alpha = (0.08f + 0.06f * fluidity) * safeIntensity),
                        Color.White.copy(alpha = if (style.isDark) 0.02f else 0.045f),
                        Color.Transparent
                    ),
                    center = Offset(
                        size.width * 0.46f,
                        size.height * 0.335f
                    ),
                    radius = maxDimension * (0.42f + 0.12f * fluidity)
                )
                val diffusionB = Brush.radialGradient(
                    colors = listOf(
                        refraction.copy(alpha = (0.085f + 0.075f * fluidity) * safeIntensity),
                        style.surfaceBase.copy(alpha = (0.045f + 0.03f * fluidity) * safeIntensity),
                        Color.Transparent
                    ),
                    center = Offset(
                        size.width * 0.44f,
                        size.height * 0.66f
                    ),
                    radius = maxDimension * (0.36f + 0.10f * fluidity)
                )
                val distortionHeight = size.height * (0.18f + 0.18f * fluidity)
                val bottomLiquidPath = Path().apply {
                    moveTo(0f, size.height - distortionHeight * (0.80f))
                    cubicTo(
                        size.width * 0.18f,
                        size.height - distortionHeight * (0.99f),
                        size.width * 0.34f,
                        size.height - distortionHeight * (0.42f),
                        size.width * 0.52f,
                        size.height - distortionHeight * (0.61f)
                    )
                    cubicTo(
                        size.width * 0.68f,
                        size.height - distortionHeight * (1.01f),
                        size.width * 0.84f,
                        size.height - distortionHeight * (0.35f),
                        size.width,
                        size.height - distortionHeight * (0.69f)
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                val upperLiquidPath = Path().apply {
                    moveTo(0f, distortionHeight * (0.46f))
                    cubicTo(
                        size.width * 0.24f,
                        distortionHeight * (0.17f),
                        size.width * 0.48f,
                        distortionHeight * (0.53f),
                        size.width * 0.70f,
                        distortionHeight * (0.32f)
                    )
                    cubicTo(
                        size.width * 0.82f,
                        distortionHeight * 0.04f,
                        size.width * 0.92f,
                        distortionHeight * (0.62f),
                        size.width,
                        distortionHeight * 0.24f
                    )
                    lineTo(size.width, 0f)
                    lineTo(0f, 0f)
                    close()
                }
                val liquidWaveBrush = Brush.horizontalGradient(
                    colors = listOf(
                        tint.copy(alpha = if (waves) (0.32f + 0.12f * fluidity) * safeIntensity else 0f),
                        Color.White.copy(alpha = if (waves) (0.24f + 0.10f * fluidity) * safeIntensity else 0f),
                        refraction.copy(alpha = if (waves) ((0.40f + 0.14f * fluidity) * safeIntensity).coerceAtMost(0.82f) else 0f),
                        Color.White.copy(alpha = if (waves) 0.16f * safeIntensity else 0f),
                        tint.copy(alpha = if (waves) 0.24f * safeIntensity else 0f)
                    )
                )
                val upperCausticBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (waves) ((0.48f + 0.18f * fluidity) * safeIntensity).coerceAtMost(0.92f) else 0f),
                        tint.copy(alpha = if (waves) 0.16f * safeIntensity else 0f),
                        Color.Transparent,
                        refraction.copy(alpha = if (waves) 0.18f * safeIntensity else 0f),
                        Color.White.copy(alpha = if (waves) 0.30f * safeIntensity else 0f)
                    )
                )
                val waveA = Brush.radialGradient(
                    colors = listOf(
                        tint.copy(alpha = if (waves) (0.24f + 0.12f * fluidity) * safeIntensity else 0f),
                        tint.copy(alpha = if (waves) 0.08f * safeIntensity else 0f),
                        Color.Transparent
                    ),
                    center = Offset(
                        x = size.width * (0.18f + 0.11f * ((bubbleSeed % 5 + 5) % 5)),
                        y = size.height * (0.74f - 0.08f * ((bubbleSeed % 3 + 3) % 3))
                    ),
                    radius = maxDimension * (0.42f + 0.12f * fluidity)
                )
                val waveB = Brush.radialGradient(
                    colors = listOf(
                        refraction.copy(alpha = if (waves) (0.18f + 0.10f * fluidity) * safeIntensity else 0f),
                        Color.Transparent
                    ),
                    center = Offset(
                        x = size.width * (0.76f - 0.07f * ((bubbleSeed % 4 + 4) % 4)),
                        y = size.height * (0.28f + 0.06f * ((bubbleSeed % 2 + 2) % 2))
                    ),
                    radius = maxDimension * (0.34f + 0.14f * fluidity)
                )
                onDrawWithContent {
                    // Animated values are read only in the draw phase. Gradients and paths above stay cached between frames.
                    val motion = motionState?.value?.let { (sin(it) + 1f) * 0.5f } ?: 0.5f
                    val travel = (motion - 0.5f) * 2f
                    val horizontalShift = size.width * 0.12f * travel
                    val verticalShift = size.height * 0.045f * travel
                    val rippleProgress = ripple.value
                    val rippleOrigin = rippleCenter

                    drawPath(path = outlinePath, brush = baseBrush)
                    drawPath(path = outlinePath, brush = diffusionA)
                    drawPath(path = outlinePath, brush = diffusionB)
                    clipPath(outlinePath) {
                        withTransform({ translate(left = horizontalShift, top = verticalShift) }) {
                            drawRect(brush = topLens)
                        }
                        withTransform({ translate(left = -horizontalShift, top = -verticalShift) }) {
                            drawRect(brush = lowerRefraction)
                        }
                    }
                    clipPath(outlinePath) {
                        withTransform({ translate(left = horizontalShift * 0.55f, top = -verticalShift * 0.6f) }) {
                            drawRect(brush = waveA)
                        }
                        withTransform({ translate(left = -horizontalShift * 0.55f, top = verticalShift * 0.6f) }) {
                            drawRect(brush = waveB)
                        }
                    }
                    drawPath(path = outlinePath, brush = innerGleam)
                    drawContent()
                    // Reflexos e gotas ficam na camada superior do vidro, como no mockup.
                    if (waves) {
                        withTransform({ translate(top = verticalShift) }) {
                            drawPath(path = upperLiquidPath, brush = upperCausticBrush)
                        }
                        withTransform({ translate(top = -verticalShift) }) {
                            drawPath(path = bottomLiquidPath, brush = liquidWaveBrush)
                        }
                    }
                    if (rippleProgress < 1f) {
                        val maxRippleRadius = maxDimension * 0.92f
                        for (ring in 0..2) {
                            val startDelay = ring * 0.13f
                            val ringProgress = ((rippleProgress - startDelay) / (1f - startDelay))
                                .coerceIn(0f, 1f)
                            if (rippleProgress >= startDelay) {
                                val fade = (1f - ringProgress) * (1f - ringProgress)
                                val radius = maxRippleRadius * ringProgress
                                drawCircle(
                                    color = Color.White.copy(alpha = (0.54f * fade).coerceAtMost(0.54f)),
                                    radius = radius,
                                    center = rippleOrigin,
                                    style = Stroke(width = (5.5f * (1f - ringProgress) + 1.2f).dp.toPx())
                                )
                                drawCircle(
                                    color = refraction.copy(alpha = (0.38f * fade).coerceAtMost(0.38f)),
                                    radius = radius * 0.97f,
                                    center = rippleOrigin,
                                    style = Stroke(width = (2.2f * (1f - ringProgress) + 0.7f).dp.toPx())
                                )
                            }
                        }
                        // Pequenas gotas acompanham a onda e se dispersam pelo vidro.
                        val droplets = listOf(
                            Offset(1f, 0f), Offset(0.72f, 0.69f), Offset(0f, 1f),
                            Offset(-0.72f, 0.69f), Offset(-1f, 0f), Offset(-0.72f, -0.69f),
                            Offset(0f, -1f), Offset(0.72f, -0.69f)
                        )
                        droplets.forEachIndexed { index, direction ->
                            val delay = (index % 3) * 0.07f
                            val progress = ((rippleProgress - delay) / (1f - delay)).coerceIn(0f, 1f)
                            if (rippleProgress >= delay) {
                                val fade = 1f - progress
                                val radius = maxDimension * (0.016f + 0.012f * fade)
                                val center = rippleOrigin + direction * (maxDimension * 0.34f * progress)
                                drawCircle(
                                    color = Color.White.copy(alpha = (0.78f * fade).coerceAtMost(0.78f)),
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = (1.15f * fade + 0.45f).dp.toPx())
                                )
                                drawCircle(
                                    color = refraction.copy(alpha = (0.25f * fade).coerceAtMost(0.25f)),
                                    radius = radius * 0.42f,
                                    center = center - Offset(radius * 0.28f, radius * 0.28f)
                                )
                            }
                        }
                    }
                    drawPath(
                        path = outlinePath,
                        brush = iridescentRim,
                        style = Stroke(width = 1.45.dp.toPx())
                    )
                    drawPath(
                        path = outlinePath,
                        brush = specularEdge,
                        style = Stroke(width = borderWidthDp.dp.toPx())
                    )
                    drawPath(
                        path = outlinePath,
                        color = refraction.copy(alpha = (0.22f + 0.20f * fluidity) * safeIntensity),
                        style = Stroke(width = (1.25f + 0.85f * fluidity).dp.toPx())
                    )
                    drawPath(
                        path = outlinePath,
                        color = Color.White.copy(alpha = (0.48f + 0.24f * fluidity) * safeIntensity),
                        style = Stroke(width = 0.92.dp.toPx())
                    )
                }
            }
    }
}

/**
 * Renderizador dos cartões repetidos da Home. Mantém a leitura de água/vidro e
 * a dispersão ao toque, mas evita sombra offscreen e a pilha óptica completa
 * usada pelos controles de destaque. Isso torna cada item barato para o
 * LazyColumn mover e criar durante uma rolagem.
 */
private fun Modifier.expressiveLightweightLiquidGlass(
    shape: Shape,
    tint: Color,
    secondaryTint: Color,
    intensity: Float,
    waves: Boolean,
    bubbleSeed: Int
): Modifier = composed {
    val style = LocalExpressiveGlassStyle.current
    val fluidity = style.fluidity.coerceIn(0f, 1f)
    val ripple = remember { Animatable(1f) }
    var rippleCenter by remember { mutableStateOf(Offset.Zero) }
    val rippleScope = rememberCoroutineScope()

    this
        .clip(shape)
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                if (up != null) {
                    rippleCenter = up.position
                    rippleScope.launch {
                        ripple.snapTo(0f)
                        ripple.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 760, easing = LinearEasing)
                        )
                    }
                }
            }
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val outlinePath = Path().apply {
                when (outline) {
                    is androidx.compose.ui.graphics.Outline.Rectangle -> addRect(outline.rect)
                    is androidx.compose.ui.graphics.Outline.Rounded -> addRoundRect(outline.roundRect)
                    is androidx.compose.ui.graphics.Outline.Generic -> addPath(outline.path)
                }
            }
            val maxDimension = maxOf(size.width, size.height).coerceAtLeast(1f)
            val seedShift = (((bubbleSeed % 7) + 7) % 7) / 7f
            val baseBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (style.isDark) 0.10f else 0.48f),
                    tint.copy(alpha = (0.30f + 0.16f * fluidity) * intensity),
                    style.surfaceBase.copy(alpha = (style.surfaceAlpha * 0.28f).coerceIn(0f, 1f)),
                    secondaryTint.copy(alpha = (0.18f + 0.10f * fluidity) * intensity)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val lensBrush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.62f * intensity).coerceAtMost(0.82f)),
                    Color.White.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(size.width * (0.18f + seedShift * 0.12f), size.height * 0.05f),
                radius = maxDimension * 0.62f
            )
            val waveBrush = Brush.horizontalGradient(
                colors = listOf(
                    tint.copy(alpha = if (waves) 0.18f * intensity else 0f),
                    Color.White.copy(alpha = if (waves) 0.30f * intensity else 0f),
                    secondaryTint.copy(alpha = if (waves) 0.24f * intensity else 0f),
                    Color.Transparent
                )
            )
            val wavePath = Path().apply {
                moveTo(0f, size.height * 0.73f)
                cubicTo(
                    size.width * 0.24f, size.height * 0.55f,
                    size.width * 0.55f, size.height * 0.90f,
                    size.width, size.height * 0.63f
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            val rimBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.88f),
                    tint.copy(alpha = 0.44f),
                    secondaryTint.copy(alpha = 0.36f),
                    Color.White.copy(alpha = 0.72f)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )

            onDrawWithContent {
                drawPath(outlinePath, baseBrush)
                clipPath(outlinePath) { drawRect(lensBrush) }
                drawContent()
                if (waves) drawPath(wavePath, waveBrush)

                val progress = ripple.value
                if (progress < 1f) {
                    val fade = (1f - progress) * (1f - progress)
                    val radius = maxDimension * 0.82f * progress
                    drawCircle(
                        color = Color.White.copy(alpha = 0.48f * fade),
                        radius = radius,
                        center = rippleCenter,
                        style = Stroke(width = (4.2f * (1f - progress) + 1f).dp.toPx())
                    )
                    val directions = listOf(
                        Offset(1f, 0f), Offset(0f, 1f), Offset(-1f, 0f), Offset(0f, -1f)
                    )
                    directions.forEach { direction ->
                        val dropRadius = maxDimension * (0.012f + 0.010f * fade)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.72f * fade),
                            radius = dropRadius,
                            center = rippleCenter + direction * (maxDimension * 0.30f * progress),
                            style = Stroke(width = 0.9.dp.toPx())
                        )
                    }
                }
                drawPath(outlinePath, rimBrush, style = Stroke(width = 1.6.dp.toPx()))
                drawPath(
                    outlinePath,
                    Color.White.copy(alpha = 0.46f),
                    style = Stroke(width = 0.8.dp.toPx())
                )
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


private class AmbientLiquidBubble(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    val radiusDp: Float,
    val color: Color,
    val phase: Float
)

@Composable
private fun AdaptiveWaterContainer(
    modifier: Modifier,
    primary: Color,
    secondary: Color,
    turbulence: Float,
    speedMultiplier: Float,
    motion: String,
    sizeMultiplier: Float,
    additionalBubbles: Int,
    brightness: Float,
    outlineEnabled: Boolean,
    isDark: Boolean,
    touchPoint: MutableState<Offset?>
) {
    val density = LocalDensity.current
    val drawerOpenSignal = LocalNrdDrawerIsOpen.current ?: remember { MutableStateFlow(false) }
    val performanceTier = LocalDevicePerformanceTier.current
    val normalizedExtraBubbles = additionalBubbles.coerceIn(0, 18)
    val bubbleCount = (4 + ((normalizedExtraBubbles * 12 + 9) / 18))
        .coerceAtMost(performanceTier.maxBackgroundBubbles)
    val enhancedLighting = performanceTier.enableComplexShaders
    // Keep bubbles moving through list gestures; only pause work hidden by the drawer.
    val pausePhysics = remember(performanceTier.pausePhysicsOnDrawer, drawerOpenSignal) {
        drawerOpenSignal.map { drawerOpen -> performanceTier.pausePhysicsOnDrawer && drawerOpen }
    }
    // This list changes only when the surface/profile changes; frame updates
    // mutate its preallocated bubble objects and invalidate drawing via frameTick.
    val bubbles = remember { ArrayList<AmbientLiquidBubble>(16) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(canvasSize, primary, secondary, isDark, bubbleCount) {
        bubbles.clear()
        if (canvasSize.width > 0f && canvasSize.height > 0f) {
            val random = Random(primary.hashCode() xor secondary.hashCode() xor canvasSize.width.toInt())
            repeat(bubbleCount) { index ->
                val radius = 21f + random.nextFloat() * 24f
                val tint = when (index % 4) {
                    0 -> primary
                    1 -> secondary
                    else -> Color.White
                }
                bubbles += AmbientLiquidBubble(
                    x = radius * density.density + random.nextFloat() * (canvasSize.width - radius * 2f * density.density).coerceAtLeast(1f),
                    y = radius * density.density + random.nextFloat() * (canvasSize.height - radius * 2f * density.density).coerceAtLeast(1f),
                    velocityX = (random.nextFloat() - 0.5f) * 16f * speedMultiplier,
                    velocityY = (random.nextFloat() - 0.5f) * 16f * speedMultiplier,
                    radiusDp = radius,
                    phase = random.nextFloat() * 6.28318f,
                    color = tint.copy(alpha = if (isDark) 0.34f else 0.48f)
                )
            }
        }
    }

    LaunchedEffect(canvasSize, turbulence, speedMultiplier, motion, sizeMultiplier, performanceTier, pausePhysics) {
        var lastFrameNanos = 0L
        while (true) {
            if (pausePhysics.first()) {
                pausePhysics.first { isPaused -> !isPaused }
                lastFrameNanos = 0L
                continue
            }
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L && canvasSize.width > 0f && canvasSize.height > 0f) {
                    val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f)
                        .coerceIn(0.005f, 0.020f)
                    var index = 0
                    while (index < bubbles.size) {
                        val bubble = bubbles[index]
                        val radius = bubble.radiusDp * sizeMultiplier * density.density
                        var vx: Float
                        var vy: Float
                        if (performanceTier.enableComplexShaders) {
                            val damping = 0.955f.toDouble()
                                .pow((dt / 0.016f).toDouble()).toFloat()
                            val time = frameNanos / 1_000_000_000f
                            val dx = bubble.x - canvasSize.width * 0.5f
                            val dy = bubble.y - canvasSize.height * 0.5f
                            val distance = hypot(dx, dy).coerceAtLeast(1f)
                            vx = when (motion) {
                                "circular" -> -dy / distance * 42f * speedMultiplier
                                "rise" -> bubble.velocityX * 0.96f + sin(time * 0.7f + bubble.phase) * 9f * speedMultiplier
                                "drift" -> 24f * speedMultiplier + sin(time * 0.24f + bubble.phase) * 10f
                                else -> bubble.velocityX + sin(time * 0.46f + bubble.phase) * (34f + 54f * turbulence) * dt * speedMultiplier
                            }
                            vy = when (motion) {
                                "circular" -> dx / distance * 42f * speedMultiplier
                                "rise" -> -34f * speedMultiplier + sin(time * 0.39f + bubble.phase) * 8f
                                "drift" -> sin(time * 0.39f + bubble.phase * 1.23f) * 7f
                                else -> bubble.velocityY + sin(time * 0.39f + bubble.phase * 1.23f) * (30f + 48f * turbulence) * dt * speedMultiplier
                            }
                            val touchRadius = 250f * density.density
                            touchPoint.value?.let { point ->
                                val touchX = bubble.x - point.x
                                val touchY = bubble.y - point.y
                                val touchDistance = hypot(touchX, touchY)
                                if (touchDistance in 1f..touchRadius) {
                                    val force = (1f - touchDistance / touchRadius) * (1500f + 2100f * turbulence) * dt
                                    vx += touchX / touchDistance * force
                                    vy += touchY / touchDistance * force
                                }
                            }
                            vx *= damping
                            vy *= damping
                            val speed = hypot(vx, vy)
                            val maxSpeed = 118f * speedMultiplier
                            if (speed > maxSpeed) {
                                vx = vx / speed * maxSpeed
                                vy = vy / speed * maxSpeed
                            }
                        } else {
                            // Entry devices use constant linear motion without trigonometry or collision forces.
                            vx = bubble.velocityX
                            vy = bubble.velocityY
                        }

                        var x = bubble.x + vx * dt
                        var y = bubble.y + vy * dt
                        if (x < -radius) x = canvasSize.width + radius
                        if (x > canvasSize.width + radius) x = -radius
                        if (y < -radius) y = canvasSize.height + radius
                        if (y > canvasSize.height + radius) y = -radius
                        bubble.velocityX = vx
                        bubble.velocityY = vy
                        bubble.x = x
                        bubble.y = y
                        index++
                    }
                }
                lastFrameNanos = frameNanos
                frameTick = frameNanos
            }
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .graphicsLayer { clip = true }
            .drawWithCache {
                val scale = density.density
                val rimStroke = Stroke(width = 1.45f * scale)
                val highlightStroke = Stroke(width = 3.1f * scale, cap = StrokeCap.Round)
                val bounceStroke = Stroke(width = 2.2f * scale, cap = StrokeCap.Round)
                val lightStrength = brightness.coerceIn(0.25f, 2f)
                val primaryGlow = primary.copy(alpha = (if (enhancedLighting) 0.13f else 0.07f) * lightStrength)
                val secondaryGlow = secondary.copy(alpha = (if (enhancedLighting) 0.08f else 0.035f) * lightStrength)
                val iridescentRim = if (enhancedLighting) {
                    Brush.sweepGradient(
                        listOf(Color.White.copy(alpha = 0.88f), Color(0xFF80D8FF).copy(alpha = 0.72f), Color(0xFFFF80AB).copy(alpha = 0.64f), Color(0xFFFFE082).copy(alpha = 0.76f), Color.White.copy(alpha = 0.88f))
                    )
                } else null
                val whiteHighlight = Color.White.copy(alpha = (if (isDark) 0.76f else 0.85f) * lightStrength)
                val whiteBounce = Color.White.copy(alpha = (if (isDark) 0.20f else 0.35f) * lightStrength)
                val whiteFillAlpha = (if (isDark) 0.035f else 0.10f) * lightStrength
                val whiteOutlineAlpha = (0.40f * lightStrength).coerceIn(0f, 0.9f)
                val entryBubbleFill = Color.White.copy(alpha = 0.35f)
                val entryBubbleRim = Color.White.copy(alpha = 0.55f)
                onDrawBehind {
                    if (frameTick == 0L) return@onDrawBehind
                    var index = 0
                    while (index < bubbles.size) {
                        val bubble = bubbles[index]
                        val cx = bubble.x
                        val cy = bubble.y
                        val radius = bubble.radiusDp * sizeMultiplier * scale
                        if (!enhancedLighting) {
                            drawCircle(entryBubbleFill, radius * 0.96f, Offset(cx, cy))
                            drawCircle(entryBubbleRim, radius * 0.96f, Offset(cx, cy), style = rimStroke)
                            index++
                            continue
                        }
                        val highlightX = cx - radius * 0.30f
                        val highlightY = cy - radius * 0.32f
                        if (enhancedLighting) {
                            drawCircle(primaryGlow, radius * 1.24f, Offset(cx, cy))
                            drawCircle(secondaryGlow, radius * 1.10f, Offset(cx, cy))
                        }
                        drawCircle(bubble.color, radius * 0.98f, Offset(cx, cy), alpha = (0.20f * lightStrength).coerceIn(0f, 0.75f))
                        drawCircle(Color.White, radius * 0.72f, Offset(highlightX, highlightY), alpha = whiteFillAlpha)
                        if (outlineEnabled) {
                            if (enhancedLighting) {
                                drawCircle(iridescentRim!!, radius * 0.98f, Offset(cx, cy), style = rimStroke)
                            } else {
                                drawCircle(Color.White, radius * 0.98f, Offset(cx, cy), alpha = whiteOutlineAlpha, style = rimStroke)
                            }
                        }
                        drawArc(whiteBounce, 28f, 118f, false, Offset(cx - radius * 0.84f, cy - radius * 0.84f), Size(radius * 1.68f, radius * 1.68f), style = bounceStroke)
                        drawArc(whiteHighlight, 190f, 85f, false, Offset(cx - radius * 0.78f, cy - radius * 0.78f), Size(radius * 1.56f, radius * 1.56f), style = highlightStroke)
                        drawCircle(whiteHighlight, radius * 0.09f, Offset(highlightX - radius * 0.04f, highlightY - radius * 0.04f))
                        index++
                    }
                }
            }
    ) {}
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
            val performanceTier = LocalDevicePerformanceTier.current
            val transition = rememberInfiniteTransition(label = "expressive-liquid-background")
            val driftState = if (performanceTier.enableComplexShaders) {
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 9800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "expressive-background-drift"
                )
            } else {
                remember { mutableStateOf(0f) }
            }
            val touchPoint = remember { mutableStateOf<Offset?>(null) }
            val touchScope = rememberCoroutineScope()
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var touchFadeJob: Job? = null
                        while (true) {
                            var releasedAt: Offset? = null
                            var activePoint: Offset? = null
                            var pointerReleased = false
                            awaitPointerEventScope {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                activePoint = event.changes.firstOrNull { it.pressed }?.position
                                releasedAt = event.changes.firstOrNull {
                                    it.previousPressed && !it.pressed
                                }?.position
                                pointerReleased = releasedAt != null
                            }
                            when {
                                activePoint != null -> {
                                    touchFadeJob?.cancel()
                                    touchPoint.value = activePoint
                                }
                                pointerReleased -> {
                                    touchFadeJob?.cancel()
                                    touchPoint.value = releasedAt
                                    touchFadeJob = touchScope.launch {
                                        delay(180)
                                        touchPoint.value = null
                                    }
                                }
                                else -> touchPoint.value = null
                            }
                        }
                    }
                    .background(Brush.verticalGradient(colors = colors))
                    .then(
                        if (performanceTier.enableComplexShaders) Modifier.drawWithCache {
                        val maxDimension = maxOf(size.width, size.height).coerceAtLeast(1f)
                        val lightScale = if (expressiveGlass.isDark) 0.52f else 1f
                        val ambientPrimary = expressiveGlass.accent
                        val ambientSecondary = expressiveGlass.secondaryAccent
                        val ambientTertiary = expressiveGlass.tertiaryAccent
                        val haloPrimary = Brush.radialGradient(
                            colors = listOf(
                                ambientPrimary.copy(alpha = 0.26f * lightScale),
                                ambientPrimary.copy(alpha = 0.09f * lightScale),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.18f + 0.18f * 0.5f),
                                size.height * (0.13f + 0.05f * 0.5f)
                            ),
                            radius = maxDimension * 0.54f
                        )
                        val haloSecondary = Brush.radialGradient(
                            colors = listOf(
                                ambientSecondary.copy(alpha = 0.23f * lightScale),
                                ambientSecondary.copy(alpha = 0.08f * lightScale),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.86f - 0.13f * 0.5f),
                                size.height * (0.34f + 0.08f * 0.5f)
                            ),
                            radius = maxDimension * 0.50f
                        )
                        val haloTertiary = Brush.radialGradient(
                            colors = listOf(
                                ambientTertiary.copy(alpha = 0.20f * lightScale),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.26f + 0.14f * 0.5f),
                                size.height * (0.77f - 0.06f * 0.5f)
                            ),
                            radius = maxDimension * 0.47f
                        )
                        val pearlLight = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (expressiveGlass.isDark) 0.08f else 0.42f),
                                Color.White.copy(alpha = if (expressiveGlass.isDark) 0.03f else 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(
                                size.width * (0.54f + 0.09f * 0.5f),
                                size.height * (0.55f - 0.07f * 0.5f)
                            ),
                            radius = maxDimension * 0.39f
                        )
                        val lowerGlow = Brush.radialGradient(
                            colors = listOf(
                                expressiveGlass.accent.copy(alpha = if (expressiveGlass.isDark) 0.13f else 0.20f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.76f, size.height * 0.96f),
                            radius = maxDimension * 0.45f
                        )
                        val causticBrush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (expressiveGlass.isDark) 0.07f else 0.48f),
                                ambientSecondary.copy(alpha = if (expressiveGlass.isDark) 0.08f else 0.34f),
                                Color.White.copy(alpha = if (expressiveGlass.isDark) 0.05f else 0.36f),
                                Color.Transparent
                            )
                        )
                        val causticPath = Path().apply {
                            moveTo(-size.width * 0.10f, size.height * (0.30f + 0.04f * 0.5f))
                            cubicTo(
                                size.width * 0.24f, size.height * (0.18f + 0.04f * 0.5f),
                                size.width * 0.58f, size.height * (0.44f - 0.06f * 0.5f),
                                size.width * 1.10f, size.height * (0.28f + 0.03f * 0.5f)
                            )
                        }
                        val lowerCausticPath = Path().apply {
                            moveTo(-size.width * 0.08f, size.height * (0.82f - 0.04f * 0.5f))
                            cubicTo(
                                size.width * 0.28f, size.height * (0.70f + 0.05f * 0.5f),
                                size.width * 0.66f, size.height * (0.92f - 0.06f * 0.5f),
                                size.width * 1.08f, size.height * (0.76f + 0.04f * 0.5f)
                            )
                        }
                        val causticStroke = Stroke(width = 8.dp.toPx())
                        val causticHighlightStroke = Stroke(width = 1.5.dp.toPx())
                        val lowerCausticStroke = Stroke(width = 6.dp.toPx())
                        onDrawBehind {
                            // The animated state is observed in the draw phase, so the app content is not recomposed each frame.
                            val driftTravel = driftState.value - 0.5f
                            withTransform({ translate(left = size.width * 0.18f * driftTravel, top = size.height * 0.05f * driftTravel) }) {
                                drawRect(brush = haloPrimary)
                            }
                            withTransform({ translate(left = -size.width * 0.13f * driftTravel, top = size.height * 0.08f * driftTravel) }) {
                                drawRect(brush = haloSecondary)
                            }
                            withTransform({ translate(left = size.width * 0.14f * driftTravel, top = -size.height * 0.06f * driftTravel) }) {
                                drawRect(brush = haloTertiary)
                            }
                            withTransform({ translate(left = size.width * 0.09f * driftTravel, top = -size.height * 0.07f * driftTravel) }) {
                                drawRect(brush = pearlLight)
                            }
                            drawRect(brush = lowerGlow)
                            withTransform({ translate(top = size.height * 0.04f * driftTravel) }) {
                                drawPath(causticPath, brush = causticBrush, style = causticStroke)
                                drawPath(causticPath, color = Color.White.copy(alpha = if (expressiveGlass.isDark) 0.10f else 0.45f), style = causticHighlightStroke)
                            }
                            withTransform({ translate(top = -size.height * 0.04f * driftTravel) }) {
                                drawPath(lowerCausticPath, brush = causticBrush, style = lowerCausticStroke)
                            }
                        }
                    } else Modifier
                    ),
                content = {
                    AdaptiveWaterContainer(
                        modifier = Modifier.fillMaxSize(),
                        primary = expressiveGlass.accent,
                        secondary = expressiveGlass.secondaryAccent,
                        turbulence = expressiveGlass.fluidity,
                        speedMultiplier = expressiveGlass.bubbleSpeed,
                        motion = expressiveGlass.bubbleMotion,
                        sizeMultiplier = expressiveGlass.bubbleSize,
                        additionalBubbles = expressiveGlass.bubbleExtraCount,
                        brightness = expressiveGlass.bubbleBrightness,
                        outlineEnabled = expressiveGlass.bubbleOutline,
                        isDark = expressiveGlass.isDark,
                        touchPoint = touchPoint
                    )
                    Box(modifier = Modifier.fillMaxSize(), content = { content() })
                }
            )
        }
        expressive.enabled -> {
            val dark = LocalNrdDarkMode.current
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

internal fun expressiveColorScheme(darkTheme: Boolean) = if (darkTheme) {
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
    expressiveGlassBubbleSpeed: Float = 1f,
    expressiveGlassBubbleMotion: String = "random",
    expressiveGlassBubbleSize: Float = 1f,
    expressiveGlassBubbleExtraCount: Int = 0,
    expressiveGlassBubbleBrightness: Float = 1f,
    expressiveGlassBubbleOutline: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearanceMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val performanceTier = rememberDevicePerformanceTier()
    val requestedExpressiveStyle = expressiveStyle.takeIf { it in setOf("solid", "glass") } ?: "solid"
    val normalizedExpressiveStyle = if (performanceTier.enableComplexShaders) requestedExpressiveStyle else "solid"
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
        fluidity = expressiveGlassFluidity,
        bubbleSpeed = expressiveGlassBubbleSpeed,
        bubbleMotion = expressiveGlassBubbleMotion,
        bubbleSize = expressiveGlassBubbleSize,
        bubbleExtraCount = expressiveGlassBubbleExtraCount,
        bubbleBrightness = expressiveGlassBubbleBrightness,
        bubbleOutline = expressiveGlassBubbleOutline
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

    val drawerIsOpen = remember { MutableStateFlow(false) }
    val expressiveGlassMotion = remember { mutableStateOf(0f) }
    LaunchedEffect(isExpressiveGlass, performanceTier.enableComplexShaders) {
        if (!isExpressiveGlass || !performanceTier.enableComplexShaders) {
            expressiveGlassMotion.value = 0f
            return@LaunchedEffect
        }
        var lastUpdateNanos = 0L
        val minimumFrameIntervalNanos = 16_000_000L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastUpdateNanos == 0L || frameNanos - lastUpdateNanos >= minimumFrameIntervalNanos) {
                    val cycleNanos = 3_800_000_000L
                    val cycleProgress = (frameNanos % cycleNanos).toFloat() / cycleNanos.toFloat()
                    expressiveGlassMotion.value = cycleProgress * 6.2831855f
                    lastUpdateNanos = frameNanos
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalGlassSoftStyle provides glassStyle,
        LocalExpressiveStyle provides expressive,
        LocalNrdDrawerIsOpen provides drawerIsOpen,
        LocalDevicePerformanceTier provides performanceTier,
        LocalExpressiveGlassStyle provides expressiveGlassStyle,
        LocalExpressiveGlassMotion provides expressiveGlassMotion,
        LocalNrdDarkMode provides darkTheme
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
