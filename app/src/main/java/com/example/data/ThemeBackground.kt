package com.example.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ThemeBackground(
    val id: String,
    val label: String,
    val url: String,
    val isActive: Boolean = false,
    val startDate: String? = null,
    val endDate: String? = null,
    val imageScale: Float = 1f,
    val imageOffsetX: Float = 0f,
    val imageOffsetY: Float = 0f
) {
    /** O fundo só participa do agendamento quando está ativo e possui início válido. */
    fun isAvailableOn(date: String = todayIsoDate()): Boolean {
        if (!isActive || url.isBlank()) return false

        val start = normalizeDate(startDate) ?: return false
        val end = normalizeDate(endDate) ?: if (endDate.isNullOrBlank()) null else return false

        return date >= start && (end == null || date <= end)
    }

    fun hasStartedBy(date: String = todayIsoDate()): Boolean =
        normalizeDate(startDate)?.let { date >= it } ?: true

    fun hasEndedBefore(date: String = todayIsoDate()): Boolean =
        normalizeDate(endDate)?.let { date > it } ?: false

    companion object {
        private const val ISO_DATE_PATTERN = "yyyy-MM-dd"
        private const val DISPLAY_DATE_PATTERN = "dd/MM/yyyy"

        fun parseDate(value: String?): Date? {
            val normalized = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val parser = SimpleDateFormat(ISO_DATE_PATTERN, Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            val parsed = parser.parse(normalized, position)
            return parsed?.takeIf { position.index == normalized.length }
        }

        fun normalizeDate(value: String?): String? = parseDate(value)?.let { date ->
            SimpleDateFormat(ISO_DATE_PATTERN, Locale.US).format(date)
        }

        fun todayIsoDate(): String = SimpleDateFormat(ISO_DATE_PATTERN, Locale.US).format(Date())

        fun formatDisplayDate(value: String?): String? = parseDate(value)?.let { date ->
            SimpleDateFormat(DISPLAY_DATE_PATTERN, Locale("pt", "BR")).format(date)
        }
    }
}

val SupportedThemeKeys = listOf("multicolor", "red", "gold", "green", "blue", "orange")
