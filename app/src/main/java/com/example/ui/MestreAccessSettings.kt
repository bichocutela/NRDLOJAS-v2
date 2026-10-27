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
import com.example.data.PromotionControlClient
import com.example.data.acp.AcpGatewayClient
import kotlinx.coroutines.launch

@Composable
internal fun MestreAccessSettings() {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(false) }
    var promotions by remember { mutableStateOf(false) }
    var flyers by remember { mutableStateOf(false) }
    var prices by remember { mutableStateOf(false) }
    var stores by remember { mutableStateOf(false) }
    var audience by remember { mutableStateOf(com.example.data.TabAudience()) }
    var audienceLoading by remember { mutableStateOf(true) }
    var audienceLoadFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expandedUid by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<AccessAccount?>(null) }
    var deleting by remember { mutableStateOf<AccessAccount?>(null) }
    val history by remember { RestrictedAccessRepository.observeAccounts() }.collectAsState(initial = AccessHistory())
    val scope = rememberCoroutineScope()
    val access by remember { RestrictedAccessRepository.observe() }
        .collectAsState(initial = com.example.data.RestrictedAccess())
    LaunchedEffect(Unit) {
        try { audience = RestrictedAccessRepository.tabAudience() }
        catch (_: Exception) { audienceLoadFailed = true; message = "Não foi possível carregar o público atual das abas. Atualize a tela antes de alterar." }
        finally { audienceLoading = false }
    }
    Text("Acesso às abas", style = MaterialTheme.typography.titleLarge)
    Text("Escolha o público de cada aba. VIP significa somente o Mestre e os logins cadastrados com aquela permissão.")
    listOf("Meu Perfil" to "profile", "Promoções" to "promotions", "Encartes" to "flyers", "Consultar Preços" to "prices", "Nossas Lojas" to "stores").forEach { (label, key) ->
        val selected = when(key) { "profile" -> audience.profile; "promotions" -> audience.promotions; "flyers" -> audience.flyers; "stores" -> audience.stores; else -> audience.prices }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            listOf("all" to "Todos", "vip" to "VIP").forEach { (value, title) ->
                FilterChip(selected == value, onClick = {
                    val previous = audience
                    val next = when(key) { "profile" -> audience.copy(profile=value); "promotions" -> audience.copy(promotions=value); "flyers" -> audience.copy(flyers=value); "stores" -> audience.copy(stores=value); else -> audience.copy(prices=value) }
                    audience = next; busy = true
                    scope.launch { try { RestrictedAccessRepository.setTabAudience(next); message = "Acesso de $label atualizado." } catch (_: Exception) { audience = previous; message = "Não foi possível salvar o público de $label." } finally { busy = false } }
                }, enabled = !busy && !audienceLoading && !audienceLoadFailed) { Text(title) }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
    Text("Criar cadastro", style = MaterialTheme.typography.titleLarge)
    Text("O usuário entrará pelo Login do menu e verá somente as abas selecionadas.")
    OutlinedTextField(login, { login = it }, label = { Text("Login") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(password, { password = it }, label = { Text("Senha (mínimo 8 caracteres)") }, singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    Row { Checkbox(profile, { profile = it }, enabled = !busy); Text("Meu Perfil", modifier = Modifier.padding(top = 12.dp)) }
    Row { Checkbox(promotions, { promotions = it }, enabled = !busy); Text("Promoções", modifier = Modifier.padding(top = 12.dp)) }
    Row { Checkbox(flyers, { flyers = it }, enabled = !busy); Text("Encartes", modifier = Modifier.padding(top = 12.dp)) }
    Row { Checkbox(prices, { prices = it }, enabled = !busy); Text("Consultar Preços", modifier = Modifier.padding(top = 12.dp)) }
    Row { Checkbox(stores, { stores = it }, enabled = !busy); Text("Nossas Lojas", modifier = Modifier.padding(top = 12.dp)) }
    Button(enabled = !busy, onClick = {
        busy = true; message = null
        scope.launch {
            try {
                RestrictedAccessRepository.create(login, password, profile, promotions, flyers, prices, stores)
                login = ""; password = ""; profile = false; promotions = false; flyers = false; prices = false; stores = false
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
                    onSave = { selectedProfile, selectedPromotions, selectedFlyers, selectedPrices, selectedStores ->
                        busy = true; message = null
                        scope.launch {
                            try {
                                RestrictedAccessRepository.updatePermissions(account.uid, selectedProfile, selectedPromotions, selectedFlyers, selectedPrices, selectedStores)
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
    AcpProviderSettings()
}

@Composable
private fun AcpProviderSettings() {
    var legacyEnabled by remember { mutableStateOf(false) }
    var acpTechEnabled by remember { mutableStateOf(true) }
    var primary by remember { mutableStateOf("acptech") }
    var draftLegacy by remember { mutableStateOf(false) }
    var draftAcpTech by remember { mutableStateOf(true) }
    var draftPrimary by remember { mutableStateOf("acptech") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val gateway = remember { AcpGatewayClient() }

    LaunchedEffect(Unit) {
        try {
            val config = gateway.providerStatus()
            legacyEnabled = config.optBoolean("legacyEnabled", false)
            acpTechEnabled = config.optBoolean("acpTechEnabled", false)
            primary = config.optString("primary").takeIf { it in setOf("legacy", "acptech") }
                ?: if (acpTechEnabled) "acptech" else "legacy"
            draftLegacy = legacyEnabled
            draftAcpTech = acpTechEnabled
            draftPrimary = primary
        } catch (_: Exception) {
            loadError = "Não foi possível carregar a configuração dos provedores. Tente novamente."
        } finally { loading = false }
    }

    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    Text("Provedores de preço ACP", style = MaterialTheme.typography.titleLarge)
    Text("Ative um provedor ou os dois. Com os dois ativos, o secundário só será consultado se o principal falhar; os resultados nunca são misturados.")
    if (loading) Text("Carregando configuração dos provedores...")
    if (loadError != null) Text(loadError!!, color = MaterialTheme.colorScheme.error)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(checked = draftLegacy, enabled = !loading && !saving && loadError == null,
            onCheckedChange = { enabled -> draftLegacy = enabled; if (enabled && draftPrimary !in setOf("legacy", "acptech")) draftPrimary = "legacy" })
        Text("ACP antigo", modifier = Modifier.padding(start = 8.dp))
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(checked = draftAcpTech, enabled = !loading && !saving && loadError == null,
            onCheckedChange = { enabled -> draftAcpTech = enabled; if (enabled && draftPrimary !in setOf("legacy", "acptech")) draftPrimary = "acptech" })
        Text("ACP Tech", modifier = Modifier.padding(start = 8.dp))
    }
    if (draftLegacy && draftAcpTech) {
        Text("Provedor principal", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = draftPrimary == "acptech", enabled = !loading && !saving && loadError == null,
                onClick = { draftPrimary = "acptech" })
            Text("ACP Tech", modifier = Modifier.clickable(enabled = !loading && !saving && loadError == null) { draftPrimary = "acptech" })
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = draftPrimary == "legacy", enabled = !loading && !saving && loadError == null,
                onClick = { draftPrimary = "legacy" })
            Text("ACP antigo", modifier = Modifier.clickable(enabled = !loading && !saving && loadError == null) { draftPrimary = "legacy" })
        }
    }
    val noProviderSelected = !draftLegacy && !draftAcpTech
    if (noProviderSelected) Text("Mantenha pelo menos um provedor ativo.", color = MaterialTheme.colorScheme.error)
    Button(enabled = !loading && !saving && loadError == null && !noProviderSelected &&
        (draftLegacy != legacyEnabled || draftAcpTech != acpTechEnabled || draftPrimary != primary),
        onClick = {
            saving = true
            message = null
            scope.launch {
                try {
                    val selectedPrimary = if (draftPrimary == "legacy" && draftLegacy || draftPrimary == "acptech" && draftAcpTech) draftPrimary
                        else if (draftAcpTech) "acptech" else "legacy"
                    PromotionControlClient.write("config/acpProvider", mapOf(
                        "legacyEnabled" to draftLegacy,
                        "acpTechEnabled" to draftAcpTech,
                        "primary" to selectedPrimary
                    ), merge = true)
                    legacyEnabled = draftLegacy
                    acpTechEnabled = draftAcpTech
                    primary = selectedPrimary
                    draftPrimary = selectedPrimary
                    message = "Provedores atualizados."
                } catch (_: Exception) {
                    message = "Não foi possível salvar. A configuração anterior continua ativa."
                } finally { saving = false }
            }
        }) { Text(if (saving) "Salvando..." else "Salvar provedores") }
    message?.let { Text(it) }
}

@Composable
private fun AccountHistoryCard(account: AccessAccount, expanded: Boolean, busy: Boolean,
    onToggle: () -> Unit, onSave: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(account.login, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (expanded) "Fechar ⌃" else "Editar ⌄", color = MaterialTheme.colorScheme.primary)
            }
            val tabs = listOfNotNull(if (account.profile) "Meu Perfil" else null,
                if (account.promotions) "Promoções" else null, if (account.flyers) "Encartes" else null, if (account.prices) "Consultar Preços" else null,
                if (account.stores) "Nossas Lojas" else null)
            Text(if (!account.enabled || tabs.isEmpty()) "Sem abas liberadas" else tabs.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (expanded) {
                var profile by remember(account.uid) { mutableStateOf(account.profile) }
                var promotions by remember(account.uid) { mutableStateOf(account.promotions) }
                var flyers by remember(account.uid) { mutableStateOf(account.flyers) }
                var prices by remember(account.uid) { mutableStateOf(account.prices) }
                var stores by remember(account.uid) { mutableStateOf(account.stores) }
                Spacer(Modifier.height(8.dp))
                Row { Checkbox(profile, { profile = it }, enabled = !busy); Text("Meu Perfil", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(promotions, { promotions = it }, enabled = !busy); Text("Promoções", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(flyers, { flyers = it }, enabled = !busy); Text("Encartes", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(prices, { prices = it }, enabled = !busy); Text("Consultar Preços", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(stores, { stores = it }, enabled = !busy); Text("Nossas Lojas", Modifier.padding(top = 12.dp)) }
                Button(enabled = !busy, onClick = { onSave(profile, promotions, flyers, prices, stores) }) { Text("Salvar permissões") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = onDelete) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    TextButton(enabled = !busy, onClick = onEdit) { Text("Alterar dados") }
                }
            }
        }
    }
}
