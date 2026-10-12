package com.example.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import org.json.JSONObject

/** Device preferences. Native notification surfaces still belong to the operating system. */
object NotificationAppearance {
    private fun prefs(context: Context) = context.getSharedPreferences("nrd_notification_appearance", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun source(context: Context) = prefs(context).getString("source", "app") ?: "app"
    fun style(context: Context) = prefs(context).getString("style", "tonal") ?: "tonal"
    fun save(context: Context, enabled: Boolean, source: String, style: String) {
        prefs(context).edit().putBoolean("enabled", enabled)
            .putString("source", source.takeIf { it in setOf("app", "system") } ?: "app")
            .putString("style", style.takeIf { it in setOf("solid", "tonal", "gradient", "glass") } ?: "tonal").apply()
        if (!enabled) XiaomiSuperIslandUpdateNotifier.cancel(context)
    }
    fun rememberAppTheme(context: Context, mode: String, accent: Int) {
        val p = prefs(context)
        if (p.getString("appMode", "system") != mode || p.getInt("accent", Color.rgb(25, 118, 210)) != accent) {
            p.edit().putString("appMode", mode).putInt("accent", accent).apply()
        }
    }
    data class Palette(val dark: Boolean, val style: String, val accent: Int, val background: Int, val foreground: Int) {
        val key: String get() = "$dark:$style:$accent:$background"
        val backgroundHex: String get() = hex(background)
        val foregroundHex: String get() = hex(foreground)
    }
    fun palette(context: Context): Palette {
        val p = prefs(context)
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = NativeNotificationColors.expandedIsDark(isXiaomi(), systemDark)
        val style = style(context)
        val accent = if (source(context) == "system" && android.os.Build.VERSION.SDK_INT >= 31) {
            context.getColor(android.R.color.system_accent1_600)
        } else p.getInt("accent", Color.rgb(25, 118, 210))
        val colors = NativeNotificationColors.text(dark)
        return Palette(dark, style, accent, colors.background, colors.title)
    }
    private fun isXiaomi(): Boolean {
        val brand = (android.os.Build.MANUFACTURER + " " + android.os.Build.BRAND).lowercase(java.util.Locale.ROOT)
        return listOf("xiaomi", "redmi", "poco").any { it in brand }
    }
    fun applyFocus(context: Context, parameter: JSONObject) {
        // HyperIsland expanded cards are always dark. Its notification shade can be light.
        // Never copy the app's foreground into both native modes, or force a background
        // that the OEM may ignore. Supply a genuine light/dark pair instead.
        parameter.remove("bgInfo")
        val palette = palette(context)
        val light = NativeNotificationColors.text(false)
        val dark = NativeNotificationColors.text(true)
        for (component in listOf("baseInfo", "hintInfo")) {
            parameter.optJSONObject(component)?.let { info ->
                for (field in listOf("colorTitle", "colorSubTitle", "colorExtraTitle")) {
                    info.put(field, hex(light.title)).put(field + "Dark", hex(dark.title))
                }
                for (field in listOf("colorContent", "colorSubContent")) {
                    info.put(field, hex(light.body)).put(field + "Dark", hex(dark.body))
                }
                info.optJSONObject("actionInfo")?.let { action ->
                    action.put("actionTitleColor", hex(light.title)).put("actionTitleColorDark", hex(dark.title))
                    action.put("actionBgColor", hex(NativeNotificationColors.button(false, palette.accent, palette.style)))
                    action.put("actionBgColorDark", hex(NativeNotificationColors.button(true, palette.accent, palette.style)))
                }
            }
        }
    }
    private fun hex(color: Int) = String.format(java.util.Locale.ROOT, "#%06X", color and 0xFFFFFF)
}
