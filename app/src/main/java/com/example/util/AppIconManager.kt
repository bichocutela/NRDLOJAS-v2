package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object AppIconManager {
    private const val TAG = "AppIconManager"

    private val aliases = mapOf(
        "multicolor" to "com.example.MainActivityMulticolor",
        "red" to "com.example.MainActivityRed",
        "green" to "com.example.MainActivityGreen",
        "blue" to "com.example.MainActivityBlue",
        "orange" to "com.example.MainActivityOrange",
        "gold" to "com.example.MainActivityGold"
    )

    fun applyIcon(context: Context, iconName: String): Boolean {
        val pm = context.packageManager
        val packageName = context.packageName
        val targetAliasName = aliases[iconName] ?: aliases["multicolor"]!!
        val targetComponent = ComponentName(packageName, targetAliasName)

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // API 33+ Atomic update
                val componentSettings = aliases.values.map { aliasName ->
                    PackageManager.ComponentEnabledSetting(
                        ComponentName(packageName, aliasName),
                        if (aliasName == targetAliasName) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
                pm.setComponentEnabledSettings(componentSettings)
            } else {
                // API < 33 Sequential update
                // 1. Enable target first
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                // 2. Disable others
                aliases.values.forEach { aliasName ->
                    if (aliasName != targetAliasName) {
                        pm.setComponentEnabledSetting(
                            ComponentName(packageName, aliasName),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            }

            return verifyIconState(context, targetAliasName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply icon", e)
            return false
        }
    }

    private fun verifyIconState(context: Context, expectedAlias: String): Boolean {
        val pm = context.packageManager
        val packageName = context.packageName
        var activeCount = 0
        var isExpectedActive = false

        aliases.values.forEach { aliasName ->
            val state = pm.getComponentEnabledSetting(ComponentName(packageName, aliasName))
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                activeCount++
                if (aliasName == expectedAlias) {
                    isExpectedActive = true
                }
            } else if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                if (aliasName == aliases["multicolor"]) {
                    activeCount++
                    if (aliasName == expectedAlias) isExpectedActive = true
                }
            }
        }

        return activeCount == 1 && isExpectedActive
    }

    fun ensureValidIconState(context: Context, savedIcon: String?) {
        val targetIcon = savedIcon?.takeIf { aliases.containsKey(it) } ?: "multicolor"
        val expectedAlias = aliases[targetIcon]!!
        
        if (!verifyIconState(context, expectedAlias)) {
            Log.w(TAG, "Invalid icon state detected. Auto-repairing to $targetIcon")
            applyIcon(context, targetIcon)
        }
    }
}
