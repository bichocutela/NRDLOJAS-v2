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

internal const val MIN_INTERFACE_SCALE = 0.75f
internal const val MAX_INTERFACE_SCALE = 1.30f

internal fun normalizedInterfaceScale(value: Float): Float =
    value.coerceIn(MIN_INTERFACE_SCALE, MAX_INTERFACE_SCALE)

/**
 * Redimensiona a estrutura sem deixar alvos de toque e cards encolherem na mesma
 * proporção agressiva da tipografia. A fonte acompanha toda a escala; a geometria
 * acompanha metade da variação em relação a 1x.
 */
internal fun interfaceLayoutScale(value: Float): Float {
    val safe = normalizedInterfaceScale(value)
    return 1f + ((safe - 1f) * 0.5f)
}

/**
 * O Expressivo usa formas e hierarquia visual maiores por natureza. Em telefones
 * compactos aplicamos uma correção automática para manter a mesma composição do
 * mockup sem provocar sensação de zoom ou cortes.
 */
internal fun expressiveResponsiveScale(widthDp: Int, enabled: Boolean): Float {
    if (!enabled) return 1f
    return when {
        widthDp < 360 -> 0.84f
        widthDp < 390 -> 0.88f
        widthDp < 430 -> 0.92f
        widthDp < 480 -> 0.96f
        else -> 1f
    }
}

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
