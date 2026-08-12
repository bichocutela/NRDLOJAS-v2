package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.DynamicTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicTabScreen(tab: DynamicTab, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (tab.type) {
                "text" -> {
                    Text(text = tab.content, style = MaterialTheme.typography.bodyLarge)
                }
                "image" -> {
                    AsyncImage(
                        model = tab.content,
                        contentDescription = tab.title,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                "video" -> {
                    Text("Funcionalidade de vídeo será integrada futuramente. URL: ${tab.content}", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    Text("Tipo não suportado.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
