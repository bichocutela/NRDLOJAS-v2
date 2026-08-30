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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val accent: Color = Color(0xFF78B7F2),
    val surfaceAlpha: Float = 1f,
    val strongSurfaceAlpha: Float = 1f,
    val borderAlpha: Float = 0f,
    val isDark: Boolean = false
)

val LocalGlassSoftStyle = staticCompositionLocalOf { GlassSoftStyle() }

fun glassSoftAccent(name: String): Color = when (name) {
    "blue" -> Color(0xFF74B6F2)
    "green" -> Color(0xFF79CFA3)
    "purple" -> Color(0xFFA98AEF)
    "pink", "red" -> Color(0xFFEE88BE)
    "orange", "gold" -> Color(0xFFF4B768)
    "cyan" -> Color(0xFF65CFD4)
    else -> Color(0xFF82B8EF)
}

private fun glassSoftStyle(
    enabled: Boolean,
    type: String,
    transparency: Float,
    accentName: String,
    isDark: Boolean
): GlassSoftStyle {
    val safeTransparency = transparency.coerceIn(0.20f, 0.90f)
    val solidFactor = 1f - safeTransparency
    val surfaceAlpha = when (type) {
        "frosted" -> (0.68f + solidFactor * 0.26f).coerceIn(0.72f, 0.94f)
        "crystal" -> (0.26f + solidFactor * 0.34f).coerceIn(0.30f, 0.58f)
        else -> (0.46f + solidFactor * 0.34f).coerceIn(0.50f, 0.78f)
    }
    val borderAlpha = when (type) {
        "frosted" -> 0.54f
        "crystal" -> 0.90f
        else -> 0.70f
    }
    return GlassSoftStyle(
        enabled = enabled,
        type = type,
        transparency = safeTransparency,
        accentName = accentName,
        accent = glassSoftAccent(accentName),
        surfaceAlpha = surfaceAlpha,
        strongSurfaceAlpha = (surfaceAlpha + 0.13f).coerceAtMost(0.96f),
        borderAlpha = borderAlpha,
        isDark = isDark
    )
}

private fun glassSoftColorScheme(base: androidx.compose.material3.ColorScheme, style: GlassSoftStyle) =
    base.copy(
        primary = style.accent,
        onPrimary = if (style.isDark) Color(0xFF07131F) else Color.White,
        primaryContainer = style.accent.copy(alpha = if (style.type == "crystal") 0.34f else 0.55f),
        onPrimaryContainer = if (style.isDark) Color.White else Color(0xFF15202C),
        background = if (style.isDark) Color(0xFF0B111B).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
        surface = if (style.isDark) Color(0xFF101824).copy(alpha = style.surfaceAlpha) else Color.White.copy(alpha = style.surfaceAlpha),
        surfaceVariant = if (style.isDark) Color(0xFF192434).copy(alpha = style.strongSurfaceAlpha) else Color.White.copy(alpha = style.strongSurfaceAlpha),
        surfaceContainer = if (style.isDark) Color(0xFF142030).copy(alpha = style.surfaceAlpha) else Color.White.copy(alpha = style.surfaceAlpha),
        surfaceContainerLow = if (style.isDark) Color(0xFF101A28).copy(alpha = style.surfaceAlpha) else Color.White.copy(alpha = style.surfaceAlpha),
        surfaceContainerHigh = if (style.isDark) Color(0xFF1B283A).copy(alpha = style.strongSurfaceAlpha) else Color.White.copy(alpha = style.strongSurfaceAlpha),
        outline = Color.White.copy(alpha = style.borderAlpha),
        outlineVariant = style.accent.copy(alpha = if (style.type == "crystal") 0.54f else 0.30f),
        surfaceTint = Color.Transparent
    )

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
    val colors = if (style.isDark) {
        if (style.accentName == "multicolor") {
            listOf(Color(0xFF0B1725), Color(0xFF173329), Color(0xFF2A1E42), Color(0xFF351E31), Color(0xFF102C35))
        } else {
            listOf(Color(0xFF09121E), style.accent.copy(alpha = 0.44f), Color(0xFF21192E), Color(0xFF0D2027))
        }
    } else if (style.accentName == "multicolor") {
        listOf(Color(0xFFB9DEFA), Color(0xFFCBEFD9), Color(0xFFDCCBFF), Color(0xFFF8CFE7), Color(0xFFFFE2BB), Color(0xFFC6F0F1))
    } else {
        listOf(
            style.accent.copy(alpha = 0.58f),
            Color(0xFFD8EEFF),
            Color(0xFFE7D8FF),
            Color(0xFFFFDCEC),
            Color(0xFFD5F3EE)
        )
    }
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
    val fallbackTheme = when (glassAccentColor) {
        "green" -> "green"
        "orange" -> "orange"
        "blue", "purple", "pink", "cyan" -> "blue"
        "gold" -> "gold"
        "red" -> "red"
        else -> "multicolor"
    }
    val style = glassSoftStyle(isGlassSoft, glassType, glassTransparency, glassAccentColor, darkTheme)
    val baseScheme = getThemeColorScheme(if (isGlassSoft) fallbackTheme else appTheme, darkTheme)
    val colorScheme = if (isGlassSoft) glassSoftColorScheme(baseScheme, style) else baseScheme
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
