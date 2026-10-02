package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.UpdatePolicyRepository
import com.example.util.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MestreUpdateSettings(onOpenLegacyNotice: () -> Unit) {
    val context = LocalContext.current
    val policy by remember { UpdatePolicyRepository.observe(context) }.collectAsState(initial = UpdatePolicyRepository.cached(context))
    var enabled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("v" + BuildConfig.VERSION_NAME.removePrefix("v")) }
    var versions by remember { mutableStateOf(emptyList<String>()) }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(policy) {
        enabled = policy.enabled
        if (policy.minimumVersion.isNotBlank()) selected = policy.minimumVersion
    }
    LaunchedEffect(refresh) {
        loading = true
        versions = UpdateChecker.publishedVersionTags()
        if (selected !in versions && versions.isNotEmpty() && policy.minimumVersion.isBlank()) selected = versions.first()
        loading = false
    }
    Text("Atualização do aplicativo", style = MaterialTheme.typography.titleLarge)
    Text("Escolha a versão mínima que poderá continuar usando o app.")
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(checked = enabled, enabled = !busy && policy.loaded, onCheckedChange = { enabled = it })
        Text("Exigir atualização", modifier = Modifier.padding(start = 8.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!loading && !busy && versions.isNotEmpty()) expanded = it }) {
        OutlinedTextField(value = selected, onValueChange = {}, readOnly = true,
            label = { Text(if (loading) "Carregando versões..." else "Versão mínima permitida") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(), enabled = !loading && !busy && versions.isNotEmpty())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            versions.forEach { version -> DropdownMenuItem(text = { Text(version) }, onClick = { selected = version; expanded = false }) }
        }
    }
    TextButton(enabled = !loading && !busy, onClick = { refresh++ }) { Text("Recarregar versões") }
    if (!loading && versions.isEmpty()) Text("Não foi possível carregar as versões. Verifique a conexão.")
    Text("Quem estiver abaixo da versão escolhida verá um aviso e poderá acessar apenas Sobre para atualizar. Versões anteriores a este controle precisam instalar esta atualização uma vez.", style = MaterialTheme.typography.bodySmall)
    Spacer(modifier = Modifier.height(12.dp))
    Button(enabled = policy.loaded && !busy && (!enabled || selected in versions), onClick = {
        busy = true; message = null
        scope.launch {
            try {
                UpdatePolicyRepository.save(enabled, selected)
                message = if (enabled) "Versão mínima salva. O aviso será mostrado aos aparelhos compatíveis com este controle." else "Atualização obrigatória desativada."
            } catch (error: Exception) {
                message = if (error is IllegalArgumentException || error is IllegalStateException) error.message else "Não foi possível salvar. Verifique a conexão."
            } finally { busy = false }
        }
    }) { Text(if (busy) "Salvando..." else "Salvar") }
    message?.let { Text(it) }
    TextButton(onClick = onOpenLegacyNotice) { Text("Avisar versões anteriores com Inserir Novidade") }
}
