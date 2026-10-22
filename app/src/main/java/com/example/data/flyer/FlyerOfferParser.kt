package com.example.data.flyer

import kotlin.math.abs
import kotlin.math.hypot

internal data class FlyerTextBlock(
    val page: Int,
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val pageWidth: Int,
    val pageHeight: Int
) {
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
}

internal data class FlyerParseDraft(
    val name: String,
    val validFrom: String?,
    val validTo: String?,
    val offers: List<FlyerOffer>,
    val warnings: List<String>
)

internal object FlyerOfferParser {
    private val secondUnitRegex = Regex(
        "(?i)(\\d{1,3}(?:[.,]\\d+)?)\\s*%\\s*(?:de\\s*)?(?:desconto\\s*)?(?:na\\s*)?(?:segunda|2\\s*[ªa]?)\\s*unidade"
    )
    private val takePayMeasureRegex = Regex(
        "(?i)leve\\s+([0-9]+(?:[.,][0-9]+)?)\\s*(ml|l|litro(?:s)?|g|kg)\\s*[,;:/-]*\\s*pague\\s+([0-9]+(?:[.,][0-9]+)?)\\s*(ml|l|litro(?:s)?|g|kg)"
    )
    private val takePayQuantityRegex = Regex(
        "(?i)leve\\s+([0-9]+(?:[.,][0-9]+)?)\\s*(?:un(?:idade)?s?)?\\s*[,;:/-]*\\s*pague\\s+([0-9]+(?:[.,][0-9]+)?)"
    )
    private val cashbackPercentRegex = Regex(
        "(?i)(\\d{1,3}(?:[.,]\\d+)?)\\s*%\\s*(?:de\\s*)?(?:cashback|volta|retorno)"
    )
    private val cashbackMoneyRegex = Regex(
        "(?i)(?:cashback|de\\s+volta|retorno)[^0-9]{0,12}R?\\$?\\s*([0-9]+(?:[.,][0-9]{2}))"
    )
    private val dePorRegex = Regex(
        "(?i)de\\s+R?\\$?\\s*([0-9]+(?:[.,][0-9]{2}))\\s+(?:por|a)\\s+R?\\$?\\s*([0-9]+(?:[.,][0-9]{2}))"
    )
    private val explicitPriceRegex = Regex(
        "(?i)(?:R\\$\\s*)?([0-9]{1,4}[,.][0-9]{2})\\s*(?:cada|kg|un\\.?|unidade)?"
    )
    private val validityRegex = Regex(
        "(?i)(?:ofertas?\\s+v[aá]lidas?\\s+)?(?:de\\s+)?(\\d{1,2})(?:[./-](\\d{1,2})(?:[./-](\\d{2,4}))?)?\\s*(?:a|at[eé]|-)\\s*(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})"
    )
    private val fullValidityRegex = Regex(
        "(?i)(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})\\s*(?:a|at[eé]|-)\\s*(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})"
    )
    private val namedMonthValidityRegex = Regex(
        "(?i)(?:ofertas?\\s+v[aá]lidas?\\s+)?(?:de\\s+)?(\\d{1,2})\\s*(?:a|at[eé]|-)\\s*(\\d{1,2})\\s+de\\s+(janeiro|fevereiro|mar[cç]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\\s+de\\s+(\\d{4})"
    )

    fun parse(sourceName: String, blocks: List<FlyerTextBlock>): FlyerParseDraft {
        val cleaned = blocks.filter { it.text.isNotBlank() }
        val allText = cleaned.joinToString(" ") { it.text.replace('\n', ' ') }
        val validity = parseValidity(allText)
        val warnings = mutableListOf<String>()
        if (validity == null) warnings += "A vigência não foi identificada automaticamente. Confira as datas antes de salvar."

        val offers = mutableListOf<FlyerOffer>()
        cleaned.forEach blockLoop@{ block ->
            val compactText = block.text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
            if (isClubText(compactText) || nearClubBadge(block, cleaned)) return@blockLoop

            secondUnitRegex.findAll(compactText).forEach secondLoop@{ match ->
                val percent = decimal(match.groupValues[1]) ?: return@secondLoop
                if (percent !in 1.0..100.0) return@secondLoop
                val description = nearestDescription(block, cleaned)
                offers += baseOffer(
                    type = FlyerOfferType.SECOND_UNIT_PERCENT,
                    block = block,
                    description = description,
                    detail = "${formatQuantity(percent)}% de desconto na segunda unidade.",
                    secondUnitDiscountPercent = percent
                )
            }

            takePayMeasureRegex.findAll(compactText).forEach measureLoop@{ match ->
                val take = decimal(match.groupValues[1]) ?: return@measureLoop
                val pay = decimal(match.groupValues[3]) ?: return@measureLoop
                val takeUnit = normalizeMeasureUnit(match.groupValues[2])
                val payUnit = normalizeMeasureUnit(match.groupValues[4])
                val description = nearestDescription(block, cleaned)
                offers += baseOffer(
                    type = FlyerOfferType.TAKE_PAY_MEASURE,
                    block = block,
                    description = description,
                    detail = "Leve ${formatQuantity(take)} $takeUnit e pague ${formatQuantity(pay)} $payUnit.",
                    takeQuantity = take,
                    payQuantity = pay,
                    takeUnit = takeUnit,
                    payUnit = payUnit
                )
            }

            if (!takePayMeasureRegex.containsMatchIn(compactText)) {
                takePayQuantityRegex.findAll(compactText).forEach quantityLoop@{ match ->
                    val take = decimal(match.groupValues[1]) ?: return@quantityLoop
                    val pay = decimal(match.groupValues[2]) ?: return@quantityLoop
                    if (take <= pay || pay <= 0.0 || take > 200.0) return@quantityLoop
                    val description = nearestDescription(block, cleaned)
                    offers += baseOffer(
                        type = FlyerOfferType.TAKE_PAY_QUANTITY,
                        block = block,
                        description = description,
                        detail = "Leve ${formatQuantity(take)}, pague ${formatQuantity(pay)}.",
                        takeQuantity = take,
                        payQuantity = pay
                    )
                }
            }

            cashbackPercentRegex.findAll(compactText).forEach cashbackLoop@{ match ->
                val percent = decimal(match.groupValues[1]) ?: return@cashbackLoop
                if (percent !in 1.0..100.0) return@cashbackLoop
                val description = nearestDescription(block, cleaned)
                offers += baseOffer(
                    type = FlyerOfferType.CASHBACK,
                    block = block,
                    description = description,
                    detail = "${formatQuantity(percent)}% de cashback/retorno conforme o encarte.",
                    cashbackPercent = percent
                )
            }

            cashbackMoneyRegex.findAll(compactText).forEach cashbackValueLoop@{ match ->
                val value = decimal(match.groupValues[1]) ?: return@cashbackValueLoop
                val description = nearestDescription(block, cleaned)
                offers += baseOffer(
                    type = FlyerOfferType.CASHBACK,
                    block = block,
                    description = description,
                    detail = "${formatMoney(value)} de cashback/retorno conforme o encarte.",
                    cashbackValue = value
                )
            }

            dePorRegex.findAll(compactText).forEach dePorLoop@{ match ->
                val regular = decimal(match.groupValues[1]) ?: return@dePorLoop
                val offer = decimal(match.groupValues[2]) ?: return@dePorLoop
                if (regular <= offer || offer <= 0.0) return@dePorLoop
                val description = nearestDescription(block, cleaned)
                offers += baseOffer(
                    type = FlyerOfferType.DE_POR,
                    block = block,
                    description = description,
                    detail = "De ${formatMoney(regular)} por ${formatMoney(offer)}.",
                    regularPrice = regular,
                    flyerPrice = offer
                )
            }
        }

        // Preço normal e Clube continuam sendo responsabilidade da ACP. Não criamos uma
        // oferta apenas porque o OCR encontrou uma etiqueta de preço no encarte.
        val deduped = offers
            .filterNot { isClubText(it.sourceDescription) }
            .distinctBy { offer ->
                listOf(
                    offer.page,
                    offer.type,
                    normalizeText(offer.sourceDescription),
                    offer.secondUnitDiscountPercent,
                    offer.takeQuantity,
                    offer.payQuantity,
                    offer.flyerPrice,
                    offer.cashbackPercent,
                    offer.cashbackValue
                ).joinToString("|")
            }

        return FlyerParseDraft(
            name = suggestedName(sourceName, validity),
            validFrom = validity?.first,
            validTo = validity?.second,
            offers = deduped,
            warnings = warnings
        )
    }

    private fun baseOffer(
        type: FlyerOfferType,
        block: FlyerTextBlock,
        description: String,
        detail: String,
        baseConfidence: Double = 0.80,
        regularPrice: Double? = null,
        flyerPrice: Double? = null,
        secondUnitDiscountPercent: Double? = null,
        takeQuantity: Double? = null,
        payQuantity: Double? = null,
        takeUnit: String? = null,
        payUnit: String? = null,
        cashbackPercent: Double? = null,
        cashbackValue: Double? = null
    ): FlyerOffer {
        val normalized = normalizeText(description)
        val group = Regex("\\b(selecao|todos|todas|diversos|diversas|sabores|mesma marca|mesmo rotulo)\\b")
            .containsMatchIn(normalized)
        val hasDescription = normalized.length >= 4
        val confidence = when {
            !hasDescription -> 0.35
            group -> baseConfidence.coerceAtMost(0.72)
            else -> baseConfidence
        }
        return FlyerOffer(
            type = type,
            scope = if (group) FlyerOfferScope.GROUP else FlyerOfferScope.PRODUCT,
            sourceDescription = description.ifBlank { block.text.trim() },
            sourceText = block.text.take(3000),
            detail = detail,
            page = block.page,
            confidence = confidence,
            matchStatus = if (group || confidence < 0.75) FlyerMatchStatus.REVIEW else FlyerMatchStatus.UNRESOLVED,
            matchTerms = normalized.split(' ').filter { it.length >= 3 }.distinct(),
            regularPrice = regularPrice,
            flyerPrice = flyerPrice,
            secondUnitDiscountPercent = secondUnitDiscountPercent,
            takeQuantity = takeQuantity,
            payQuantity = payQuantity,
            takeUnit = takeUnit,
            payUnit = payUnit,
            cashbackPercent = cashbackPercent,
            cashbackValue = cashbackValue
        )
    }

    private fun nearestDescription(anchor: FlyerTextBlock, blocks: List<FlyerTextBlock>): String {
        val candidates = blocks.asSequence()
            .filter { it.page == anchor.page && it !== anchor }
            .filter { isPossibleDescription(it.text) }
            .map { candidate -> candidate to descriptionDistance(anchor, candidate) }
            .filter { (_, score) -> score < 1.15 }
            .sortedBy { it.second }
            .take(3)
            .toList()
        if (candidates.isEmpty()) return ""

        return candidates.minByOrNull { (candidate, score) ->
            val belowPenalty = if (candidate.centerY > anchor.centerY + anchor.pageHeight * 0.06) 0.35 else 0.0
            score + belowPenalty
        }?.first?.text
            ?.replace('\n', ' ')
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }

    private fun descriptionDistance(anchor: FlyerTextBlock, candidate: FlyerTextBlock): Double {
        val dx = abs(anchor.centerX - candidate.centerX) / anchor.pageWidth.coerceAtLeast(1)
        val dy = abs(anchor.centerY - candidate.centerY) / anchor.pageHeight.coerceAtLeast(1)
        val horizontalOverlap = minOf(anchor.right, candidate.right) - maxOf(anchor.left, candidate.left)
        val overlapBonus = if (horizontalOverlap > 0) 0.20 else 0.0
        return hypot(dx * 1.25, dy * 2.4) - overlapBonus
    }

    private fun nearClubBadge(anchor: FlyerTextBlock, blocks: List<FlyerTextBlock>): Boolean = blocks.any { candidate ->
        candidate.page == anchor.page && candidate !== anchor && isClubText(candidate.text) &&
            descriptionDistance(anchor, candidate) < 0.16
    }

    private fun isPossibleDescription(raw: String): Boolean {
        val text = raw.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val normalized = normalizeText(text)
        if (normalized.length < 4 || normalized.length > 180) return false
        if (isClubText(text) || containsCommercialRule(text)) return false
        if (validityRegex.containsMatchIn(text) || fullValidityRegex.containsMatchIn(text) || namedMonthValidityRegex.containsMatchIn(text)) return false
        if (Regex("(?i)\\b(r\\$|cada|kg)\\b").containsMatchIn(text) && explicitPriceRegex.containsMatchIn(text)) return false
        if (normalized in setOf("baixe o app", "peca agora", "aniversario premiado", "perfeito")) return false
        return normalized.count(Char::isLetter) >= 4
    }

    private fun containsCommercialRule(text: String): Boolean =
        secondUnitRegex.containsMatchIn(text) ||
            takePayMeasureRegex.containsMatchIn(text) ||
            takePayQuantityRegex.containsMatchIn(text) ||
            cashbackPercentRegex.containsMatchIn(text) ||
            cashbackMoneyRegex.containsMatchIn(text) ||
            dePorRegex.containsMatchIn(text)

    private fun isClubText(text: String): Boolean {
        val normalized = normalizeText(text)
        return "clube de vantagens" in normalized || "clube vantagens" in normalized ||
            ("cpf" in normalized && ("desconto" in normalized || "oferta" in normalized))
    }

    private fun parseValidity(text: String): Pair<String, String>? {
        fullValidityRegex.find(text)?.let { match ->
            validRange(
                date(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
                date(match.groupValues[4], match.groupValues[5], match.groupValues[6])
            )?.let { return it }
        }
        validityRegex.find(text)?.let { match ->
            val endDay = match.groupValues[4]
            val endMonth = match.groupValues[5]
            val endYear = match.groupValues[6]
            val startMonth = match.groupValues[2].ifBlank { endMonth }
            val startYear = match.groupValues[3].ifBlank { endYear }
            validRange(
                date(match.groupValues[1], startMonth, startYear),
                date(endDay, endMonth, endYear)
            )?.let { return it }
        }
        namedMonthValidityRegex.find(text)?.let { match ->
            val month = monthNumber(match.groupValues[3]) ?: return@let
            validRange(
                date(match.groupValues[1], month.toString(), match.groupValues[4]),
                date(match.groupValues[2], month.toString(), match.groupValues[4])
            )?.let { return it }
        }
        return null
    }

    private fun validRange(start: String?, end: String?): Pair<String, String>? {
        val startDate = parseIsoDate(start) ?: return null
        val endDate = parseIsoDate(end) ?: return null
        return if (endDate.before(startDate)) null else start!! to end!!
    }

    private fun date(day: String, month: String, year: String): String? {
        val normalizedYear = when (year.length) {
            2 -> 2000 + year.toIntOrNull().orZero()
            4 -> year.toIntOrNull() ?: return null
            else -> return null
        }
        val dayValue = day.toIntOrNull() ?: return null
        val monthValue = month.toIntOrNull() ?: return null
        val iso = "%04d-%02d-%02d".format(normalizedYear, monthValue, dayValue)
        return iso.takeIf { parseIsoDate(it) != null }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun monthNumber(raw: String): Int? = when (normalizeText(raw)) {
        "janeiro" -> 1
        "fevereiro" -> 2
        "marco" -> 3
        "abril" -> 4
        "maio" -> 5
        "junho" -> 6
        "julho" -> 7
        "agosto" -> 8
        "setembro" -> 9
        "outubro" -> 10
        "novembro" -> 11
        "dezembro" -> 12
        else -> null
    }

    private fun suggestedName(sourceName: String, validity: Pair<String, String>?): String {
        val base = sourceName.substringAfterLast('/').substringBefore('?')
            .replace(Regex("(?i)\\.(pdf|png|jpe?g|webp)$"), "")
            .replace(Regex("[-_]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("\\s+\\d{4,}$"), "")
            .trim()
            .takeIf { it.length >= 3 && !it.all(Char::isDigit) }
        if (!base.isNullOrBlank()) return base
        return validity?.let { (start, end) ->
            val s = formatIsoDate(start, "dd/MM")
            val e = formatIsoDate(end, "dd/MM")
            if (s != null && e != null) "Encarte $s a $e" else null
        } ?: "Novo encarte"
    }

    private fun normalizeMeasureUnit(raw: String): String = when (normalizeText(raw)) {
        "l", "litro", "litros" -> "L"
        "ml" -> "ml"
        "kg" -> "kg"
        "g" -> "g"
        else -> raw.trim()
    }

    private fun decimal(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()
}
