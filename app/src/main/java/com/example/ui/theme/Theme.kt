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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearanceMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val isGlassSoft = appTheme == "glass"
    val style = resolveGlassSoftStyle(isGlassSoft, glassType, glassTransparency, glassAccentColor, darkTheme)
    val colorScheme = if (isGlassSoft) glassSoftColorScheme(style) else getThemeColorScheme(appTheme, darkTheme)
    CompositionLocalProvider(LocalGlassSoftStyle provides style) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = if (isGlassSoft) GlassSoftShapes else MaterialTheme.shapes,
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
