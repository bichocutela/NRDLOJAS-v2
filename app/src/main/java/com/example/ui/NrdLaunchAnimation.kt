package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.hypot

/**
 * A short launch overlay: navigation composes immediately underneath it.
 * Animation state is read only in drawing/layer phases, not by the Home.
 * Saveable completion avoids replaying the intro on activity recreation.
 */
@Composable
internal fun NrdLaunchAnimation(appTheme: String) {
    var completed by rememberSaveable { mutableStateOf(false) }
    if (completed) return

    val progress = remember { Animatable(0f) }
    val background = MaterialTheme.colorScheme.primaryContainer
    val logo = when (appTheme) {
        "red" -> R.drawable.nrd_logo_red
        "green" -> R.drawable.nrd_logo_green
        "blue" -> R.drawable.nrd_logo_blue
        "orange" -> R.drawable.nrd_logo_orange
        "gold" -> R.drawable.nrd_logo_gold
        else -> R.drawable.nrd_logo_multicolor
    }

    LaunchedEffect(Unit) {
        // Animatable uses the platform animation duration scale, including disabled animations.
        progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        completed = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Do not allow invisible Home controls to receive touches during the intro.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .drawWithCache {
                val mask = Path()
                val maxRadius = hypot(size.width / 2f, size.height / 2f)
                onDrawBehind {
                    val reveal = ((progress.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    val radius = maxRadius * reveal
                    mask.reset()
                    mask.fillType = PathFillType.EvenOdd
                    mask.addRect(Rect(0f, 0f, size.width, size.height))
                    if (radius > 0f) {
                        mask.addOval(
                            Rect(
                                center.x - radius, center.y - radius,
                                center.x + radius, center.y + radius
                            )
                        )
                    }
                    drawPath(mask, background)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = "NRD Códigos Correlatos",
            modifier = Modifier
                .size(150.dp)
                .graphicsLayer {
                    val entrance = (progress.value / 0.3f).coerceIn(0f, 1f)
                    val exit = ((progress.value - 0.4f) / 0.25f).coerceIn(0f, 1f)
                    scaleX = 0.92f + 0.08f * entrance
                    scaleY = scaleX
                    alpha = entrance * (1f - exit)
                }
        )
    }
}
