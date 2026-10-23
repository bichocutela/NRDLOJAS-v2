package com.example.data

/** Fallback transcribed from the store leaflet supplied on 23/10/2026.
 * Live official hours always take precedence; no hours are inferred for other units. */
internal object NordestaoStoreHours {
    private val daily = setOf("lagoa nova", "cidade jardim", "santa catarina", "tirol", "nova parnamirim", "ponta negra")
    private val sundayShort = setOf("alecrim", "petropolis", "igapo", "igapo/sao goncalo", "igapo / sao goncalo", "rota do sol")
    fun fromLeaflet(name: String): String? {
        val key = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
            .removePrefix("nordestao ").trim()
        return when(key) {
            in daily -> "Segunda a domingo: 07h às 22h\nFeriados: 07h às 22h"
            in sundayShort -> "Segunda a sábado: 07h às 22h\nDomingos e feriados: 07h às 20h"
            else -> null
        }
    }
}
val NordestaoStore.displayHours: String get() = businessHours.takeIf { it.isNotBlank() }
    ?: NordestaoStoreHours.fromLeaflet(name).orEmpty()
val NordestaoStore.hoursFromLeaflet: Boolean get() = businessHours.isBlank() && NordestaoStoreHours.fromLeaflet(name) != null
