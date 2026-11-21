package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

internal object HomePriceSearchAccess {
    private fun document() = FirebaseFirestore.getInstance().collection("config").document("home_price_search")
    val updates = callbackFlow<Boolean> {
        val listener = document().addSnapshotListener { value, error ->
            trySend(error == null && value?.getBoolean("enabled") == true)
        }
        awaitClose { listener.remove() }
    }
    fun userUpdates(uid: String) = callbackFlow<Boolean?> {
        val listener = document().addSnapshotListener { value, error ->
            trySend(if (error == null) (value?.get("users") as? Map<*, *>)?.get(uid) as? Boolean else false)
        }
        awaitClose { listener.remove() }
    }
    suspend fun saveUser(uid: String, enabled: Boolean) {
        document().set(mapOf("users" to mapOf(uid to enabled)), com.google.firebase.firestore.SetOptions.merge()).await()
    }
    suspend fun save(enabled: Boolean) {
        document().set(mapOf("enabled" to enabled), com.google.firebase.firestore.SetOptions.merge()).await()
    }
}

@Composable
internal fun HomePriceSearchAccessSetting() {
    val enabled by remember { HomePriceSearchAccess.updates }.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    OutlinedCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Consultar Preços na busca inicial", style = MaterialTheme.typography.titleMedium)
            Text("Libera a consulta no popup da tela inicial para usuários com acesso a Consultar Preços. Mestre e Admin têm acesso permanente.")
            Row {
                Switch(checked = enabled, enabled = !busy, onCheckedChange = { next ->
                    busy = true
                    scope.launch {
                        try { HomePriceSearchAccess.save(next); message = "Permissão atualizada." }
                        catch (_: Exception) { message = "Não foi possível salvar. Tente novamente." }
                        finally { busy = false }
                    }
                })
                Text(if (enabled) "Ativado para usuários" else "Desativado para usuários", Modifier.padding(12.dp))
            }
            message?.let { Text(it) }
        }
    }
}

@Composable
internal fun HomePriceSearchUserSetting(uid: String) {
    val saved by remember(uid) { HomePriceSearchAccess.userUpdates(uid) }.collectAsState(initial = null)
    var draft by remember(uid, saved) { mutableStateOf(saved == true) }
    var busy by remember(uid) { mutableStateOf(false) }
    var message by remember(uid) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Row {
        Checkbox(checked = draft, enabled = !busy, onCheckedChange = { draft = it })
        Text("Consultar Preços na busca inicial", Modifier.padding(top = 12.dp))
    }
    TextButton(enabled = !busy && (saved == null || draft != saved), onClick = {
        busy = true
        scope.launch {
            try { HomePriceSearchAccess.saveUser(uid, draft); message = "Acesso à busca inicial salvo." }
            catch (_: Exception) { message = "Não foi possível salvar. Tente novamente." }
            finally { busy = false }
        }
    }) { Text("Salvar acesso à busca inicial") }
    message?.let { Text(it) }
}
