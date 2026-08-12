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
    var showDialog by remember { mutableStateOf(false) }
    var editingTab by remember { mutableStateOf<DynamicTab?>(null) }
    
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
            FloatingActionButton(onClick = {
                title = ""
                type = "text"
                content = ""
                editingTab = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Nova Aba")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            items(tabs.sortedBy { it.displayOrder }) { tab ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tab.title, style = MaterialTheme.typography.titleMedium)
                            Text("Tipo: ${tab.type}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            if (tab.displayOrder > 0) {
                                viewModel.updateTab(tab.copy(displayOrder = tab.displayOrder - 1))
                            }
                        }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Mover para cima")
                        }
                        IconButton(onClick = {
                            viewModel.updateTab(tab.copy(displayOrder = tab.displayOrder + 1))
                        }) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Mover para baixo")
                        }
                        IconButton(onClick = {
                            title = tab.title
                            type = tab.type
                            content = tab.content
                            editingTab = tab
                            showDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = {
                            viewModel.deleteTab(tab)
                        }) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = type == "video", onClick = { type = "video" })
                                Text("Vídeo")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { 
                                Text(
                                    when (type) {
                                        "image" -> "URL da Imagem"
                                        "video" -> "URL do Vídeo (YouTube, etc)"
                                        else -> "Conteúdo (Texto)"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            if (editingTab != null) {
                                viewModel.updateTab(editingTab!!.copy(title = title, type = type, content = content))
                            } else {
                                val order = (tabs.maxOfOrNull { it.displayOrder } ?: 0) + 1
                                viewModel.insertTab(DynamicTab(title = title, type = type, content = content, displayOrder = order))
                            }
                            showDialog = false
                        }
                    }) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
