package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class NrdScreenProfile(
    val compact: Boolean,
    val veryCompact: Boolean,
    val tablet: Boolean,
    val expanded: Boolean,
    val widthDp: Int,
    val horizontalPadding: Dp,
    val actionSpacing: Dp,
    val contentMaxWidth: Dp,
    val dialogMaxWidth: Dp
)

@Composable
internal fun rememberNrdScreenProfile(): NrdScreenProfile {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return NrdScreenProfile(
        compact = widthDp < 420,
        veryCompact = widthDp < 360,
        tablet = widthDp >= 600,
        expanded = widthDp >= 840,
        widthDp = widthDp,
        horizontalPadding = when {
            widthDp < 360 -> 10.dp
            widthDp < 600 -> 16.dp
            widthDp < 840 -> 24.dp
            else -> 32.dp
        },
        actionSpacing = if (widthDp < 360) 6.dp else 8.dp,
        contentMaxWidth = when {
            widthDp < 600 -> Dp.Unspecified
            widthDp < 840 -> 720.dp
            else -> 960.dp
        },
        dialogMaxWidth = when {
            widthDp < 600 -> Dp.Unspecified
            widthDp < 840 -> 620.dp
            else -> 720.dp
        }
    )
}

/**
 * Mantém telas de telefone fluidas e impede que o conteúdo fique excessivamente
 * esticado em tablets. Em telas largas o conteúdo é centralizado com largura máxima.
 */
@Composable
internal fun NrdResponsiveContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp? = null,
    content: @Composable () -> Unit
) {
    val profile = rememberNrdScreenProfile()
    val targetMaxWidth = maxWidth ?: profile.contentMaxWidth
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val contentModifier = if (targetMaxWidth == Dp.Unspecified) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().widthIn(max = targetMaxWidth)
        }
        Box(modifier = contentModifier.padding(horizontal = profile.horizontalPadding)) {
            content()
        }
    }
}

@Composable
internal fun NrdTwoActionLayout(
    modifier: Modifier = Modifier,
    stackOnCompact: Boolean = true,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    val profile = rememberNrdScreenProfile()
    if (stackOnCompact && profile.compact) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(profile.actionSpacing)
        ) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(profile.actionSpacing)
        ) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}
