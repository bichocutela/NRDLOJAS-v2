from pathlib import Path

# 1) Consulta ACP: ações importantes se reorganizam em telas muito estreitas.
p = Path('app/src/main/java/com/example/ui/AcpProductsPanel.kt')
s = p.read_text(encoding='utf-8')
old = '''                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openProduct(product) }, enabled = !detailBusy, modifier = Modifier.weight(1f)) {
                                Text("Atualizar preços")
                            }
                            OutlinedButton(
                                onClick = {
                                    val barcodeValue = product.barcode.ifBlank { product.code }.trim()
                                    if (barcodeValue.isNotBlank()) {
                                        barcodeDialogProduct = com.example.data.Product(
                                            code = barcodeValue,
                                            name = product.description,
                                            searchName = product.description.lowercase(Locale.getDefault()),
                                            category = product.categories.firstOrNull().orEmpty().ifBlank { "Varejo" },
                                            unit = product.unit ?: "un"
                                        )
                                    }
                                },
                                enabled = !detailBusy && (product.barcode.isNotBlank() || product.code.isNotBlank()),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ver Cód Barra")
                            }
                        }
'''
new = '''                        NrdTwoActionLayout(
                            stackOnCompact = true,
                            first = { actionModifier ->
                                TextButton(
                                    onClick = { openProduct(product) },
                                    enabled = !detailBusy,
                                    modifier = actionModifier
                                ) { Text("Atualizar preços", maxLines = 2) }
                            },
                            second = { actionModifier ->
                                OutlinedButton(
                                    onClick = {
                                        val barcodeValue = product.barcode.ifBlank { product.code }.trim()
                                        if (barcodeValue.isNotBlank()) {
                                            barcodeDialogProduct = com.example.data.Product(
                                                code = barcodeValue,
                                                name = product.description,
                                                searchName = product.description.lowercase(Locale.getDefault()),
                                                category = product.categories.firstOrNull().orEmpty().ifBlank { "Varejo" },
                                                unit = product.unit ?: "un"
                                            )
                                        }
                                    },
                                    enabled = !detailBusy && (product.barcode.isNotBlank() || product.code.isNotBlank()),
                                    modifier = actionModifier
                                ) { Text("Ver Cód Barra", maxLines = 2) }
                            }
                        )
'''
if old not in s:
    raise SystemExit('AcpProductsPanel action row not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# 2) Dialogo de codigo de barras: largura, padding e tipografia adaptam em telas pequenas.
p = Path('app/src/main/java/com/example/ui/ProductBarcodeDialog.kt')
s = p.read_text(encoding='utf-8')
anchor = '    val context = LocalContext.current\n'
if 'val screenProfile = rememberNrdScreenProfile()' not in s:
    s = s.replace(anchor, anchor + '    val screenProfile = rememberNrdScreenProfile()\n', 1)
s = s.replace('.fillMaxWidth(0.9f)\n                        .padding(vertical = 24.dp)', '.fillMaxWidth(if (screenProfile.compact) 0.96f else 0.9f)\n                        .padding(vertical = if (screenProfile.compact) 10.dp else 24.dp)', 1)
s = s.replace('.padding(24.dp)\n                    ) {', '.padding(if (screenProfile.compact) 14.dp else 24.dp)\n                    ) {', 1)
s = s.replace('fontSize = 28.sp * barcodeTitleScale', 'fontSize = (if (screenProfile.compact) 23.sp else 28.sp) * barcodeTitleScale', 1)
s = s.replace('fontSize = 42.sp * barcodeNumberScale', 'fontSize = (if (screenProfile.compact) 34.sp else 42.sp) * barcodeNumberScale', 1)
s = s.replace('horizontalArrangement = Arrangement.spacedBy(8.dp)\n                        ) {\n                            listOf("Padrão", "Symbol", "Datalogic")', 'horizontalArrangement = Arrangement.spacedBy(if (screenProfile.veryCompact) 4.dp else 8.dp)\n                        ) {\n                            listOf("Padrão", "Symbol", "Datalogic")', 1)
s = s.replace('Text(profile, fontSize = 12.sp, maxLines = 1)', 'Text(profile, fontSize = if (screenProfile.veryCompact) 10.sp else 12.sp, maxLines = 1)', 2)
p.write_text(s, encoding='utf-8')

# 3) Dashboard Mestre: cartões e ações viram coluna em telas muito estreitas.
p = Path('app/src/main/java/com/example/ui/MestreDashboardOverview.kt')
s = p.read_text(encoding='utf-8')
anchor = ') {\n    Text("Visão geral", style = MaterialTheme.typography.titleLarge)'
if anchor in s and 'val screenProfile = rememberNrdScreenProfile()' not in s.split('internal fun MestreDashboardOverview',1)[1].split('internal fun MestrePanelAreaNavigation',1)[0]:
    s = s.replace(anchor, ') {\n    val screenProfile = rememberNrdScreenProfile()\n    Text("Visão geral", style = MaterialTheme.typography.titleLarge)', 1)

# Replace the two metric rows and two quick-action rows by a layout that stacks under 340dp.
blocks = [
('''    Row(\n        modifier = Modifier.fillMaxWidth(),\n        horizontalArrangement = Arrangement.spacedBy(6.dp)\n    ) {\n        DashboardMetricCard(\n            title = "Pendências",\n            value = pendingSuggestions.toString(),\n            icon = Icons.Default.PendingActions,\n            modifier = Modifier.weight(1f)\n        )\n        DashboardMetricCard(\n            title = "Produtos",\n            value = productCount.toString(),\n            icon = Icons.Default.Inventory,\n            modifier = Modifier.weight(1f)\n        )\n    }''',
'''    NrdTwoActionLayout(\n        stackOnCompact = true,\n        first = { m -> DashboardMetricCard("Pendências", pendingSuggestions.toString(), Icons.Default.PendingActions, m) },\n        second = { m -> DashboardMetricCard("Produtos", productCount.toString(), Icons.Default.Inventory, m) }\n    )'''),
('''    Row(\n        modifier = Modifier.fillMaxWidth(),\n        horizontalArrangement = Arrangement.spacedBy(6.dp)\n    ) {\n        DashboardMetricCard(\n            title = "Categorias ativas",\n            value = "$activeCategoryCount de $categoryCount",\n            icon = Icons.Default.Category,\n            modifier = Modifier.weight(1f)\n        )\n        DashboardMetricCard(\n            title = "Último backup",\n            value = latestBackupAt?.let(::formatDashboardDate) ?: "Nenhum",\n            icon = Icons.Default.Backup,\n            modifier = Modifier.weight(1f)\n        )\n    }''',
'''    NrdTwoActionLayout(\n        stackOnCompact = true,\n        first = { m -> DashboardMetricCard("Categorias ativas", "$activeCategoryCount de $categoryCount", Icons.Default.Category, m) },\n        second = { m -> DashboardMetricCard("Último backup", latestBackupAt?.let(::formatDashboardDate) ?: "Nenhum", Icons.Default.Backup, m) }\n    )'''),
('''    Row(\n        modifier = Modifier.fillMaxWidth(),\n        horizontalArrangement = Arrangement.spacedBy(6.dp)\n    ) {\n        DashboardQuickAction(\n            title = "Produtos",\n            description = "Gerenciar catálogo",\n            icon = Icons.Default.Inventory,\n            onClick = onOpenCatalog,\n            modifier = Modifier.weight(1f)\n        )\n        DashboardQuickAction(\n            title = "Categorias",\n            description = "Organizar grupos",\n            icon = Icons.Default.Category,\n            onClick = onOpenCategories,\n            modifier = Modifier.weight(1f)\n        )\n    }''',
'''    NrdTwoActionLayout(\n        stackOnCompact = true,\n        first = { m -> DashboardQuickAction("Produtos", "Gerenciar catálogo", Icons.Default.Inventory, onOpenCatalog, modifier = m) },\n        second = { m -> DashboardQuickAction("Categorias", "Organizar grupos", Icons.Default.Category, onOpenCategories, modifier = m) }\n    )'''),
('''    Row(\n        modifier = Modifier.fillMaxWidth(),\n        horizontalArrangement = Arrangement.spacedBy(6.dp)\n    ) {\n        DashboardQuickAction(\n            title = "Abas",\n            description = "Organizar conteúdo",\n            icon = Icons.Default.ViewCarousel,\n            onClick = onManageTabs,\n            modifier = Modifier.weight(1f)\n        )\n        DashboardQuickAction(\n            title = "Importar",\n            description = if (importEnabled) "CSV ou TSV" else "Aguarde...",\n            icon = Icons.Default.UploadFile,\n            onClick = onImportProducts,\n            enabled = importEnabled,\n            modifier = Modifier.weight(1f)\n        )\n    }''',
'''    NrdTwoActionLayout(\n        stackOnCompact = true,\n        first = { m -> DashboardQuickAction("Abas", "Organizar conteúdo", Icons.Default.ViewCarousel, onManageTabs, modifier = m) },\n        second = { m -> DashboardQuickAction("Importar", if (importEnabled) "CSV ou TSV" else "Aguarde...", Icons.Default.UploadFile, onImportProducts, enabled = importEnabled, modifier = m) }\n    )''')]
for old, new in blocks:
    if old not in s:
        raise SystemExit('Dashboard block not found')
    s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# 4) Scanner: taxa de analise adaptativa por capacidade inicial + latencia real do ML Kit.
p = Path('app/src/main/java/com/example/ui/AcpBarcodeScanner.kt')
s = p.read_text(encoding='utf-8')
if 'import android.app.ActivityManager' not in s:
    s = s.replace('import android.Manifest\n', 'import android.Manifest\nimport android.app.ActivityManager\nimport android.os.SystemClock\n', 1)
if 'import java.util.concurrent.atomic.AtomicLong' not in s:
    s = s.replace('import java.util.concurrent.atomic.AtomicBoolean\n', 'import java.util.concurrent.atomic.AtomicBoolean\nimport java.util.concurrent.atomic.AtomicLong\n', 1)
anchor = '        val processing = AtomicBoolean(false)\n'
if 'adaptiveAverageMs' not in s:
    adaptive = '''        val activityManager = context.getSystemService(ActivityManager::class.java)\n        val memoryClassMb = activityManager?.memoryClass ?: 128\n        val cpuCount = Runtime.getRuntime().availableProcessors()\n        val initialAverageMs = when {\n            cpuCount >= 8 && memoryClassMb >= 256 -> 28L\n            cpuCount >= 6 && memoryClassMb >= 192 -> 45L\n            else -> 75L\n        }\n        val adaptiveAverageMs = AtomicLong(initialAverageMs)\n        val lastAnalysisStartMs = AtomicLong(0L)\n'''
    s = s.replace(anchor, anchor + adaptive, 1)
old = '''        analysis.setAnalyzer(executor) { proxy ->\n            if (disposed.get() || !processing.compareAndSet(false, true)) {\n                proxy.close()\n            } else {\n                val media = proxy.image\n'''
new = '''        analysis.setAnalyzer(executor) { proxy ->\n            val now = SystemClock.elapsedRealtime()\n            val averageMs = adaptiveAverageMs.get()\n            val minimumGapMs = when {\n                averageMs <= 35L -> 0L\n                averageMs <= 55L -> 12L\n                averageMs <= 85L -> 24L\n                averageMs <= 120L -> 38L\n                else -> 55L\n            }\n            val tooSoon = now - lastAnalysisStartMs.get() < minimumGapMs\n            if (disposed.get() || tooSoon || !processing.compareAndSet(false, true)) {\n                proxy.close()\n            } else {\n                lastAnalysisStartMs.set(now)\n                val analysisStartedAt = now\n                val media = proxy.image\n'''
if old not in s:
    raise SystemExit('Scanner analyzer anchor not found')
s = s.replace(old, new, 1)
old2 = '''                        .addOnCompleteListener(main) {\n                            proxy.close()\n                            processing.set(false)\n                            if (disposed.get()) closeScanner()\n                        }\n'''
new2 = '''                        .addOnCompleteListener(main) {\n                            val elapsed = (SystemClock.elapsedRealtime() - analysisStartedAt).coerceAtLeast(1L)\n                            val previous = adaptiveAverageMs.get()\n                            adaptiveAverageMs.set(((previous * 3L) + elapsed) / 4L)\n                            proxy.close()\n                            processing.set(false)\n                            if (disposed.get()) closeScanner()\n                        }\n'''
if old2 not in s:
    raise SystemExit('Scanner complete anchor not found')
s = s.replace(old2, new2, 1)
p.write_text(s, encoding='utf-8')
