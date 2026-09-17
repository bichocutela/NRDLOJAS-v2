package com.example.data.flyer

import java.util.Calendar

/**
 * Parser dedicado ao relatório "Relatório de Produtos Alterados" do Visual Mix.
 * Só entra quando reconhece o cabeçalho tabular conhecido e transforma as linhas
 * atuais do relatório em ofertas para revisão humana.
 */
internal object VisualMixReportParser {
    private val reportHeader = Regex("(?i)Relat[oó]rio de Produtos Alterados")
    private val targetDateRegex = Regex("(?i)Produtos do dia\\s+(\\d{2})/(\\d{2})/(\\d{4})")
    private val rangeRegex = Regex("(\\d{1,2})/(\\d{1,2})\\s+a\\s+(\\d{1,2})/(\\d{1,2})")
    private val endOnlyRegex = Regex("-\\s*(\\d{1,2})/(\\d{1,2})(?!.*\\d)")
    private val moneyRegex = Regex("(?<!\\d)(\\d{1,4},\\d{2})(?!\\d)")
    private val detailRegex = Regex("^\\s*([0-9]+(?:/[0-9]+)?)\\s+([0-9]{7,14})\\s+(.+?)\\s+([VPC]+)\\s*$", RegexOption.IGNORE_CASE)
    private val codeOnlyRegex = Regex("^[0-9]+(?:/[0-9]+)?$")

    fun parse(sourceName: String, rawText: String): FlyerParseDraft? {
        if (!reportHeader.containsMatchIn(rawText)) return null

        val lines = rawText.lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .toList()

        val targetDate = targetDateRegex.find(rawText)?.let { match ->
            iso(match.groupValues[3].toInt(), match.groupValues[2].toInt(), match.groupValues[1].toInt())
        }
        val referenceYear = targetDate?.substring(0, 4)?.toIntOrNull() ?: currentYear()

        val offers = mutableListOf<FlyerOffer>()
        var pendingCode: String? = null

        for (line in lines) {
            if (line.startsWith("Código Código Automação", ignoreCase = true)) continue
            if (line.startsWith("Mercad:", ignoreCase = true)) continue
            if (line.endsWith("Produtos", ignoreCase = true)) continue

            if (codeOnlyRegex.matches(line) && !moneyRegex.containsMatchIn(line)) {
                pendingCode = line.substringBefore('/')
                continue
            }

            val match = detailRegex.matchEntire(line) ?: continue
            val inlineCode = match.groupValues[1].substringBefore('/')
            val automationCode = match.groupValues[2].filter(Char::isDigit)
            val flags = match.groupValues[4].uppercase()
            if ('P' !in flags && 'C' !in flags) {
                pendingCode = null
                continue
            }

            val body = match.groupValues[3]
            val firstMoney = moneyRegex.find(body)?.range?.first ?: continue
            val description = body.substring(0, firstMoney).trim().trimEnd('.')
            if (description.length < 3) continue

            val prices = moneyRegex.findAll(body)
                .mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }
                .toList()
            if (prices.size < 2) continue

            val ranges = rangeRegex.findAll(body).toList()
            val promoRange = ranges.lastOrNull()?.let { r ->
                val start = iso(referenceYear, r.groupValues[2].toInt(), r.groupValues[1].toInt())
                val end = iso(referenceYear, r.groupValues[4].toInt(), r.groupValues[3].toInt())
                if (start != null && end != null) start to end else null
            }
            val clubEnd = endOnlyRegex.find(body)?.let { r ->
                iso(referenceYear, r.groupValues[2].toInt(), r.groupValues[1].toInt())
            }

            val regularPrice: Double
            val promoPrice: Double?
            val clubPrice: Double?
            when {
                'P' in flags && 'C' in flags && prices.size >= 3 -> {
                    regularPrice = prices[prices.lastIndex - 2]
                    promoPrice = prices[prices.lastIndex - 1]
                    clubPrice = prices.last()
                }
                'P' in flags -> {
                    regularPrice = prices[prices.lastIndex - 1]
                    promoPrice = prices.last()
                    clubPrice = null
                }
                else -> {
                    regularPrice = prices[prices.lastIndex - 1]
                    promoPrice = null
                    clubPrice = prices.last()
                }
            }

            val productCode = pendingCode?.takeIf { it.isNotBlank() } ?: inlineCode
            val normalized = normalizeText(description)
            val common = FlyerOffer(
                type = FlyerOfferType.FLYER_PRICE,
                scope = FlyerOfferScope.PRODUCT,
                sourceDescription = description,
                detail = "Visual Mix",
                page = 1,
                confidence = 0.98,
                matchStatus = FlyerMatchStatus.REVIEW,
                productCodes = listOfNotNull(productCode.takeIf { it.isNotBlank() }),
                barcodes = listOf(automationCode),
                matchTerms = normalized.split(' ').filter { it.length >= 3 }.distinct(),
                regularPrice = regularPrice,
                sourceText = line
            )

            if (promoPrice != null && promoPrice > 0.0 && promoPrice < regularPrice) {
                offers += common.copy(
                    type = FlyerOfferType.DE_POR,
                    flyerPrice = promoPrice,
                    detail = "De ${formatMoney(regularPrice)} por ${formatMoney(promoPrice)}" +
                        promoRange?.let { " • ${it.first} a ${it.second}" }.orEmpty()
                )
            }
            if (clubPrice != null && clubPrice > 0.0 && clubPrice < regularPrice) {
                offers += common.copy(
                    type = FlyerOfferType.FLYER_PRICE,
                    flyerPrice = clubPrice,
                    clubCondition = FlyerClubCondition.REQUIRED,
                    detail = "Clube ${formatMoney(clubPrice)}" +
                        clubEnd?.let { " • até $it" }.orEmpty()
                )
            }

            pendingCode = null
        }

        if (offers.isEmpty()) return FlyerParseDraft(
            name = sourceName.ifBlank { "Relatório Visual Mix" },
            validFrom = targetDate,
            validTo = targetDate,
            offers = emptyList(),
            warnings = listOf("O relatório Visual Mix foi reconhecido, mas nenhuma promoção atual foi estruturada automaticamente.")
        )

        val dateCandidates = mutableListOf<String>()
        targetDate?.let(dateCandidates::add)
        for (offer in offers) {
            Regex("\\d{4}-\\d{2}-\\d{2}").findAll(offer.detail).forEach { dateCandidates += it.value }
        }

        return FlyerParseDraft(
            name = sourceName.ifBlank { "Relatório Visual Mix" },
            validFrom = dateCandidates.minOrNull() ?: targetDate,
            validTo = dateCandidates.maxOrNull() ?: targetDate,
            offers = offers.distinctBy { "${it.type}|${it.barcodes.firstOrNull()}|${it.flyerPrice}|${it.clubCondition}" },
            warnings = listOf("Relatório Visual Mix reconhecido. Confira a vigência individual exibida em cada oferta antes de publicar.")
        )
    }

    private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    private fun iso(year: Int, month: Int, day: Int): String? {
        val value = "%04d-%02d-%02d".format(year, month, day)
        return value.takeIf { parseIsoDate(it) != null }
    }
}
