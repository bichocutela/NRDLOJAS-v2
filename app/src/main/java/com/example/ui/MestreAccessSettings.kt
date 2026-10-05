package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import com.example.data.AccessAccount
import com.example.data.AccessHistory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.RestrictedAccessRepository
import kotlinx.coroutines.launch

@Composable
internal fun MestreAccessSettings() {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(false) }
    var promotions by remember { mutableStateOf(false) }
    var prices by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expandedUid by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<AccessAccount?>(null) }
    var deleting by remember { mutableStateOf<AccessAccount?>(null) }
    val history by remember { RestrictedAccessRepository.observeAccounts() }.collectAsState(initial = AccessHistory())
    val scope = rememberCoroutineScope()
    val access by remember { RestrictedAccessRepository.observe() }
        .collectAsState(initial = com.example.data.RestrictedAccess())
    Text("Acesso às três abas", style = MaterialTheme.typography.titleLarge)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(checked = access.publicAccess, enabled = !busy && !access.publicLoading, onCheckedChange = { enabled ->
            busy = true; message = null
            scope.launch {
                try {
                    RestrictedAccessRepository.setPublicAccess(enabled)
                    message = if (enabled) "As três abas estão liberadas para todos." else "As abas seguem as permissões de cada login."
                } catch (_: Exception) { message = "Não foi possível salvar. Verifique a conexão." }
                finally { busy = false }
            }
        })
        Text("Liberar as três abas para todos", modifier = Modifier.padding(start = 8.dp))
    }
    Text("Desligado: acesso apenas do Mestre e dos logins autorizados. Os cadastros continuam salvos ao mudar esta opção.")
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
    Text("Criar cadastro", style = MaterialTheme.typography.titleLarge)
    Text("O usuário entrará pelo Login do menu e verá somente as abas selecionadas.")
    OutlinedTextField(login, { login = it }, label = { Text("Login") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(password, { password = it }, label = { Text("Senha (mínimo 8 caracteres)") }, singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    Row { Checkbox(profile, { profile = it }, enabled = !busy); Text("Meu Perfil", modifier = Modifier.padding(top = 12.dp)) }
    Row { Checkbox(promotions, { promotions = it }, enabled = !busy); Text("Promoções", modifier = Modifier.padding(top = 12.dp)) }
    Row { Checkbox(prices, { prices = it }, enabled = !busy); Text("Consultar Preços", modifier = Modifier.padding(top = 12.dp)) }
    Button(enabled = !busy, onClick = {
        busy = true; message = null
        scope.launch {
            try {
                RestrictedAccessRepository.create(login, password, profile, promotions, prices)
                login = ""; password = ""; profile = false; promotions = false; prices = false
                message = "Cadastro criado. Informe o login e a senha ao usuário."
            } catch (error: Exception) {
                message = if (error is IllegalArgumentException || error is IllegalStateException) error.message else "Não foi possível criar. Verifique a conexão ou se o login já existe."
            } finally { busy = false }
        }
    }) { Text(if (busy) "Aguarde..." else "Criar cadastro") }
    message?.let { Text(it) }
    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    Text("Histórico de cadastros", style = MaterialTheme.typography.titleLarge)
    Text("Toque em um login para editar as abas, alterar os dados ou excluir o cadastro.")
    when {
        history.loading -> Text("Carregando cadastros...")
        history.error != null -> Text(history.error!!, color = MaterialTheme.colorScheme.error)
        history.accounts.isEmpty() -> Text("Nenhum cadastro criado ainda.")
        else -> history.accounts.forEach { account ->
            key(account.uid) {
                AccountHistoryCard(account, expandedUid == account.uid, busy,
                    onToggle = { expandedUid = if (expandedUid == account.uid) null else account.uid },
                    onSave = { selectedProfile, selectedPromotions, selectedPrices ->
                        busy = true; message = null
                        scope.launch {
                            try {
                                RestrictedAccessRepository.updatePermissions(account.uid, selectedProfile, selectedPromotions, selectedPrices)
                                expandedUid = null
                                message = "Permissões de ${account.login} atualizadas."
                            } catch (_: Exception) { message = "Não foi possível salvar as permissões. Tente novamente." }
                            finally { busy = false }
                        }
                    }, onDelete = { deleting = account }, onEdit = { editing = account })
            }
        }
    }
    editing?.let { account ->
        var editedLogin by remember(account.uid) { mutableStateOf(account.login) }
        var editedPassword by remember(account.uid) { mutableStateOf("") }
        var editError by remember(account.uid) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!busy) editing = null },
            title = { Text("Alterar dados") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editedLogin, { editedLogin = it; editError = null }, label = { Text("Login") },
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editedPassword, { editedPassword = it; editError = null }, label = { Text("Nova senha") },
                        supportingText = { Text("Deixe em branco para manter a senha atual. Mínimo 8 caracteres.") },
                        singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; editError = null
                scope.launch {
                    try {
                        RestrictedAccessRepository.updateAccount(account.uid, editedLogin, editedPassword)
                        editing = null; message = "Dados do cadastro atualizados."
                    } catch (error: Exception) {
                        editError = if (error is IllegalArgumentException || error is IllegalStateException) error.message
                            else "Não foi possível alterar os dados. Tente novamente."
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Salvando..." else "Salvar dados") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { editing = null }) { Text("Cancelar") } })
    }
    deleting?.let { account ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null },
            title = { Text("Excluir ${account.login}?") },
            text = { Text("O login e suas permissões serão excluídos. Para voltar a acessar, será necessário criar um novo cadastro.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; message = null
                scope.launch {
                    try {
                        RestrictedAccessRepository.deleteAccount(account.uid)
                        expandedUid = null; deleting = null
                        message = "Login excluído."
                    } catch (error: Exception) {
                        deleting = null
                        message = if (error is IllegalStateException) error.message else "Não foi possível excluir. Tente novamente."
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Excluindo..." else "Excluir") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun AccountHistoryCard(account: AccessAccount, expanded: Boolean, busy: Boolean,
    onToggle: () -> Unit, onSave: (Boolean, Boolean, Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(account.login, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (expanded) "Fechar ⌃" else "Editar ⌄", color = MaterialTheme.colorScheme.primary)
            }
            val tabs = listOfNotNull(if (account.profile) "Meu Perfil" else null,
                if (account.promotions) "Promoções" else null, if (account.prices) "Consultar Preços" else null)
            Text(if (!account.enabled || tabs.isEmpty()) "Sem abas liberadas" else tabs.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (expanded) {
                var profile by remember(account.uid) { mutableStateOf(account.profile) }
                var promotions by remember(account.uid) { mutableStateOf(account.promotions) }
                var prices by remember(account.uid) { mutableStateOf(account.prices) }
                Spacer(Modifier.height(8.dp))
                Row { Checkbox(profile, { profile = it }, enabled = !busy); Text("Meu Perfil", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(promotions, { promotions = it }, enabled = !busy); Text("Promoções", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(prices, { prices = it }, enabled = !busy); Text("Consultar Preços", Modifier.padding(top = 12.dp)) }
                Button(enabled = !busy, onClick = { onSave(profile, promotions, prices) }) { Text("Salvar permissões") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = onDelete) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    TextButton(enabled = !busy, onClick = onEdit) { Text("Alterar dados") }
                }
            }
        }
    }
}
