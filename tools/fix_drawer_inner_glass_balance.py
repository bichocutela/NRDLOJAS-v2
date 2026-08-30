from pathlib import Path

path = Path('app/src/main/java/com/example/ui/AppNavGraph.kt')
text = path.read_text(encoding='utf-8')

anchor = '''    var expandedCategory by remember { mutableStateOf<String?>(null) }\n    val scope = rememberCoroutineScope()\n\n    Column(\n'''
insert = '''    val drawerInnerAlpha = when (glassType) {\n        "frosted" -> (0.88f - glassTransparency * 0.16f).coerceIn(0.74f, 0.88f)\n        "crystal" -> (0.78f - glassTransparency * 0.16f).coerceIn(0.62f, 0.78f)\n        else -> (0.84f - glassTransparency * 0.16f).coerceIn(0.68f, 0.84f)\n    }\n    var expandedCategory by remember { mutableStateOf<String?>(null) }\n    val scope = rememberCoroutineScope()\n\n    Column(\n'''
if anchor not in text:
    raise SystemExit('anchor drawer não encontrado')
text = text.replace(anchor, insert, 1)

old = '''                if (isGlassTheme) Modifier\n                    .background(sharedGlassStyle.fill.copy(alpha = sharedGlassStyle.alpha), RoundedCornerShape(28.dp))\n                    .border(1.dp, sharedGlassStyle.border, RoundedCornerShape(28.dp))\n                else Modifier\n'''
new = '''                if (isGlassTheme) Modifier\n                    .clip(RoundedCornerShape(28.dp))\n                    .background(sharedGlassStyle.fill.copy(alpha = drawerInnerAlpha))\n                    .border(1.dp, sharedGlassStyle.border, RoundedCornerShape(28.dp))\n                else Modifier\n'''
if old not in text:
    raise SystemExit('bloco visual drawer não encontrado')
text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
print('Painel interno Glass recalibrado')
