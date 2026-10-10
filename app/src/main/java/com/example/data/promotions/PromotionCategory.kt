package com.example.data.promotions

import java.text.Normalizer
import java.util.Locale

/** Match the product noun, not ingredients later in the description. */
internal object PromotionCategory {
    private val rules = listOf(
        "Hortifruti" to "banana|maca|laranja|limao|uva|mamao|manga|melancia|melao|abacaxi|abacate|pera|tomate|batata|cebola|cenoura|alface|repolho|pepino|pimentao|alho|coentro|couve|brocolis|beterraba|inhame|macaxeira|mandioca|abobora",
        "Açougue e peixaria" to "carne|bovina|bovino|frango|file|picanha|alcatra|patinho|acem|musculo|costela|coxao|contra file|lombo|pernil|suino|suina|peixe|tilapia|salmao|camarao|sardinha fresca",
        "Frios e laticínios" to "leite|iogurte|queijo|requeijao|manteiga|margarina|presunto|mortadela|salame|bacon|linguica|salsicha|bebida lactea|creme de leite|leite condensado",
        "Padaria" to "pao|bolo|torta|croissant|rosca|sonho|salgado",
        "Congelados" to "sorvete|picole|pizza|lasanha|hamburguer|nuggets|polpa",
        "Bebidas" to "agua|refrigerante|suco|nectar|cerveja|vinho|whisky|vodka|cachaca|energetico|isotonico|cha pronto",
        "Higiene e beleza" to "sabonete|shampoo|xampu|condicionador|desodorante|creme dental|pasta dental|escova dental|papel higienico|absorvente|fralda|protetor solar|hidratante|enxaguante|perfume|algodao|lenco umedecido",
        "Limpeza" to "detergente|sabao|amaciante|desinfetante|agua sanitaria|alvejante|limpador|esponja|inseticida|saco lixo|lava roupas|lava loucas|alcool",
        "Pet" to "racao|alimento para caes|alimento para gatos|areia sanitaria|petisco para",
        "Mercearia" to "arroz|feijao|acucar|cafe|farinha|fuba|flocao|macarrao|massa|biscoito|bolacha|azeite|oleo|molho|extrato|sal|tempero|vinagre|maionese|ketchup|mostarda|chocolate|bombom|doce|geleia|cereal|aveia|granola|milho|ervilha|atum|sardinha|ovo|tapioca|pipoca|amendoim|castanha"
    ).map { (label, nouns) -> label to Regex("^(?:$nouns)(?:\\b|\\s)") }

    val categories = listOf("Alimentos", "Açougue", "Bazar", "Bebês e crianças", "Bebidas",
        "Bebidas alcoólicas", "Congelados", "Frios e embutidos", "Higiene e beleza", "Hortifruti",
        "Leites e laticínios", "Limpeza", "Padaria e confeitaria", "Pet shop", "Saudáveis", "Snacks", "Outras ofertas")

    fun resolve(description: String, officialOrFallback: String?, previous: String? = null): String {
        fun convert(value: String?): String? = when (value) {
            "Açougue e peixaria" -> "Açougue"
            "Frios e laticínios" -> if (Regex("(?i)presunto|mortadela|salame|bacon|linguiça|linguica|salsicha").containsMatchIn(description)) "Frios e embutidos" else "Leites e laticínios"
            "Padaria" -> "Padaria e confeitaria"
            "Pet" -> "Pet shop"
            "Mercearia" -> "Alimentos"
            else -> value?.takeIf { it in categories && it != "Outras ofertas" }
        }
        return convert(officialOrFallback) ?: convert(previous) ?: convert(forDescription(description)) ?: "Outras ofertas"
    }

    fun forDescription(description: String): String {
        val normalized = Normalizer.normalize(description, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ").trim()
        if (Regex("\\b(agua micelar|agua oxigenada|agua de colonia|demaquilante|dermocosmetico|protetor solar|leite de rosas|leite de colonia|locao corporal|tonico facial|serum facial|creme facial)\\b").containsMatchIn(normalized)) return "Higiene e beleza"
        if (Regex("\\b(agua sanitaria|alvejante|detergente|lava roupas|lava loucas|amaciante|desinfetante|limpador|inseticida|sabao)\\b").containsMatchIn(normalized)) return "Limpeza"
        return rules.firstOrNull { it.second.containsMatchIn(normalized) }?.first ?: "Outras ofertas"
    }
}

