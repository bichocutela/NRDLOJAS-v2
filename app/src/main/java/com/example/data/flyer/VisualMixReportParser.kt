package com.example.data.flyer

/**
 * Parser dedicado ao relatório "Relatório de Produtos Alterados" do Visual Mix.
 *
 * Ele não tenta adivinhar o layout de um encarte visual. Só entra quando reconhece
 * o cabeçalho tabular conhecido e transforma linhas do relatório em ofertas para
 * revisão humana. O vínculo/publicação continua obedecendo o fluxo já existente.
 */
internal object VisualMixReportParser {
    private val reportHeader = Regex("(?i)Relat[oó]rio de Produtos Alterados")
    private val targetDateRegex = Regex("(?i)Produtos do dia\\s+(\\d{2})/(\\d{2})/(\\d{4})")
    private val rangeRegex = Regex("(\\d{1,2})/(\\d{1,2})\\s+a\\s+(\\d{1,2})/(\\d{1,2})")
    private val endOnlyRegex = Regex("-\\s*(\\d{1,2})/(\\d{1,2})(?!.*\\d)")
    private val moneyRegex = Regex("(?<!\\d)(\\d{1,4},\\d{2})(?!\\d)")
    private val detailRegex = Regex("^\\s*([0-9]+(?:/[0-9]+)?)\\s+([0-9]{7,14})\\s+(.+?)\\s+([VPC]+)\\s*$", RegexOption.IGNORE_CASE)
    private val codeOnlyRegex = Regex("^[0-9]+(?:/[0-9]+)?$")
    private val rowStartRegex = Regex("^[0-9]+(?:/[0-9]+)?\\s+[0-9]{7,14}\\b")
    private val rowIndexRegex = Regex("^[0-9]{1,3}/[0-9]{1,3}$")
    private val rowEndRegex = Regex("\\b[VPC]{1,3}\\s*$", RegexOption.IGNORE_CASE)

    fun parse(sourceName: String, rawText: String): FlyerParseDraft? {
        if (!reportHeader.containsMatchIn(rawText)) return null

        val physicalLines = rawText.lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .toList()
        val lines = rebuildLogicalLines(physicalLines)

        val targetDate = targetDateRegex.find(rawText)?.let { match ->
            iso(match.groupValues[3].toInt(), match.groupValues[2].toInt(), match.groupValues[1].toInt())
        }

        val offers = mutableListOf<FlyerOffer>()
        var pendingCode: String? = null

        for (line in lines) {
            if (line.startsWith("Código Código Automação", ignoreCase = true)) continue
            if (line.startsWith("Mercad:", ignoreCase = true)) continue
            if (line.endsWith("Produtos", ignoreCase = true)) continue

            if (codeOnlyRegex.matches(line) && !moneyRegex.containsMatchIn(line)) {
                val candidate = line.substringBefore('/')
                pendingCode = candidate.takeIf { it.length >= 4 }
                continue
            }

            val match = detailRegex.matchEntire(line) ?: continue
            val rowCodeToken = match.groupValues[1]
            val ean = match.groupValues[2].filter(Char::isDigit)
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
                val year = targetDate?.substring(0, 4)?.toIntOrNull() ?: 2000
                iso(year, r.groupValues[2].toInt(), r.groupValues[1].toInt()) to
                    iso(year, r.groupValues[4].toInt(), r.groupValues[3].toInt())
            }
            val clubEnd = endOnlyRegex.find(body)?.let { r ->
                val year = targetDate?.substring(0, 4)?.toIntOrNull() ?: 2000
                iso(year, r.groupValues[2].toInt(), r.groupValues[1].toInt())
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

            val inlineCode = rowCodeToken.substringBefore('/').takeIf { it.length >= 4 }
            val productCode = pendingCode ?: inlineCode
            val normalized = normalizeText(description)
            val common = FlyerOffer(
                type = FlyerOfferType.FLYER_PRICE,
                scope = FlyerOfferScope.PRODUCT,
                sourceDescription = description,
                detail = buildString {
                    append("Visual Mix")
                    promoRange?.let { append(" • promoção ").append(it.first).append(" a ").append(it.second) }
                    if (clubEnd != null) append(" • clube até ").append(clubEnd)
                },
                page = 1,
                confidence = 0.98,
                matchStatus = FlyerMatchStatus.REVIEW,
                productCodes = listOfNotNull(productCode),
                barcodes = listOf(ean),
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
        val validFrom = dateCandidates.minOrNull() ?: targetDate
        val validTo = dateCandidates.maxOrNull() ?: targetDate

        return FlyerParseDraft(
            name = sourceName.ifBlank { "Relatório Visual Mix" },
            validFrom = validFrom,
            validTo = validTo,
            offers = offers.distinctBy { "${it.type}|${it.barcodes.firstOrNull()}|${it.flyerPrice}|${it.clubCondition}" },
            warnings = listOf("Relatório Visual Mix reconhecido. Confira a vigência individual exibida em cada oferta antes de publicar.")
        )
    }

    /**
     * PdfRenderer/ML Kit e alguns extratores de texto podem dividir uma linha da tabela
     * em vários pedaços. Aqui remontamos somente linhas que parecem registros de produto,
     * sem concatenar cabeçalhos ou códigos internos isolados.
     */
    private fun rebuildLogicalLines(lines: List<String>): List<String> {
        val rebuilt = mutableListOf<String>()
        var buffer: StringBuilder? = null

        fun flushBuffer() {
            buffer?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(rebuilt::add)
            buffer = null
        }

        for (line in lines) {
            val startsCompleteRow = rowStartRegex.containsMatchIn(line)
            val startsSplitRow = rowIndexRegex.matches(line)

            val current = buffer
            if (current != null) {
                if (startsCompleteRow || startsSplitRow) {
                    flushBuffer()
                    buffer = StringBuilder(line)
                    if (rowEndRegex.containsMatchIn(line) && moneyRegex.containsMatchIn(line)) flushBuffer()
                } else {
                    current.append(' ').append(line)
                    if (rowEndRegex.containsMatchIn(line) && moneyRegex.containsMatchIn(current.toString())) flushBuffer()
                }
                continue
            }

            val isLongStandaloneCode = codeOnlyRegex.matches(line) && line.substringBefore('/').length >= 4
            if (isLongStandaloneCode) {
                rebuilt += line
                continue
            }

            if (startsCompleteRow || startsSplitRow) {
                buffer = StringBuilder(line)
                if (rowEndRegex.containsMatchIn(line) && moneyRegex.containsMatchIn(line)) flushBuffer()
                continue
            }

            rebuilt += line
        }
        flushBuffer()
        return rebuilt
    }

    private fun iso(year: Int, month: Int, day: Int): String? {
        if (year !in 2000..2100 || month !in 1..12 || day !in 1..31) return null
        val leap = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
        val maxDay = when (month) {
            2 -> if (leap) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        if (day > maxDay) return null
        return "%04d-%02d-%02d".format(year, month, day)
    }
}
