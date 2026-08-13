package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    background = Color(0xFF101313),
    onBackground = Color(0xFFE7EDEC),
    surface = Color(0xFF151A1A),
    onSurface = Color(0xFFE7EDEC),
    surfaceVariant = Color(0xFF252B2B),
    onSurfaceVariant = Color(0xFFC0C8C7),
    outline = Color(0xFF899391)
)

private fun getThemeColorScheme(themeName: String, darkTheme: Boolean) = when (themeName) {
    "multicolor" -> if (darkTheme) {
        DefaultDarkColorScheme.copy(
            primary = Color(0xFF6DCE70),
            onPrimary = Color(0xFF003910),
            primaryContainer = Color(0xFF0F5B22),
            onPrimaryContainer = Color(0xFFB5F5B2),
            secondary = Color(0xFFF0C553),
            secondaryContainer = Color(0xFF604800),
            onSecondary = Color(0xFF3B2E00),
            tertiary = Color(0xFF82B1FF),
            tertiaryContainer = Color(0xFF0B4F92),
            onTertiary = Color(0xFF002C58),
            onTertiaryContainer = Color(0xFFD7E5FF),
            error = Color(0xFFFF6B6B)
        )
    } else {
        DefaultLightColorScheme.copy(
            primary = Color(0xFF388E3C),
            primaryContainer = Color(0xFF2E7D32),
            onPrimary = Color.White,
            onPrimaryContainer = Color.White,
            secondary = Color(0xFFD4AF37),
            secondaryContainer = Color(0xFFF5E3A9),
            onSecondary = Color(0xFF212121),
            onSecondaryContainer = Color(0xFF212121),
            tertiary = Color(0xFF1976D2),
            tertiaryContainer = Color(0xFFBBDEFB),
            onTertiary = Color.White,
            onTertiaryContainer = Color(0xFF212121),
            error = Color(0xFFE62325)
        )
    }
    "gold" -> if (darkTheme) {
        DefaultDarkColorScheme.copy(
            primary = Color(0xFFF0C553),
            onPrimary = Color(0xFF3E3000),
            primaryContainer = Color(0xFF6B5200),
            onPrimaryContainer = Color(0xFFFFEAB0)
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
            onPrimary = Color(0xFF003910),
            primaryContainer = Color(0xFF0F5B22),
            onPrimaryContainer = Color(0xFFB5F5B2)
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
            onPrimary = Color(0xFF00315C),
            primaryContainer = Color(0xFF0B4F92),
            onPrimaryContainer = Color(0xFFD7E5FF)
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
            onPrimary = Color(0xFF4A2600),
            primaryContainer = Color(0xFF7A3E00),
            onPrimaryContainer = Color(0xFFFFDCC2)
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
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearanceMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = getThemeColorScheme(appTheme, darkTheme)
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

val MulticolorPalette = listOf(
    Pair(Color(0xFFE62325), Color.White),
    Pair(Color(0xFF388E3C), Color.White),
    Pair(Color(0xFF1976D2), Color.White),
    Pair(Color(0xFFFF9800), Color.White),
    Pair(Color(0xFFD4AF37), Color(0xFF212121))
)

fun getDynamicThemeColor(index: Int, appTheme: String, defaultColor: Color, defaultOnColor: Color): Pair<Color, Color> {
    if (appTheme == "multicolor") {
        return MulticolorPalette[index % MulticolorPalette.size]
    }
    return Pair(defaultColor, defaultOnColor)
}
