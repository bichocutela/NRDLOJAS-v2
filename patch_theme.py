import sys
with open("app/src/main/java/com/example/ui/theme/Theme.kt", "r") as f:
    content = f.read()

target = """private fun getThemeColorScheme(themeName: String) = when(themeName) {"""
replacement = """private fun getThemeColorScheme(themeName: String) = when(themeName) {
    "multicolor" -> DefaultLightColorScheme.copy(
        primary = Color(0xFF388E3C), // Verde
        primaryContainer = Color(0xFF2E7D32),
        onPrimary = Color.White,
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFD4AF37), // Dourado
        secondaryContainer = Color(0xFFF5E3A9),
        onSecondary = Color(0xFF212121),
        onSecondaryContainer = Color(0xFF212121),
        tertiary = Color(0xFF1976D2), // Azul
        tertiaryContainer = Color(0xFFBBDEFB),
        onTertiary = Color.White,
        onTertiaryContainer = Color(0xFF212121),
        error = Color(0xFFE62325) // Vermelho (usando para destaques como erro)
    )"""

if target in content:
    content = content.replace(target, replacement)
    
    target2 = 'appTheme: String = "red"'
    replacement2 = 'appTheme: String = "multicolor"'
    content = content.replace(target2, replacement2)

    with open("app/src/main/java/com/example/ui/theme/Theme.kt", "w") as f:
        f.write(content)
    print("Success")
else:
    print("Failed")
