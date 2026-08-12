import sys
with open("app/src/main/java/com/example/ui/SettingsScreen.kt", "r") as f:
    content = f.read()

# Add import if needed
if "import com.example.ui.theme.getDynamicThemeColor" not in content:
    content = content.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport com.example.ui.theme.getDynamicThemeColor")

# Replace Title colors
content = content.replace('Text("Preferências de Interface", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)', 
                          'val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")\n            Text("Preferências de Interface", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(0, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)')

content = content.replace('val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")\n            var expandedThemeMenu', 'var expandedThemeMenu')

content = content.replace('Text("Tema do Aplicativo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)', 
                          'Text("Tema do Aplicativo", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(1, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)')

content = content.replace('Text("Vibração", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)', 
                          'Text("Vibração", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(2, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)')

content = content.replace('Text("Notificações", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)', 
                          'Text("Notificações", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(3, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)')

content = content.replace('Text("Feedback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)', 
                          'Text("Feedback", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(4, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)')

with open("app/src/main/java/com/example/ui/SettingsScreen.kt", "w") as f:
    f.write(content)
print("Success")
