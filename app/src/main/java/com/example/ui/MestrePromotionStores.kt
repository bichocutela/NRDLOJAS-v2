package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.StoreCatalog
import com.example.data.flyer.FlyerAnalysisResult
import com.example.data.flyer.FlyerImportEngine
import com.example.data.promotions.PromotionStores
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MestrePromotionStores() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stores by remember { PromotionStores.observe() }.collectAsState(initial = PromotionStores.defaults)
    var selectedStore by remember { mutableStateOf("0012") }
    var analysis by remember { mutableStateOf<FlyerAnalysisResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try {
                analysis = FlyerImportEngine.analyzeVisualMixUri(context, uri)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "Não foi possível ler o documento." }
            finally { busy = false }
        }
    }

    Text("Habilitar lojas", style = MaterialTheme.typography.titleLarge)
    Text("Envie o mesmo Relatório de Produtos Alterados do Visual Mix usado em Consultar Preços. Confira a loja, os preços e as validades antes de publicar.")
    Text("Cidade Jardim atualiza os preços pelo ACP. O documento complementa as validades. Nas outras lojas, as ofertas De/Por vêm do documento publicado.", style = MaterialTheme.typography.bodySmall)
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    stores.forEach { store ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(StoreCatalog.labelFor(store.code), modifier = Modifier.weight(1f))
                    Switch(checked = store.enabled, enabled = !busy && (store.code == "0012" || store.count > 0),
                        onCheckedChange = { enabled -> scope.launch {
                            busy = true
                            try { PromotionStores.setEnabled(store.code, enabled); message = "Loja atualizada." }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { message = failure.message }
                            finally { busy = false }
                        } })
                }
                Text(if (store.source.isBlank()) "Nenhum documento publicado" else "${store.source} • ${store.count} ofertas De/Por",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !busy, onClick = {
                    selectedStore = store.code
                    analysis = null
                    picker.launch(arrayOf("application/pdf", "text/plain", "image/*"))
                }) { Text("Enviar documento Visual Mix") }
            }
        }
    }
    analysis?.let { result ->
        val records = remember(result) { PromotionStores.records(result) }
        AlertDialog(onDismissRequest = { if (!busy) analysis = null },
            title = { Text("Publicar em ${StoreCatalog.nameFor(selectedStore)}?") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item { Column {
                    Text("${records.size} ofertas De/Por. ${result.offers.size - records.size} linhas de outras regras ou inválidas não serão publicadas.")
                    result.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    } }
                    items(records) { record -> Column {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("${record.code.ifBlank { record.barcode }} • ${record.name}")
                        Text("De R$ %.2f por R$ %.2f".format(java.util.Locale("pt", "BR"), record.previous, record.price))
                        Text("${record.from ?: "Início não informado"} até ${record.to ?: "Fim não informado"}", style = MaterialTheme.typography.bodySmall)
                    } }
                }
            },
            confirmButton = { TextButton(enabled = !busy && records.isNotEmpty(), onClick = { scope.launch {
                busy = true
                try {
                    PromotionStores.publish(selectedStore, result)
                    analysis = null
                    message = "Documento publicado e loja habilitada. As ofertas serão atualizadas automaticamente."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { message = failure.message ?: "Não foi possível publicar." }
                finally { busy = false }
            } }) { Text("Publicar e habilitar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { analysis = null }) { Text("Cancelar") } })
    }
}
