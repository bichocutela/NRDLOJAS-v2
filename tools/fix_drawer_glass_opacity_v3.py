from pathlib import Path

path = Path('app/src/main/java/com/example/ui/AppNavGraph.kt')
text = path.read_text(encoding='utf-8')
old = '''    val drawerInnerAlpha = when (glassType) {
        "frosted" -> (0.88f - glassTransparency * 0.16f).coerceIn(0.74f, 0.88f)
        "crystal" -> (0.78f - glassTransparency * 0.16f).coerceIn(0.62f, 0.78f)
        else -> (0.84f - glassTransparency * 0.16f).coerceIn(0.68f, 0.84f)
    }
'''
new = '''    val drawerInnerAlpha = when (glassType) {
        "frosted" -> (0.98f - glassTransparency * 0.05f).coerceIn(0.94f, 0.98f)
        "crystal" -> (0.94f - glassTransparency * 0.07f).coerceIn(0.88f, 0.94f)
        else -> (0.96f - glassTransparency * 0.06f).coerceIn(0.91f, 0.96f)
    }
'''
if old not in text:
    raise SystemExit('bloco drawerInnerAlpha não encontrado')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
print('Opacidade interna do drawer Glass reforçada')
