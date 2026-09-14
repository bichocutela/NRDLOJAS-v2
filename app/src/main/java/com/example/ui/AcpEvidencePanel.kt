package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.acp.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Separate, opt-in master diagnostic. It never adds offers to consultation results. */
@Composable
internal fun AcpEvidencePanel(api: AcpApi, store: AcpStorage, exportBusy: Boolean,
    onExport: (String, Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val scanner = remember(api, store) { AcpEvidenceScanner(api::readEvidencePage, store) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf("2012568001") }
    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<AcpEvidenceProgress?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val valid = target.isNotBlank()

    LaunchedEffect(target) {
        progress = if (valid) scanner.progress(target) else null
        message = null
    }

    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Fechar investigação de cartazes" else "Investigar cartazes por código/EAN")
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Procura o produto nos grupos e cartazes impressos. Não confirma oferta vigente. Mantenha esta tela aberta durante a coleta.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = target, onValueChange = { target = it.filter { character -> character in '0'..'9' }.take(20) },
            label = { Text("Código interno ou EAN exato") }, enabled = !running && !exportBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(enabled = valid && !running && !exportBusy && progress?.finished != true,
            modifier = Modifier.fillMaxWidth(), onClick = {
                val requested = target
                running = true
                message = null
                job = scope.launch {
                    try {
                        scanner.scan(requested) { update -> progress = update }
                        message = "Varredura finalizada. Salve os resultados para análise."
                    } catch (cancelled: CancellationException) {
                        message = "Coleta pausada. Continuar retoma da última página salva."
                        throw cancelled
                    } catch (failure: AcpFailure) { message = failure.message }
                    catch (_: AcpUnauthorized) { message = "A sessão ACP precisa ser confirmada novamente. O progresso foi preservado." }
                    catch (_: Exception) { message = "A coleta foi interrompida. Você pode continuar ou salvar o resultado parcial." }
                    finally { running = false }
                }
            }) { Text(if (running) "Investigando…" else if (progress == null) "Iniciar investigação" else "Continuar investigação") }
        if (running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { job?.cancel(); message = "Pausando após a consulta em andamento…" }) { Text("Pausar") }
        }
        progress?.let { p ->
            val phase = when (p.phase) { "groups" -> "Grupos"; "history" -> "Histórico"; else -> "Varredura finalizada" }
            Text("$phase • ${p.pages} páginas • ${p.records} registros • ${p.matches} ocorrências",
                style = MaterialTheme.typography.bodySmall)
            if (p.unparsed > 0) Text("${p.unparsed} registros não puderam ser interpretados. O resultado é parcial.")
            if (!p.groupsAvailable) Text("Grupos indisponíveis nesta coleta; o histórico foi tratado separadamente.")
            if (p.historyChanged) Text("O histórico mudou durante a coleta. Ela não representa uma fotografia completa do momento atual.")
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(enabled = valid && progress != null && !running && !exportBusy,
            modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    try {
                        val report = scanner.report(target)
                        if (report == null) message = "Ainda não há páginas salvas. Inicie a investigação."
                        else onExport(report, -1)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Não foi possível preparar o resultado. Tente novamente." }
                }
            }) { Text("Salvar resultados da investigação") }
    }
}
