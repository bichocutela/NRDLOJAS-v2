package com.example.ui

import androidx.compose.foundation.layout.*
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
    val scope = rememberCoroutineScope()
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
    }) { Text(if (busy) "Criando..." else "Criar cadastro") }
    message?.let { Text(it) }
}
