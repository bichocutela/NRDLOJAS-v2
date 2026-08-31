package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.BannerMaskSettings
import com.example.data.ThemeBackground

@Composable
fun BannerPreviewEditor(
    themeKey: String,
    themeLabel: String,
    background: ThemeBackground,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ThemeBackground, BannerMaskSettings) -> Unit
) {
    val storedMask = rememberBannerMaskSettings(themeKey, background.url)
    var maskDraft by remember(background.id) { mutableStateOf<BannerMaskSettings?>(null) }
    val effectiveMask = (maskDraft ?: storedMask).normalized()

    var previewScale by remember(background.id, background.imageScale) {
        mutableFloatStateOf(background.imageScale.coerceIn(0.5f, 3f))
    }
    var previewOffsetX by remember(background.id, background.imageOffsetX) {
        mutableFloatStateOf(background.imageOffsetX.coerceIn(-1f, 1f))
    }
    var previewOffsetY by remember(background.id, background.imageOffsetY) {
        mutableFloatStateOf(background.imageOffsetY.coerceIn(-1f, 1f))
    }
    var previewStretchX by remember(background.id, background.imageStretchX) {
        mutableFloatStateOf(background.imageStretchX.coerceIn(0.5f, 2.5f))
    }
    var previewStretchY by remember(background.id, background.imageStretchY) {
        mutableFloatStateOf(background.imageStretchY.coerceIn(0.5f, 2.5f))
    }

    var framingUnlocked by remember(background.id) { mutableStateOf(false) }
    var framingExpanded by remember(background.id) { mutableStateOf(false) }
    var maskExpanded by remember(background.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Prévia da Home",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${background.label} • $themeLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar prévia")
                    }
                }

                Text(
                    "Resultado na Home",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                HomeResultPreview(
                    themeKey = themeKey,
                    background = background,
                    maskSettings = effectiveMask,
                    imageScale = previewScale,
                    imageOffsetX = previewOffsetX,
                    imageOffsetY = previewOffsetY,
                    imageStretchX = previewStretchX,
                    imageStretchY = previewStretchY
                )

                Text(
                    "O resultado acima fica visível enquanto você abre somente o grupo de ajustes necessário.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExpandableEditorCard(
                        title = "Enquadramento",
                        subtitle = "Zoom ${(previewScale * 100).toInt()}% • posição e proporção",
                        expanded = framingExpanded,
                        onToggle = {
                            val shouldOpen = !framingExpanded
                            framingExpanded = shouldOpen
                            if (shouldOpen) maskExpanded = false
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (framingUnlocked) "Ajustes liberados" else "Ajustes protegidos",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    if (framingUnlocked) "Mova, amplie ou estique a arte." else "Abra o cadeado para editar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { framingUnlocked = !framingUnlocked }) {
                                Icon(
                                    if (framingUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = if (framingUnlocked) "Bloquear enquadramento" else "Desbloquear enquadramento",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(122.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = background.url,
                                contentDescription = "Imagem original de ${background.label}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = previewScale * previewStretchX
                                        scaleY = previewScale * previewStretchY
                                        translationX = size.width * previewOffsetX
                                        translationY = size.height * previewOffsetY
                                    },
                                contentScale = ContentScale.Fit
                            )
                        }

                        EditorSlider(
                            label = "Zoom",
                            valueLabel = "${(previewScale * 100).toInt()}%",
                            value = previewScale,
                            valueRange = 0.5f..3f,
                            enabled = framingUnlocked,
                            onValueChange = { previewScale = it }
                        )
                        EditorSlider(
                            label = "Horizontal",
                            valueLabel = "${(previewOffsetX * 100).toInt()}",
                            value = previewOffsetX,
                            valueRange = -1f..1f,
                            enabled = framingUnlocked,
                            onValueChange = { previewOffsetX = it }
                        )
                        EditorSlider(
                            label = "Vertical",
                            valueLabel = "${(previewOffsetY * 100).toInt()}",
                            value = previewOffsetY,
                            valueRange = -1f..1f,
                            enabled = framingUnlocked,
                            onValueChange = { previewOffsetY = it }
                        )
                        EditorSlider(
                            label = "Largura",
                            valueLabel = "${(previewStretchX * 100).toInt()}%",
                            value = previewStretchX,
                            valueRange = 0.5f..2.5f,
                            enabled = framingUnlocked,
                            onValueChange = { previewStretchX = it }
                        )
                        EditorSlider(
                            label = "Altura",
                            valueLabel = "${(previewStretchY * 100).toInt()}%",
                            value = previewStretchY,
                            valueRange = 0.5f..2.5f,
                            enabled = framingUnlocked,
                            onValueChange = { previewStretchY = it }
                        )

                        TextButton(
                            onClick = {
                                previewScale = 1f
                                previewOffsetX = 0f
                                previewOffsetY = 0f
                                previewStretchX = 1f
                                previewStretchY = 1f
                            },
                            enabled = framingUnlocked,
                            modifier = Modifier.align(Alignment.End),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Restaurar")
                        }
                    }

                    ExpandableEditorCard(
                        title = "Máscara e transição",
                        subtitle = "${maskStyleLabel(effectiveMask.style)} • ${(effectiveMask.strength * 100).toInt()}% de intensidade",
                        expanded = maskExpanded,
                        onToggle = {
                            val shouldOpen = !maskExpanded
                            maskExpanded = shouldOpen
                            if (shouldOpen) framingExpanded = false
                        }
                    ) {
                        Text(
                            "A borda agora se mistura com o próprio fundo da tela, criando a névoa suave do resultado acima.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("Tipo", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MaskStyleChip(
                                label = "Suave",
                                selected = effectiveMask.style == BannerMaskSettings.STYLE_SOFT,
                                modifier = Modifier.weight(1f)
                            ) {
                                maskDraft = effectiveMask.copy(style = BannerMaskSettings.STYLE_SOFT)
                            }
                            MaskStyleChip(
                                label = "Definida",
                                selected = effectiveMask.style == BannerMaskSettings.STYLE_DEFINED,
                                modifier = Modifier.weight(1f)
                            ) {
                                maskDraft = effectiveMask.copy(style = BannerMaskSettings.STYLE_DEFINED)
                            }
                            MaskStyleChip(
                                label = "Difusa",
                                selected = effectiveMask.style == BannerMaskSettings.STYLE_DIFFUSE,
                                modifier = Modifier.weight(1f)
                            ) {
                                maskDraft = effectiveMask.copy(style = BannerMaskSettings.STYLE_DIFFUSE)
                            }
                        }

                        EditorSlider(
                            label = "Intensidade",
                            valueLabel = "${(effectiveMask.strength * 100).toInt()}%",
                            value = effectiveMask.strength,
                            valueRange = 0f..1f,
                            enabled = true,
                            onValueChange = { maskDraft = effectiveMask.copy(strength = it) }
                        )
                        EditorSlider(
                            label = "Profundidade",
                            valueLabel = "${(effectiveMask.depth * 100).toInt()}%",
                            value = effectiveMask.depth,
                            valueRange = 0.08f..0.45f,
                            enabled = true,
                            onValueChange = { maskDraft = effectiveMask.copy(depth = it) }
                        )

                        Text("Lados", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            EdgeChip("Cima", effectiveMask.shadeTop, Modifier.weight(1f)) {
                                maskDraft = effectiveMask.copy(shadeTop = !effectiveMask.shadeTop)
                            }
                            EdgeChip("Baixo", effectiveMask.shadeBottom, Modifier.weight(1f)) {
                                maskDraft = effectiveMask.copy(shadeBottom = !effectiveMask.shadeBottom)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            EdgeChip("Esquerda", effectiveMask.shadeLeft, Modifier.weight(1f)) {
                                maskDraft = effectiveMask.copy(shadeLeft = !effectiveMask.shadeLeft)
                            }
                            EdgeChip("Direita", effectiveMask.shadeRight, Modifier.weight(1f)) {
                                maskDraft = effectiveMask.copy(shadeRight = !effectiveMask.shadeRight)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { maskDraft = effectiveMask.copy(strength = 0f) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("Sem transição", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    maskDraft = BannerMaskSettings(
                                        style = BannerMaskSettings.STYLE_SOFT,
                                        strength = 0.62f,
                                        depth = 0.30f,
                                        shadeTop = false,
                                        shadeBottom = true,
                                        shadeLeft = false,
                                        shadeRight = false
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("Névoa embaixo", maxLines = 1)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            background.copy(
                                imageScale = previewScale,
                                imageOffsetX = previewOffsetX,
                                imageOffsetY = previewOffsetY,
                                imageStretchX = previewStretchX,
                                imageStretchY = previewStretchY
                            ),
                            effectiveMask
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    contentPadding = PaddingValues(vertical = 9.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvando...")
                    } else {
                        Text("Salvar enquadramento e máscara")
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("Fechar sem salvar")
                }
            }
        }
    }
}

@Composable
private fun HomeResultPreview(
    themeKey: String,
    background: ThemeBackground,
    maskSettings: BannerMaskSettings,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageStretchX: Float,
    imageStretchY: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val previewHeight = maxWidth / 3f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight)
                        .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    MaskedThemeBanner(
                        appTheme = themeKey,
                        backgroundUrl = background.url,
                        imageScale = imageScale,
                        imageOffsetX = imageOffsetX,
                        imageOffsetY = imageOffsetY,
                        imageStretchX = imageStretchX,
                        imageStretchY = imageStretchY,
                        maskSettingsOverride = maskSettings,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Pesquisar produto...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExpandableEditorCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Recolher $title" else "Expandir $title",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (expanded) {
                HorizontalDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun EditorSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}

@Composable
private fun MaskStyleChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier
    )
}

@Composable
private fun EdgeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier
    )
}

private fun maskStyleLabel(style: String): String = when (style) {
    BannerMaskSettings.STYLE_DEFINED -> "Definida"
    BannerMaskSettings.STYLE_DIFFUSE -> "Difusa"
    else -> "Suave"
}
