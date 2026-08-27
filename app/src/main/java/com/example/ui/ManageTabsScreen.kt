package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.DynamicTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTabsScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    val tabs by viewModel.dynamicTabs.collectAsState()
    val isSyncingTabs by viewModel.isSyncingTabs.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingTab by remember { mutableStateOf<DynamicTab?>(null) }
    var tabToDelete by remember { mutableStateOf<DynamicTab?>(null) }
    
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("text") } // "text", "image", "video"
    var content by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gerenciar Abas") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                title = ""
                type = "text"
                content = ""
                editingTab = null
                showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova Aba")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            items(tabs.sortedWith(compareBy<DynamicTab> { it.displayOrder }.thenBy { it.id })) { tab ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tab.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tipo: ${if (tab.type == "video") "Vídeo legado não suportado" else tab.type}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (tab.type == "video") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val orderedTabs = tabs.sortedWith(compareBy<DynamicTab> { it.displayOrder }.thenBy { it.id })
                        val tabIndex = orderedTabs.indexOfFirst { it.id == tab.id }
                        IconButton(
                            onClick = { viewModel.moveTab(tab, -1) },
                            enabled = !isSyncingTabs && tabIndex > 0
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Mover para cima")
                        }
                        IconButton(
                            onClick = { viewModel.moveTab(tab, 1) },
                            enabled = !isSyncingTabs && tabIndex >= 0 && tabIndex < orderedTabs.lastIndex
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Mover para baixo")
                        }
                        IconButton(
                            onClick = {
                                title = tab.title
                                type = tab.type.takeIf { it == "text" || it == "image" } ?: "text"
                                content = tab.content
                                editingTab = tab
                                showDialog = true
                            },
                            enabled = !isSyncingTabs
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(
                            onClick = { tabToDelete = tab },
                            enabled = !isSyncingTabs
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir")
                        }
                    }
                }
            }
        }
        
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(if (editingTab == null) "Nova Aba" else "Editar Aba") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Título da Aba") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Tipo da Aba:", style = MaterialTheme.typography.bodyMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = type == "text", onClick = { type = "text" })
                                Text("Texto")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = type == "image", onClick = { type = "image" })
                                Text("Imagem")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { 
                                                                    Text(
                                    if (type == "image") "URL da Imagem" else "Conteúdo (Texto)"
                                )

                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            if (editingTab != null) {
                                viewModel.updateTab(editingTab!!.copy(title = title, type = type, content = content))
                            } else {
                                val order = (tabs.maxOfOrNull { it.displayOrder } ?: 0) + 1
                                viewModel.insertTab(DynamicTab(title = title, type = type, content = content, displayOrder = order))
                            }
                            showDialog = false
                        }
                        },
                        enabled = !isSyncingTabs
                    ) {
                        Text(if (isSyncingTabs) "Salvando..." else "Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        tabToDelete?.let { selectedTab ->
            AlertDialog(
                onDismissRequest = { if (!isSyncingTabs) tabToDelete = null },
                title = { Text("Excluir aba?") },
                text = {
                    Text("A aba \"${selectedTab.title}\" será removida para todos os usuários. Essa ação não poderá ser desfeita automaticamente.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            tabToDelete = null
                            viewModel.deleteTab(selectedTab)
                        },
                        enabled = !isSyncingTabs
                    ) {
                        Text("Excluir", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { tabToDelete = null },
                        enabled = !isSyncingTabs
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
