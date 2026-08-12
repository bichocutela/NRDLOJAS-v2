import sys
with open("app/src/main/java/com/example/ui/SearchScreen.kt", "r") as f:
    content = f.read()

target = """    val normalizedTheme = when (appTheme.trim().lowercase()) {
        "gold" -> "gold"
        "green" -> "green"
        "blue" -> "blue"
        "orange" -> "orange"
        else -> "red"
    }"""
replacement = """    val normalizedTheme = when (appTheme.trim().lowercase()) {
        "multicolor" -> "multicolor"
        "gold" -> "gold"
        "green" -> "green"
        "blue" -> "blue"
        "orange" -> "orange"
        else -> "red"
    }"""
if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/SearchScreen.kt", "w") as f:
        f.write(content)
    print("Success")
else:
    print("Failed")
