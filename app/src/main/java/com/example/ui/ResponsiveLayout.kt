package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class NrdScreenProfile(
    val compact: Boolean,
    val veryCompact: Boolean,
    val horizontalPadding: Dp,
    val actionSpacing: Dp
)

@Composable
internal fun rememberNrdScreenProfile(): NrdScreenProfile {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return NrdScreenProfile(
        compact = widthDp < 380,
        veryCompact = widthDp < 340,
        horizontalPadding = when {
            widthDp < 340 -> 10.dp
            widthDp < 380 -> 12.dp
            else -> 16.dp
        },
        actionSpacing = if (widthDp < 340) 6.dp else 8.dp
    )
}

@Composable
internal fun NrdTwoActionLayout(
    modifier: Modifier = Modifier,
    stackOnCompact: Boolean = true,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    val profile = rememberNrdScreenProfile()
    if (stackOnCompact && profile.veryCompact) {
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
