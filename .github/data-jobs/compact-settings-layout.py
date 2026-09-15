from pathlib import Path

p = Path("app/src/main/java/com/example/ui/SettingsScreen.kt")
s = p.read_text(encoding="utf-8")

replacements = [
    (
        '''                .padding(horizontal = 12.dp, vertical = 10.dp),\n            verticalArrangement = Arrangement.spacedBy(12.dp)''',
        '''                .padding(horizontal = 10.dp, vertical = 6.dp),\n            verticalArrangement = Arrangement.spacedBy(6.dp)'''
    ),
    (
        '''            if (appearanceExpanded) {\n                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {''',
        '''            if (appearanceExpanded) {\n                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {'''
    ),
    (
        '''            Button(onClick = { coroutineScope.launch { viewModel.userPreferences.setFontScale(1.0f) } }, modifier = Modifier.align(Alignment.End)) {\n                Text("Restaurar Padrão")\n            }''',
        '''            Button(\n                onClick = { coroutineScope.launch { viewModel.userPreferences.setFontScale(1.0f) } },\n                modifier = Modifier.align(Alignment.End).height(44.dp),\n                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)\n            ) {\n                Text("Restaurar Padrão")\n            }'''
    ),
    (
        '''            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))''',
        '''            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))'''
    ),
    (
        '''                Spacer(modifier = Modifier.height(8.dp))\n                val settingsGlassShape = RoundedCornerShape(20.dp)''',
        '''                Spacer(modifier = Modifier.height(4.dp))\n                val settingsGlassShape = RoundedCornerShape(18.dp)'''
    ),
    (
        '''                    Column(\n                        modifier = Modifier.padding(12.dp),\n                        verticalArrangement = Arrangement.spacedBy(8.dp)\n                    ) {''',
        '''                    Column(\n                        modifier = Modifier.padding(10.dp),\n                        verticalArrangement = Arrangement.spacedBy(6.dp)\n                    ) {'''
    ),
    (
        '''                                .height(112.dp)\n                                .glassSoftShadow(previewShape, 4.dp)\n                                .clip(previewShape)\n                                .background(Brush.linearGradient(previewColors))\n                                .padding(12.dp),''',
        '''                                .height(96.dp)\n                                .glassSoftShadow(previewShape, 4.dp)\n                                .clip(previewShape)\n                                .background(Brush.linearGradient(previewColors))\n                                .padding(8.dp),'''
    ),
    (
        '''                                Column(Modifier.padding(12.dp)) {''',
        '''                                Column(Modifier.padding(10.dp)) {'''
    ),
    (
        '''                        Spacer(modifier = Modifier.height(10.dp))\n            Text(\n                "Modo de aparência",''',
        '''                        Spacer(modifier = Modifier.height(4.dp))\n            Text(\n                "Modo de aparência",'''
    ),
    (
        '''            Spacer(modifier = Modifier.height(8.dp))\n\n            val normalizedAppearanceMode''',
        '''            Spacer(modifier = Modifier.height(4.dp))\n\n            val normalizedAppearanceMode'''
    ),
    (
        '''                                .padding(vertical = 12.dp, horizontal = 4.dp),''',
        '''                                .padding(vertical = 8.dp, horizontal = 4.dp),'''
    ),
    (
        '''                modifier = Modifier.padding(top = 8.dp),''',
        '''                modifier = Modifier.padding(top = 4.dp),'''
    ),
    (
        '''            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))''',
        '''            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))'''
    ),
    (
        '''            if (notificationsExpanded) {\n                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {''',
        '''            if (notificationsExpanded) {\n                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {'''
    ),
    (
        '''                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {''',
        '''                Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {'''
    ),
    (
        '''            if (feedbackExpanded) {\n                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {''',
        '''            if (feedbackExpanded) {\n                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {'''
    ),
    (
        '''                .padding(horizontal = 14.dp, vertical = 10.dp),''',
        '''                .padding(horizontal = 12.dp, vertical = 7.dp),'''
    ),
]

for old, new in replacements:
    if old not in s:
        raise SystemExit(f"Trecho esperado não encontrado:\n{old[:120]}")
    s = s.replace(old, new, 1)

# Há duas linhas de preferência de notificação com o mesmo recuo.
old_notify_indent = '''                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {'''
new_notify_indent = '''                Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {'''
if old_notify_indent in s:
    s = s.replace(old_notify_indent, new_notify_indent, 1)

# Compacta os cartões de histórico de feedback sem mudar conteúdo ou ações.
s = s.replace('''            modifier = Modifier.fillMaxWidth().padding(12.dp),\n            verticalAlignment = Alignment.CenterVertically,''', '''            modifier = Modifier.fillMaxWidth().padding(10.dp),\n            verticalAlignment = Alignment.CenterVertically,''', 1)
s = s.replace('''        Column(modifier = Modifier.padding(12.dp)) {\n            Text(suggestion.text, style = MaterialTheme.typography.bodyLarge)\n            Spacer(modifier = Modifier.height(6.dp))''', '''        Column(modifier = Modifier.padding(10.dp)) {\n            Text(suggestion.text, style = MaterialTheme.typography.bodyLarge)\n            Spacer(modifier = Modifier.height(4.dp))''', 1)

p.write_text(s, encoding="utf-8")
