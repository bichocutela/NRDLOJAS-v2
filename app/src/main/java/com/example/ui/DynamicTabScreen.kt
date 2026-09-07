package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.DynamicPageBlock
import com.example.data.DynamicPageCodec
import com.example.data.DynamicPageDocument
import com.example.data.DynamicTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicTabScreen(tab: DynamicTab, onNavigateBack: () -> Unit) {
    val visibleNow = DynamicPageCodec.isVisible(tab)
    val blocks = DynamicPageCodec.blocksFor(tab)
    val document = DynamicPageCodec.decodeDocument(tab.content)
    val isCourse = document?.mode == DynamicPageDocument.MODE_COURSE

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
        if (!visibleNow) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Este conteúdo não está disponível neste momento.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        if (blocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isCourse) "Nenhuma aula foi adicionada a este curso."
                    else "Nenhum conteúdo foi adicionado a esta página.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        if (isCourse) {
            DynamicCourseContent(
                tab = tab,
                blocks = blocks,
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(blocks, key = { it.id }) { block ->
                when (block.type) {
                    DynamicPageBlock.TYPE_TEXT -> {
                        Text(
                            text = block.value,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    DynamicPageBlock.TYPE_IMAGE -> {
                        DynamicImageBlock(
                            url = block.value,
                            title = tab.title
                        )
                    }

                    DynamicPageBlock.TYPE_VIDEO -> {
                        DynamicVideoBlock(
                            url = block.value,
                            title = tab.title
                        )
                    }

                    DynamicPageBlock.TYPE_AUDIO -> {
                        DynamicAudioBlock(
                            url = block.value,
                            title = tab.title
                        )
                    }

                    DynamicPageBlock.TYPE_PDF -> {
                        DynamicPdfBlock(
                            url = block.value,
                            title = tab.title
                        )
                    }

                    else -> {
                        Text(
                            "Tipo de conteúdo não suportado.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
