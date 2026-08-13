import re

with open('app/src/main/java/com/example/data/UserPreferences.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add appIcon flow
flow_pattern = r'val appTheme: Flow<String> = context\.dataStore\.data\.map \{ preferences ->\s*preferences\[APP_THEME\] \?: "multicolor"\s*\}'
flow_replacement = '''val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "multicolor"
    }

    val appIcon: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_ICON] ?: "multicolor"
    }'''

content = re.sub(flow_pattern, flow_replacement, content)

# Add setAppIcon
func_pattern = r'suspend fun setAppTheme\(theme: String\) \{\s*context\.dataStore\.edit \{ it\[APP_THEME\] = theme \}\s*\}'
func_replacement = '''suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { it[APP_THEME] = theme }
    }

    suspend fun setAppIcon(icon: String) {
        context.dataStore.edit { it[APP_ICON] = icon }
    }'''

content = re.sub(func_pattern, func_replacement, content)

# Add APP_ICON key
key_pattern = r'val APP_THEME = stringPreferencesKey\("app_theme"\)'
key_replacement = '''val APP_THEME = stringPreferencesKey("app_theme")
        val APP_ICON = stringPreferencesKey("app_icon")'''

content = re.sub(key_pattern, key_replacement, content)

with open('app/src/main/java/com/example/data/UserPreferences.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Prefs updated")
