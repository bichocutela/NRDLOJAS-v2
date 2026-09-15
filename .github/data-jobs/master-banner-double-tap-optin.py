from pathlib import Path

path = Path('app/src/main/java/com/example/ui/SearchScreen.kt')
text = path.read_text(encoding='utf-8')

old_import = 'import androidx.compose.foundation.background\n'
new_import = 'import androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.background\n'
if new_import not in text:
    if text.count(old_import) != 1:
        raise SystemExit('import base não encontrado de forma única')
    text = text.replace(old_import, new_import, 1)

old_optin = '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun SearchScreen('
new_optin = '@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)\n@Composable\nfun SearchScreen('
if new_optin not in text:
    if text.count(old_optin) != 1:
        raise SystemExit('anotação SearchScreen não encontrada de forma única')
    text = text.replace(old_optin, new_optin, 1)

path.write_text(text, encoding='utf-8')
print('Opt-in do combinedClickable aplicado.')
