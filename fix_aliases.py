import re

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

aliases_old = """    val aliases = mapOf(
        "multicolor" to "$packageName.MainActivityMulticolor",
        "red" to "$packageName.MainActivityRed",
        "green" to "$packageName.MainActivityGreen",
        "blue" to "$packageName.MainActivityBlue",
        "orange" to "$packageName.MainActivityOrange",
        "gold" to "$packageName.MainActivityGold"
    )"""

aliases_new = """    val aliases = mapOf(
        "multicolor" to "com.example.MainActivityMulticolor",
        "red" to "com.example.MainActivityRed",
        "green" to "com.example.MainActivityGreen",
        "blue" to "com.example.MainActivityBlue",
        "orange" to "com.example.MainActivityOrange",
        "gold" to "com.example.MainActivityGold"
    )"""

content = content.replace(aliases_old, aliases_new)

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
