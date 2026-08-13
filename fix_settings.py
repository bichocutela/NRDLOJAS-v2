import re

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('.androidx.compose.foundation.clickable', '.clickable')

# Make sure clickable is imported
if 'import androidx.compose.foundation.clickable' not in content:
    content = content.replace('import androidx.compose.foundation.layout.*', 'import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.clickable')

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

