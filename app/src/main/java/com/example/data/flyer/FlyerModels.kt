package com.example.data.flyer

import com.example.data.Product
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class FlyerOfferType {
    SECOND_UNIT_PERCENT,
    TAKE_PAY_QUANTITY,
    TAKE_PAY_MEASURE,
    DE_POR,
    CASHBACK,
    FLYER_PRICE
}

enum class FlyerOfferScope { PRODUCT, GROUP }

enum class FlyerMatchStatus { CONFIRMED, REVIEW, UNRESOLVED }

enum class FlyerCampaignStatus { SCHEDULED, ACTIVE, EXPIRED, DISABLED }

data class FlyerOffer(
    val id: String = UUID.randomUUID().toString(),
    val type: FlyerOfferType,
    val scope: FlyerOfferScope = FlyerOfferScope.PRODUCT,
    val sourceDescription: String,
    val detail: String = "",
    val page: Int = 1,
    val confidence: Double = 0.0,
    val matchStatus: FlyerMatchStatus = FlyerMatchStatus.UNRESOLVED,
    val productCodes: List<String> = emptyList(),
    val barcodes: List<String> = emptyList(),
    val matchedProductName: String? = null,
    val matchTerms: List<String> = emptyList(),
    val flyerPrice: Double? = null,
    val regularPrice: Double? = null,
    val secondUnitDiscountPercent: Double? = null,
    val secondUnitPrice: Double? = null,
    val equivalentUnitPrice: Double? = null,
    val takeQuantity: Double? = null,
    val payQuantity: Double? = null,
    val takeUnit: String? = null,
    val payUnit: String? = null,
    val cashbackPercent: Double? = null,
    val cashbackValue: Double? = null
) {
    fun matches(product: Product): Boolean {
        if (matchStatus != FlyerMatchStatus.CONFIRMED) return false
        val productCode = normalizeIdentifier(product.code)
        if (productCodes.any { normalizeIdentifier(it) == productCode }) return true
        if (barcodes.any { normalizeIdentifier(it) == productCode }) return true

        if (scope != FlyerOfferScope.PRODUCT || confidence < 0.92) return false
        val expected = normalizeText(matchedProductName ?: sourceDescription)
        val actual = normalizeText(product.name)
        if (expected.isBlank() || actual.isBlank()) return false
        if (expected == actual) return true
        return tokenSimilarity(expected, actual) >= 0.90
    }

    fun displayTitle(): String = when (type) {
        FlyerOfferType.SECOND_UNIT_PERCENT -> secondUnitDiscountPercent?.let { "${formatQuantity(it)}% NA 2ª UNIDADE" } ?: "2ª UNIDADE"
        FlyerOfferType.TAKE_PAY_QUANTITY -> "LEVE ${formatQuantity(takeQuantity)} • PAGUE ${formatQuantity(payQuantity)}"
        FlyerOfferType.TAKE_PAY_MEASURE -> "LEVE ${formatQuantity(takeQuantity)} ${takeUnit.orEmpty()} • PAGUE ${formatQuantity(payQuantity)} ${payUnit.orEmpty()}"
        FlyerOfferType.DE_POR -> "OFERTA DE / POR"
        FlyerOfferType.CASHBACK -> cashbackPercent?.let { "${formatQuantity(it)}% DE VOLTA" }
            ?: cashbackValue?.let { "${formatMoney(it)} DE VOLTA" }
            ?: "CASHBACK"
        FlyerOfferType.FLYER_PRICE -> "PREÇO DO ENCARTE"
    }
}

data class FlyerCampaign(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceType: String,
    val sourceLabel: String,
    val validFrom: String,
    val validTo: String,
    val createdAt: Long = System.currentTimeMillis(),
    val enabled: Boolean = true,
    val offers: List<FlyerOffer> = emptyList()
) {
    fun statusAt(nowMillis: Long = System.currentTimeMillis()): FlyerCampaignStatus {
        if (!enabled) return FlyerCampaignStatus.DISABLED
        val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val start = parseIsoDate(validFrom) ?: return FlyerCampaignStatus.DISABLED
        val end = parseIsoDate(validTo) ?: return FlyerCampaignStatus.DISABLED
        return when {
            today.isBefore(start) -> FlyerCampaignStatus.SCHEDULED
            today.isAfter(end) -> FlyerCampaignStatus.EXPIRED
            else -> FlyerCampaignStatus.ACTIVE
        }
    }

    fun isActiveAt(nowMillis: Long = System.currentTimeMillis()): Boolean =
        statusAt(nowMillis) == FlyerCampaignStatus.ACTIVE
}

internal fun calculateSecondUnit(basePrice: Double, discountPercent: Double): Pair<Double, Double>? {
    if (basePrice <= 0.0 || discountPercent <= 0.0 || discountPercent > 100.0) return null
    val base = BigDecimal.valueOf(basePrice)
    val discount = BigDecimal.valueOf(discountPercent)
    val second = base.multiply(BigDecimal(100).subtract(discount))
        .divide(BigDecimal(100), 6, RoundingMode.HALF_UP)
    val average = base.add(second).divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
    return second.setScale(2, RoundingMode.HALF_UP).toDouble() to average.toDouble()
}

internal fun calculateTakePayAverage(basePrice: Double, take: Double, pay: Double): Double? {
    if (basePrice <= 0.0 || take <= 0.0 || pay <= 0.0 || take <= pay) return null
    val takeValue = BigDecimal.valueOf(take)
    val payValue = BigDecimal.valueOf(pay)
    return BigDecimal.valueOf(basePrice)
        .multiply(payValue)
        .divide(takeValue, 2, RoundingMode.HALF_UP)
        .toDouble()
}

internal fun parseIsoDate(value: String?): LocalDate? = runCatching {
    value?.trim()?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
}.getOrNull()

internal fun formatMoney(value: Double): String = "R$ " +
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

internal fun formatQuantity(value: Double?): String = value?.let {
    BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.', ',')
}.orEmpty()

internal fun normalizeText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

internal fun normalizeIdentifier(value: String): String = value.filter(Char::isLetterOrDigit).lowercase()

internal fun tokenSimilarity(left: String, right: String): Double {
    val a = normalizeText(left).split(' ').filter { it.length >= 2 }.toSet()
    val b = normalizeText(right).split(' ').filter { it.length >= 2 }.toSet()
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val intersection = a.intersect(b).size.toDouble()
    val union = a.union(b).size.toDouble()
    return if (union == 0.0) 0.0 else intersection / union
}
