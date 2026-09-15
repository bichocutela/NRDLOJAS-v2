from pathlib import Path

path = Path('app/src/main/java/com/example/ui/AcpProductsPanel.kt')
text = path.read_text(encoding='utf-8')

def replace_once(old, new, label):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: esperado 1 trecho, encontrado {count}')
    text = text.replace(old, new, 1)

replace_once(
    'import androidx.compose.material3.*\n',
    'import androidx.compose.material3.*\nimport androidx.compose.material3.pulltorefresh.PullToRefreshBox\n',
    'import PullToRefreshBox'
)

replace_once(
'''    var busy by remember { mutableStateOf(false) }\n    var error by remember { mutableStateOf<String?>(null) }\n''',
'''    var busy by remember { mutableStateOf(false) }\n    var refreshing by remember { mutableStateOf(false) }\n    var refreshMessage by remember { mutableStateOf<String?>(null) }\n    var error by remember { mutableStateOf<String?>(null) }\n''',
    'estado de refresh'
)

anchor = '''    fun search(index: Int = 0, interactive: Boolean = true, enrichCampaigns: Boolean = interactive) {\n'''
pos = text.find(anchor)
if pos < 0:
    raise SystemExit('função search não encontrada')
# Insert refresh function immediately before the suggestions LaunchedEffect, after search() closes.
marker = '''    // Comportamento do editor web: enquanto o nome é digitado, os candidatos aparecem abaixo.\n'''
refresh_fun = '''    fun refreshFromAcp() {\n        if (refreshing) return\n        searchJob?.cancel()\n        val ticket = ++generation\n        refreshing = true\n        error = null\n        refreshMessage = null\n        closeDetail()\n        searchJob = scope.launch {\n            try {\n                // Confirma/reutiliza a sessão existente. Se ela expirou, AcpApi renova sem\n                // afetar o login do NRD e sem transformar o gesto em logout.\n                api.confirmAccess()\n                val clean = query.trim()\n                if (clean.isBlank()) {\n                    // Sem uma pesquisa aberta não há uma lista de preços para substituir.\n                    // Ainda assim validamos a sessão e a integração com o ACP.\n                    integration = try { api.integrationInfo() } catch (_: Exception) { null }\n                    refreshMessage = \"Conexão com o ACP atualizada. Pesquise um produto para carregar o preço mais recente.\"\n                } else {\n                    val targetPage = page?.pageIndex ?: 0\n                    val fresh = api.searchProductsUnifiedFresh(clean, targetPage, freshStore)\n                    if (ticket != generation) return@launch\n                    page = fresh\n                    lastExplicitQuery = clean\n                    previewCampaignOffers = try {\n                        api.campaignOffersFor(fresh.items)\n                    } catch (cancelled: CancellationException) {\n                        throw cancelled\n                    } catch (_: Exception) {\n                        emptyMap()\n                    }\n                    refreshMessage = \"Preços atualizados agora pelo ACP.\"\n                }\n            } catch (cancelled: CancellationException) {\n                throw cancelled\n            } catch (failure: Exception) {\n                if (ticket == generation) {\n                    // Mantém a tela autenticada. O gesto nunca expulsa o usuário da conta.\n                    error = acpErrorMessage(failure)\n                }\n            } finally {\n                if (ticket == generation) refreshing = false\n            }\n        }\n    }\n\n'''
replace_once(marker, refresh_fun + marker, 'função refreshFromAcp')

replace_once(
'''                    lastExplicitQuery = null\n                    error = null\n''',
'''                    lastExplicitQuery = null\n                    refreshMessage = null\n                    error = null\n''',
    'limpeza de mensagem ao digitar'
)

replace_once(
'''    val result = page\n    LazyColumn(\n        modifier = Modifier.fillMaxSize(),\n''',
'''    val result = page\n    PullToRefreshBox(\n        isRefreshing = refreshing,\n        onRefresh = { refreshFromAcp() },\n        modifier = Modifier.fillMaxSize()\n    ) {\n    LazyColumn(\n        modifier = Modifier.fillMaxSize(),\n''',
    'envolve lista com pull refresh'
)

replace_once(
'''                    TextButton(onClick = { search(result.pageIndex) }, enabled = !busy) { Text(\"Atualizar\") }\n''',
'''                    TextButton(onClick = { refreshFromAcp() }, enabled = !busy && !refreshing) { Text(\"Atualizar\") }\n''',
    'botão atualizar usa consulta fresca'
)

replace_once(
'''        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }\n        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }\n''',
'''        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }\n        refreshMessage?.let { message ->\n            item { Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }\n        }\n        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }\n''',
    'mensagem de refresh'
)

replace_once(
'''        }\n    }\n\n    if (scanning) {\n''',
'''        }\n    }\n    }\n\n    if (scanning) {\n''',
    'fecha PullToRefreshBox'
)

append_marker = '''private suspend fun AcpApi.refreshProductFreshForUi(selected: AcpProduct, store: AcpSecureStore): AcpProduct {\n'''
fresh_search = '''private suspend fun AcpApi.searchProductsUnifiedFresh(\n    query: String,\n    pageIndex: Int,\n    store: AcpSecureStore\n): AcpProductPage {\n    val clean = query.trim()\n    require(clean.isNotBlank() && clean.length <= 200 && pageIndex >= 0)\n    val numeric = clean.all(Char::isDigit)\n    val preferred = when {\n        !numeric -> AcpSearchField.DESCRIPTION\n        clean.length in setOf(8, 12, 13, 14) -> AcpSearchField.BARCODE\n        else -> AcpSearchField.CODE\n    }\n\n    fun clear(field: AcpSearchField, page: Int) {\n        val parameters = listOf(\n            \"pageSize\" to \"20\",\n            \"pageIndex\" to page.toString(),\n            field.parameter to clean\n        )\n        store.clear(acpResponseCacheName(\"Product/all\", parameters))\n    }\n\n    // Product/all normalmente usa cache diário. O gesto explícito de atualizar é a exceção:\n    // limpamos somente as chaves da pesquisa visível, sem varrer nem apagar o restante do cache.\n    clear(preferred, pageIndex)\n    if (numeric && pageIndex == 0) {\n        val alternate = if (preferred == AcpSearchField.BARCODE) AcpSearchField.CODE else AcpSearchField.BARCODE\n        clear(alternate, 0)\n    }\n    return searchProductsUnified(clean, pageIndex)\n}\n\n'''
replace_once(append_marker, fresh_search + append_marker, 'busca fresca')

path.write_text(text, encoding='utf-8')
print('Pull-to-refresh ACP aplicado.')
