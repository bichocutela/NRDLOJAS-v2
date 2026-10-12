package com.example.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.graphics.ColorUtils
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
        val dark = if (source(context) == "system") systemDark else when (p.getString("appMode", "system")) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }
        val style = style(context)
        val accent = p.getInt("accent", Color.rgb(25, 118, 210))
        val base = if (dark) Color.rgb(20, 24, 33) else Color.rgb(250, 251, 255)
        val background = if (style == "solid") base else ColorUtils.blendARGB(base, accent, if (dark) .14f else .07f)
        return Palette(dark, style, accent, background, if (dark) Color.WHITE else Color.rgb(25, 32, 44))
    }
    private var cachedKey: String? = null
    private var cachedBackground: Bitmap? = null
    @Synchronized
    fun applyFocus(context: Context, parameter: JSONObject, pictures: Bundle) {
        val palette = palette(context)
        val background = JSONObject().put("type", 1).put("colorBg", palette.backgroundHex)
        if (palette.style in setOf("gradient", "glass")) {
            if (cachedKey != palette.key || cachedBackground == null) {
                val bitmap = Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888)
                val end = ColorUtils.blendARGB(palette.background, palette.accent, if (palette.dark) .10f else .04f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, 480f, 240f, palette.background, end, Shader.TileMode.CLAMP)
                }
                val canvas = Canvas(bitmap)
                canvas.drawRect(0f, 0f, 480f, 240f, paint)
                if (palette.style == "glass") {
                    paint.shader = null
                    paint.color = if (palette.dark) 0x0CFFFFFF else 0x30FFFFFF
                    canvas.drawOval(-80f, -130f, 470f, 70f, paint)
                }
                // Small cache; no wallpaper capture, blur loop or background animation.
                cachedKey = palette.key
                cachedBackground = bitmap
            }
            pictures.putParcelable("miui.focus.pic_bg", Icon.createWithBitmap(cachedBackground!!))
            background.put("picBg", "miui.focus.pic_bg")
        }
        parameter.put("bgInfo", background)
        for (component in listOf("baseInfo", "hintInfo")) {
            parameter.optJSONObject(component)?.let { info ->
                for (field in listOf("colorTitle", "colorContent", "colorSubTitle", "colorSubContent", "colorExtraTitle")) {
                    // The chosen background is fixed to the user's preference, so both OEM modes
                    // receive the matching foreground (even when app and phone modes differ).
                    info.put(field, palette.foregroundHex)
                    info.put(field + "Dark", palette.foregroundHex)
                }
            }
        }
    }
    private fun hex(color: Int) = String.format(java.util.Locale.ROOT, "#%06X", color and 0xFFFFFF)
}
