package com.example.ui.theme

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Device-wide rendering limits selected once for the lifetime of the app process. */
data class DevicePerformanceTier(
    val isEntryLevel: Boolean,
    val maxBackgroundBubbles: Int,
    val enableComplexShaders: Boolean,
    val pausePhysicsOnScroll: Boolean,
    val pausePhysicsOnDrawer: Boolean
)

@Composable
fun rememberDevicePerformanceTier(): DevicePerformanceTier {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        val hasMemoryInfo = runCatching {
            activityManager?.getMemoryInfo(memoryInfo)
            activityManager != null && memoryInfo.totalMem > 0L
        }.getOrDefault(false)
        val totalMemoryBytes = if (hasMemoryInfo) memoryInfo.totalMem else 0L
        // Devices advertise RAM in round numbers, while Android may reserve memory for hardware.
        // 7.2 GB reported total is the conservative cutoff for a nominal 8 GB device.
        val minimumExpressiveRamBytes = 7_200_000_000L
        val isEntryLevel = activityManager?.isLowRamDevice == true ||
            !hasMemoryInfo || totalMemoryBytes < minimumExpressiveRamBytes

        DevicePerformanceTier(
            isEntryLevel = isEntryLevel,
            maxBackgroundBubbles = if (isEntryLevel) 6 else 16,
            enableComplexShaders = !isEntryLevel,
            pausePhysicsOnScroll = false,
            pausePhysicsOnDrawer = true
        )
    }
}
