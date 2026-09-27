package com.example.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import com.airbnb.lottie.AsyncUpdates
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlin.math.PI
import kotlin.math.sin

/**
 * Interchangeable liquid background renderer.
 *
 * Animation state is consumed by drawing code in the native engine; asset-based
 * engines fall back to the parametric renderer until their asset is supplied.
 */
@Composable
fun LiquidEngine(
    type: LiquidEngineType,
    modifier: Modifier = Modifier,
    bubbleCount: Int = 16,
    primary: Color = Color(0xFF80D8FF),
    secondary: Color = Color(0xFFB2EBF2),
    lottieAssetName: String? = null,
    riveResourceId: Int? = null,
    riveStateMachineName: String? = null
) {
    when (type) {
        LiquidEngineType.PARAMETRIC -> ParametricLiquidEngine(
            modifier = modifier,
            bubbleCount = bubbleCount,
            primary = primary,
            secondary = secondary
        )

        LiquidEngineType.LOTTIE -> {
            if (lottieAssetName.isNullOrBlank()) {
                ParametricLiquidEngine(modifier, bubbleCount, primary, secondary)
            } else {
                LottieLiquidEngine(modifier, lottieAssetName, bubbleCount, primary, secondary)
            }
        }

        LiquidEngineType.RIVE -> {
            if (riveResourceId == null || riveResourceId == 0) {
                ParametricLiquidEngine(modifier, bubbleCount, primary, secondary)
            } else {
                RiveLiquidEngine(
                    modifier = modifier,
                    resourceId = riveResourceId,
                    stateMachineName = riveStateMachineName
                )
            }
        }
    }
}

@Composable
private fun ParametricLiquidEngine(
    modifier: Modifier,
    bubbleCount: Int,
    primary: Color,
    secondary: Color
) {
    val count = bubbleCount.coerceIn(1, 16)
    val transition = rememberInfiniteTransition(label = "parametric-liquid")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquid-phase"
    )

    // Static particle properties and colors are initialized outside the draw loop.
    val startX = remember(count) { FloatArray(count) { index -> (index + 0.5f) / count } }
    val radiusFraction = remember(count) {
        FloatArray(count) { index -> 0.012f + (index % 4) * 0.004f }
    }
    val verticalPhase = remember(count) {
        FloatArray(count) { index -> index.toFloat() / count }
    }
    val driftFrequency = remember(count) {
        FloatArray(count) { index -> 0.65f + (index % 3) * 0.2f }
    }
    val fillColors = remember(count, primary, secondary) {
        Array(count) { index ->
            val tint = if (index % 2 == 0) primary else secondary
            tint.copy(alpha = if (index % 3 == 0) 0.28f else 0.20f)
        }
    }
    val rimColors = remember(count) {
        Array(count) { index ->
            Color.White.copy(alpha = if (index % 2 == 0) 0.55f else 0.38f)
        }
    }
    val glintColors = remember(count) {
        Array(count) { index ->
            Color.White.copy(alpha = if (index % 2 == 0) 0.46f else 0.30f)
        }
    }

    Canvas(
        modifier = modifier
            .graphicsLayer { clip = true }
            .drawBehind {
                val currentPhase = phase
                val minDimension = size.minDimension
                var index = 0
                while (index < count) {
                    val radius = minDimension * radiusFraction[index]
                    val progress = (currentPhase / (2f * PI.toFloat()) +
                        verticalPhase[index]) % 1f
                    val centerY = size.height + radius - progress * (size.height + radius * 2f)
                    val wave = sin(currentPhase * driftFrequency[index] + index * 1.17f)
                    val centerX = size.width * startX[index] + wave * size.width * 0.035f

                    drawCircle(
                        color = fillColors[index],
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = rimColors[index],
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = (radius * 0.055f).coerceAtLeast(0.7f)
                        )
                    )
                    drawCircle(
                        color = glintColors[index],
                        radius = radius * 0.16f,
                        center = androidx.compose.ui.geometry.Offset(
                            centerX - radius * 0.34f,
                            centerY - radius * 0.42f
                        )
                    )
                    index++
                }
            }
    ) {}
}

@Composable
private fun LottieLiquidEngine(
    modifier: Modifier,
    assetName: String,
    fallbackBubbleCount: Int,
    primary: Color,
    secondary: Color
) {
    val compositionResult by rememberLottieComposition(
        spec = LottieCompositionSpec.Asset(assetName)
    )

    val composition = compositionResult
    if (composition == null) {
        ParametricLiquidEngine(modifier, fallbackBubbleCount, primary, secondary)
    } else {
        LottieAnimation(
            composition = composition,
            modifier = modifier.fillMaxSize(),
            isPlaying = true,
            iterations = LottieConstants.IterateForever,
            renderMode = RenderMode.HARDWARE,
            contentScale = ContentScale.Crop,
            asyncUpdates = AsyncUpdates.AUTOMATIC
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun RiveLiquidEngine(
    modifier: Modifier,
    resourceId: Int,
    stateMachineName: String?
) {
    val context = LocalContext.current
    val machineName = stateMachineName?.takeIf(String::isNotBlank)
    val view = remember(context, resourceId, machineName) {
        RiveAnimationView(context).apply {
            setRiveResource(
                resId = resourceId,
                stateMachineName = machineName,
                autoplay = true,
                fit = Fit.COVER,
                loop = Loop.LOOP
            )
        }
    }

    AndroidView(
        factory = { view },
        modifier = modifier.fillMaxSize(),
        update = { riveView ->
            riveView.isClickable = true
            riveView.isFocusable = true
        }
    )
}
