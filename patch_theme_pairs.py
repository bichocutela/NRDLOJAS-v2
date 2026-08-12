import sys
with open("app/src/main/java/com/example/ui/theme/Theme.kt", "r") as f:
    content = f.read()

target = """val MulticolorPalette = listOf(
    Color(0xFFE62325), // Vermelho
    Color(0xFF388E3C), // Verde
    Color(0xFF1976D2), // Azul
    Color(0xFFFF9800), // Laranja
    Color(0xFFD4AF37)  // Dourado
)

fun getDynamicThemeColor(index: Int, appTheme: String, defaultColor: Color): Color {
    if (appTheme == "multicolor") {
        return MulticolorPalette[index % MulticolorPalette.size]
    }
    return defaultColor
}"""

replacement = """val MulticolorPalette = listOf(
    Pair(Color(0xFFE62325), Color.White), // Vermelho
    Pair(Color(0xFF388E3C), Color.White), // Verde
    Pair(Color(0xFF1976D2), Color.White), // Azul
    Pair(Color(0xFFFF9800), Color.White), // Laranja
    Pair(Color(0xFFD4AF37), Color(0xFF212121))  // Dourado
)

fun getDynamicThemeColor(index: Int, appTheme: String, defaultColor: Color, defaultOnColor: Color): Pair<Color, Color> {
    if (appTheme == "multicolor") {
        return MulticolorPalette[index % MulticolorPalette.size]
    }
    return Pair(defaultColor, defaultOnColor)
}"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/theme/Theme.kt", "w") as f:
        f.write(content)
    print("Success")
else:
    print("Failed")
