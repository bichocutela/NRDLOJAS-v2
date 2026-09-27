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
        val entryMemoryLimitBytes = (3.5 * 1024.0 * 1024.0 * 1024.0).toLong()
        val isEntryLevel = activityManager?.isLowRamDevice == true ||
            !hasMemoryInfo || totalMemoryBytes <= entryMemoryLimitBytes

        DevicePerformanceTier(
            isEntryLevel = isEntryLevel,
            maxBackgroundBubbles = if (isEntryLevel) 4 else 16,
            enableComplexShaders = !isEntryLevel,
            pausePhysicsOnScroll = isEntryLevel,
            pausePhysicsOnDrawer = true
        )
    }
}
