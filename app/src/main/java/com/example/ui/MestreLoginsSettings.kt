package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.IntegrationLoginStatus
import com.example.data.IntegrationLoginsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun MestreLoginsSettings() {
    val scope = rememberCoroutineScope()
    var statuses by remember { mutableStateOf<List<IntegrationLoginStatus>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    fun runJob(job: suspend () -> Unit) {
        scope.launch {
            busy = true; message = ""; failed = false
            try { job() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                failed = true
                message = if (error is IllegalStateException || error is IllegalArgumentException) error.message.orEmpty()
                    else "Não foi possível confirmar a operação. Atualize a lista antes de tentar novamente."
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { runJob { statuses = IntegrationLoginsRepository.list() } }
    Text("Logins", style = MaterialTheme.typography.titleLarge)
    Text("Gerencie os acessos usados pelo NRD. A senha salva não é exibida e fica protegida no servidor.")
    Text("Ao trocar um acesso, o NRD testa os novos dados antes de salvar. As próximas conexões reconhecem a mudança em até 15 segundos.",
        style = MaterialTheme.typography.bodySmall)
    TextButton(enabled = !busy, onClick = { runJob { statuses = IntegrationLoginsRepository.list(); message = "Lista atualizada." } }) {
        Text("Atualizar lista")
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    statuses.forEach { status ->
        key(status.provider) {
            var editing by remember { mutableStateOf(false) }
            var login by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            val title = when (status.provider) { "offers" -> "Ofertas"; "legacy" -> "ACP"; else -> "ACP Tech" }
            OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(when (status.provider) {
                        "offers" -> "Acesso de reserva às ofertas do Nordestão Pra Você."
                        "legacy" -> "Acesso ao ACP antigo."
                        else -> "Acesso ao ACP Tech."
                    })
                    Text(if (status.configured) "Acesso configurado" else "Acesso não configurado")
                    status.updatedAt?.let { value ->
                        val date = runCatching { Instant.parse(value).atZone(ZoneId.of("America/Fortaleza"))
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) }.getOrNull()
                        if (date != null) Text("Atualizado em $date", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(enabled = !busy && status.configured, onClick = { runJob {
                        IntegrationLoginsRepository.test(status.provider)
                        message = "$title: conexão validada."
                    } }) { Text("Testar acesso atual") }
                    TextButton(enabled = !busy, onClick = { editing = !editing; login = ""; password = "" }) {
                        Text(if (editing) "Cancelar alteração" else "Alterar acesso")
                    }
                    if (editing) {
                        OutlinedTextField(login, { login = it.take(256) }, label = { Text("Novo login") },
                            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(password, { password = it.take(512) }, label = { Text("Nova senha") },
                            singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                        Button(enabled = !busy && login.isNotBlank() && password.isNotEmpty(), onClick = { runJob {
                            IntegrationLoginsRepository.save(status.provider, login, password, status.revision)
                            login = ""; password = ""; editing = false
                            message = "$title: novo acesso validado e salvo. Não precisa de outro APK."
                            statuses = IntegrationLoginsRepository.list()
                        } }) { Text("Testar e salvar") }
                    }
                }
            }
        }
    }
}
