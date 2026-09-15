from pathlib import Path
p = Path('app/src/main/java/com/example/ui/AcpProductsPanel.kt')
text = p.read_text(encoding='utf-8')
old = '@Composable\ninternal fun AcpProductsPanel('
new = '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\ninternal fun AcpProductsPanel('
if new not in text:
    if text.count(old) != 1:
        raise SystemExit(f'assinatura esperada não encontrada de forma única: {text.count(old)}')
    text = text.replace(old, new, 1)
p.write_text(text, encoding='utf-8')
print('Opt-in aplicado.')
