package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.DynamicPageBlock
import com.example.data.DynamicPageCodec
import com.example.data.DynamicPageDocument
import com.example.data.DynamicTab
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.expressiveShadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicTabScreen(tab: DynamicTab, onNavigateBack: () -> Unit) {
    val visibleNow = DynamicPageCodec.isVisible(tab)
    val blocks = DynamicPageCodec.blocksFor(tab)
    val document = DynamicPageCodec.decodeDocument(tab.content)
    val isCourse = document?.mode == DynamicPageDocument.MODE_COURSE
    val expressive = LocalExpressiveStyle.current.enabled
    val glassStyle = LocalGlassSoftStyle.current
    val screenProfile = rememberNrdScreenProfile()

    Scaffold(
        containerColor = if (expressive || glassStyle.enabled) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        tab.title,
                        fontWeight = if (expressive) FontWeight.ExtraBold else FontWeight.Normal
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (expressive || glassStyle.enabled) Color.Transparent else MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
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
            contentPadding = PaddingValues(
                horizontal = if (screenProfile.compact) 10.dp else 14.dp,
                vertical = if (screenProfile.compact) 8.dp else 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (screenProfile.compact) 10.dp else 12.dp)
        ) {
            items(blocks, key = { it.id }) { block ->
                when (block.type) {
                    DynamicPageBlock.TYPE_TEXT -> {
                        if (expressive) {
                            val textShape = RoundedCornerShape(if (screenProfile.compact) 20.dp else 24.dp)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .expressiveShadow(textShape, 5.dp),
                                shape = textShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Text(
                                    text = block.value,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(
                                        horizontal = if (screenProfile.compact) 14.dp else 18.dp,
                                        vertical = if (screenProfile.compact) 12.dp else 16.dp
                                    )
                                )
                            }
                        } else {
                            Text(
                                text = block.value,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
