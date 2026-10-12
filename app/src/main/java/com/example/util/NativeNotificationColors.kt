package com.example.util

/** Native surface contrast is independent of the app's light/dark preference. */
object NativeNotificationColors {
    data class TextColors(val title: Int, val body: Int, val background: Int)
    fun text(dark: Boolean) = if (dark) {
        TextColors(0xFFF5F7FC.toInt(), 0xFFD4DCE8.toInt(), 0xFF141821.toInt())
    } else {
        TextColors(0xFF202938.toInt(), 0xFF465266.toInt(), 0xFFFAFBFF.toInt())
    }
    fun expandedIsDark(xiaomi: Boolean, systemDark: Boolean) = xiaomi || systemDark
    fun button(dark: Boolean, accent: Int, style: String): Int {
        val base = if (dark) 0xFF242B36.toInt() else 0xFFE9EDF4.toInt()
        val amount = when (style) { "solid" -> 0f; "tonal" -> .10f; "gradient" -> .14f; "glass" -> .06f; else -> .10f }
        fun channel(shift: Int): Int {
            val a = (base ushr shift) and 255
            val b = (accent ushr shift) and 255
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    fun hex(color: Int) = String.format(java.util.Locale.ROOT, "#%06X", color and 0xFFFFFF)
}
