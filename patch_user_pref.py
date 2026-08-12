import sys
with open("app/src/main/java/com/example/data/UserPreferences.kt", "r") as f:
    content = f.read()

target = """    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "red"
    }"""
replacement = """    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "multicolor"
    }"""
if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/data/UserPreferences.kt", "w") as f:
        f.write(content)
    print("Success")
else:
    print("Failed")
