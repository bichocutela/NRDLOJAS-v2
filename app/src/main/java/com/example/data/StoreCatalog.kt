package com.example.data

/**
 * Nomes amigáveis para os códigos de filial usados pelo contrato de promoções.
 * O código continua sendo preservado para evitar ambiguidade e permitir novas unidades.
 */
object StoreCatalog {
    private val namesByCode = mapOf(
        "0001" to "Matriz Parnamirim",
        "0005" to "Petrópolis",
        "0006" to "Parnamirim",
        "0008" to "Alecrim",
        "0009" to "Lagoa Nova",
        "0012" to "Cidade Jardim",
        "0013" to "Santa Catarina",
        "0015" to "Parnamirim Centro",
        "0016" to "Igapó",
        "0031" to "Tirol",
        "0032" to "Parnamirim",
        "0033" to "SuperFácil Emaús",
        "0034" to "Nova Parnamirim",
        "0035" to "Pajuçara",
        "0036" to "Parnamirim",
        "0037" to "SuperFácil Natal",
        "0038" to "Capim Macio",
        "0039" to "Ponta Negra",
        "0040" to "SuperFácil João Pessoa",
        "0041" to "SuperFácil Olho d’Água",
        "0042" to "Mossoró",
        "0043" to "SuperFácil Mossoró",
        "0044" to "SuperFácil Vale do Sol",
        "0045" to "SuperFácil Nova Betânia",
        "0046" to "Mossoró"
    )

    fun nameFor(code: String): String {
        val normalized = code.trim().padStart(4, '0')
        return namesByCode[normalized] ?: "Loja $normalized"
    }

    fun labelFor(code: String): String {
        val normalized = code.trim().padStart(4, '0')
        if (normalized.isBlank() || normalized == "0000") return "Loja não informada"
        return "${nameFor(normalized)} ($normalized)"
    }
}
