from pathlib import Path
import re

store_path = Path('app/src/main/java/com/example/data/PromotionChangeStore.kt')
store = store_path.read_text(encoding='utf-8')
old = '''        val delta = calculatePromotionChanges(previous.snapshot, currentSnapshot)\n        val mergedChanges = if (previous.dayKey == dayKey) {\n            mergeDailyChanges(previous.changes, delta)\n        } else {\n            delta.take(MAX_DAILY_CHANGES)\n        }\n        writeState(PromotionHistoryState(dayKey, currentSnapshot, mergedChanges))\n        PromotionChangeState(\n            dayKey = dayKey,\n            changes = mergedChanges,\n            baselineReady = true,\n            limitedBySafetyCap = false\n        )\n'''
new = '''        val delta = calculatePromotionChanges(previous.snapshot, currentSnapshot)\n        val recentChanges = if (delta.isNotEmpty()) {\n            delta.take(MAX_DAILY_CHANGES)\n        } else {\n            previous.changes.takeLast(MAX_DAILY_CHANGES)\n        }\n        writeState(PromotionHistoryState(dayKey, currentSnapshot, recentChanges))\n        PromotionChangeState(\n            dayKey = dayKey,\n            changes = recentChanges,\n            baselineReady = true,\n            limitedBySafetyCap = false\n        )\n'''
if old not in store:
    raise SystemExit('bloco de histórico não encontrado')
store = store.replace(old, new, 1)
store_path.write_text(store, encoding='utf-8')

screen_path = Path('app/src/main/java/com/example/ui/PromotionsScreen.kt')
s = screen_path.read_text(encoding='utf-8')

logout_pattern = re.compile(
    r'onClick\s*=\s*\{\s*scope\.launch\s*\{\s*promotionChangeStore\.clear\(\)\s*onLogout\(\)\s*\}\s*\},',
    re.MULTILINE,
)
s, count = logout_pattern.subn('onClick = { onLogout() },', s, count=1)
if count != 1:
    raise SystemExit(f'bloco de logout não encontrado: {count}')

replacements = [
    ('Text("Ofertas novas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)',
     'Text("Últimas mudanças", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)'),
    ('"Entraram, foram alteradas ou saíram hoje, organizadas por loja"',
     '"Última movimentação de ofertas, organizada por loja"'),
    ('"${changes.size} mudança(s) hoje • $currentStoreLabel"',
     '"${changes.size} mudança(s) na última atualização • $currentStoreLabel"'),
    ('"Nenhuma mudança de oferta registrada hoje."',
     '"Nenhuma mudança de oferta registrada ainda."'),
    ('"Nenhuma mudança de oferta registrada para $currentStoreLabel hoje."',
     '"Nenhuma mudança de oferta registrada para $currentStoreLabel ainda."'),
    ('"Quando uma oferta entrar, mudar ou sair, ela aparecerá aqui automaticamente."',
     '"As últimas mudanças ficam salvas aqui até o NRD detectar uma nova atualização de ofertas."'),
]
for old_text, new_text in replacements:
    if old_text not in s:
        raise SystemExit(f'trecho não encontrado: {old_text[:80]}')
    s = s.replace(old_text, new_text, 1)

screen_path.write_text(s, encoding='utf-8')
