package com.example.ui.theme

import androidx.annotation.RawRes

/** Selects the renderer used by the liquid background. */
enum class LiquidEngineType {
    PARAMETRIC,
    LOTTIE,
    RIVE
}

/** Optional animation assets for the non-native engines. */
data class LiquidEngineConfig(
    val type: LiquidEngineType = LiquidEngineType.PARAMETRIC,
    val lottieAssetName: String? = null,
    @RawRes val riveResourceId: Int? = null,
    val riveStateMachineName: String? = null
)

val LocalLiquidEngineConfig = androidx.compose.runtime.staticCompositionLocalOf { LiquidEngineConfig() }
