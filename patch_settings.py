import sys
with open("app/src/main/java/com/example/ui/SettingsScreen.kt", "r") as f:
    content = f.read()

target = """            val themeOptions = listOf(
                "red" to "Vermelho (Padrão)",
                "gold" to "Dourado",
                "green" to "Verde",
                "blue" to "Azul",
                "orange" to "Laranja"
            )"""
replacement = """            val themeOptions = listOf(
                "multicolor" to "Multicolorido",
                "red" to "Vermelho",
                "gold" to "Dourado",
                "green" to "Verde",
                "blue" to "Azul",
                "orange" to "Laranja"
            )"""
if target in content:
    content = content.replace(target, replacement)
    
    target2 = 'value = themeOptions.find { it.first == appTheme }?.second ?: "Vermelho (Padrão)",'
    replacement2 = 'value = themeOptions.find { it.first == appTheme }?.second ?: "Multicolorido",'
    content = content.replace(target2, replacement2)
    
    target3 = 'val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "red")'
    replacement3 = 'val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")'
    content = content.replace(target3, replacement3)

    with open("app/src/main/java/com/example/ui/SettingsScreen.kt", "w") as f:
        f.write(content)
    print("Success")
else:
    print("Failed")
