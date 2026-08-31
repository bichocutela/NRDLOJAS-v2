package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Prévia da Home",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${background.label} • $themeLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar prévia")
                    }
                }

                Text(
                    "Resultado final",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val previewHeight = maxWidth / 3f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight)
                            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        MaskedThemeBanner(
                            appTheme = themeKey,
                            backgroundUrl = background.url,
                            imageScale = previewScale,
                            imageOffsetX = previewOffsetX,
                            imageOffsetY = previewOffsetY,
                            imageStretchX = previewStretchX,
                            imageStretchY = previewStretchY,
                            maskSettingsOverride = effectiveMask,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Enquadramento da imagem",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (framingUnlocked) "Ajustes liberados" else "Protegido contra alterações acidentais",
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
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
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
                            label = "Mover horizontal",
                            valueLabel = "${(previewOffsetX * 100).toInt()}",
                            value = previewOffsetX,
                            valueRange = -1f..1f,
                            enabled = framingUnlocked,
                            onValueChange = { previewOffsetX = it }
                        )
                        EditorSlider(
                            label = "Mover vertical",
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
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Restaurar enquadramento")
                        }
                    }
                }

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Máscara e acabamento",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Escolha onde o fundo se mistura com a tela. Em 0% não há sombra; em 100% a borda chega ao sólido.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("Tipo de máscara", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            label = "Profundidade da transição",
                            valueLabel = "${(effectiveMask.depth * 100).toInt()}%",
                            value = effectiveMask.depth,
                            valueRange = 0.08f..0.45f,
                            enabled = true,
                            onValueChange = { maskDraft = effectiveMask.copy(depth = it) }
                        )

                        Text("Lados da máscara", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { maskDraft = effectiveMask.copy(strength = 0f) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Sem sombra")
                            }
                            OutlinedButton(
                                onClick = {
                                    maskDraft = BannerMaskSettings(
                                        style = BannerMaskSettings.STYLE_SOFT,
                                        strength = 0.35f,
                                        depth = 0.22f,
                                        shadeTop = false,
                                        shadeBottom = true,
                                        shadeLeft = false,
                                        shadeRight = false
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Suave embaixo")
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
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvando...")
                    } else {
                        Text("Salvar enquadramento e máscara")
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    Text("Fechar sem salvar")
                }
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
