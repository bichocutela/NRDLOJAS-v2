package com.example.data

import java.text.Normalizer
import java.util.Locale

object ProductStandards {
    val officialCategories = listOf(
        "Açougue",
        "Cafeteria",
        "Frios",
        "Hortifruti",
        "Mercearia",
        "Padaria"
    )

    private val unitReplacements = mapOf(
        "QUILOGRAMA" to "KG",
        "KILOGRAMA" to "KG",
        "QUILO" to "KG",
        "KILO" to "KG",
        "GRAMA" to "G",
        "LITRO" to "L",
        "MILILITRO" to "ML",
        "UNIDADE" to "UN"
    )

    private val explicitUnitPattern = Regex(
        "(?<![\\p{L}\\p{N}_])(${unitReplacements.keys.joinToString("|")})(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE
    )

    fun isOfficialCategory(category: String): Boolean = category in officialCategories

    fun categoryFromSuggestion(value: String): String? {
        val normalizedSuggestion = categoryKey(value)
        return officialCategories.firstOrNull { categoryKey(it) == normalizedSuggestion }
    }

    fun normalizeProductName(name: String): String = explicitUnitPattern.replace(name) { match ->
        unitReplacements.getValue(match.value.uppercase(Locale.ROOT))
    }

    fun searchNameFrom(name: String): String = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")

    private fun categoryKey(value: String): String = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("\\s+"), "")
}
