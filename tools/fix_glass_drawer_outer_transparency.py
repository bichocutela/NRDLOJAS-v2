from pathlib import Path

path = Path('app/src/main/java/com/example/ui/AppNavGraph.kt')
text = path.read_text(encoding='utf-8')

anchor = '    var userRole by remember { mutableStateOf(initialRole ?: "user") }\n'
insert = '''    var userRole by remember { mutableStateOf(initialRole ?: "user") }\n    val drawerAppTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")\n    val isGlassDrawer = drawerAppTheme.trim().lowercase() == "glass"\n'''
if anchor not in text:
    raise SystemExit('anchor AppNavGraph não encontrado')
text = text.replace(anchor, insert, 1)

old = '''        drawerContent = {\n            ModalDrawerSheet {\n                LoginDrawerContent(\n'''
new = '''        drawerContent = {\n            ModalDrawerSheet(\n                drawerContainerColor = if (isGlassDrawer) Color.Transparent else MaterialTheme.colorScheme.surface\n            ) {\n                LoginDrawerContent(\n'''
if old not in text:
    raise SystemExit('ModalDrawerSheet alvo não encontrado')
text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
print('Transparência externa do drawer Glass aplicada')
