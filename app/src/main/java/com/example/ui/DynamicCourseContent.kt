package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DynamicPageBlock
import com.example.data.DynamicTab

@Composable
fun DynamicCourseContent(
    tab: DynamicTab,
    blocks: List<DynamicPageBlock>,
    modifier: Modifier = Modifier
) {
    var expandedBlockId by remember(tab.id, blocks) {
        mutableStateOf(blocks.firstOrNull()?.id)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "TREINAMENTO NRD",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                tab.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Text(
                        "Curso disponibilizado pelo Painel Mestre. O acesso é direto, sem login ou matrícula.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { },
                            enabled = false,
                            label = { Text("${blocks.size} aula(s)") },
                            leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) }
                        )
                        AssistChip(
                            onClick = { },
                            enabled = false,
                            label = { Text("Acesso livre") },
                            leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Conteúdo do curso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        itemsIndexed(
            blocks,
            key = { _, block -> block.id }
        ) { index, block ->
            val expanded = expandedBlockId == block.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expandedBlockId = if (expanded) null else block.id
                    },
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconForCourseBlock(block.type),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Aula ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                labelForCourseBlock(block.type),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Recolher aula" else "Abrir aula"
                        )
                    }

                    if (expanded) {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            when (block.type) {
                                DynamicPageBlock.TYPE_TEXT -> Text(
                                    block.value,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                DynamicPageBlock.TYPE_IMAGE -> DynamicImageBlock(
                                    url = block.value,
                                    title = tab.title
                                )
                                DynamicPageBlock.TYPE_VIDEO -> DynamicVideoBlock(
                                    url = block.value,
                                    title = tab.title
                                )
                                DynamicPageBlock.TYPE_AUDIO -> DynamicAudioBlock(
                                    url = block.value,
                                    title = tab.title
                                )
                                DynamicPageBlock.TYPE_PDF -> DynamicPdfBlock(
                                    url = block.value,
                                    title = tab.title
                                )
                                else -> Text(
                                    "Tipo de conteúdo não suportado.",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun labelForCourseBlock(type: String): String = when (type) {
    DynamicPageBlock.TYPE_TEXT -> "Leitura"
    DynamicPageBlock.TYPE_IMAGE -> "Material visual"
    DynamicPageBlock.TYPE_VIDEO -> "Vídeo"
    DynamicPageBlock.TYPE_AUDIO -> "Áudio"
    DynamicPageBlock.TYPE_PDF -> "Documento PDF"
    else -> "Conteúdo"
}

private fun iconForCourseBlock(type: String) = when (type) {
    DynamicPageBlock.TYPE_TEXT -> Icons.Default.TextFields
    DynamicPageBlock.TYPE_IMAGE -> Icons.Default.Image
    DynamicPageBlock.TYPE_VIDEO -> Icons.Default.PlayCircle
    DynamicPageBlock.TYPE_AUDIO -> Icons.Default.Headphones
    DynamicPageBlock.TYPE_PDF -> Icons.Default.PictureAsPdf
    else -> Icons.Default.Description
}
