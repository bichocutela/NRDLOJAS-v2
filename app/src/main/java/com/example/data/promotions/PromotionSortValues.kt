package com.example.data.promotions

/** Prices may include the effective unit suffix used by take/pay offers. */
internal fun promotionNumericPrice(value: String?): Double? {
    val raw = value?.trim()?.replace(Regex("(?i)^R\\$\\s*"), "")
        ?.replace(Regex("(?i)\\s*/\\s*(un|kg|l)\\s*$"), "")?.trim() ?: return null
    val normalized = if (raw.contains(',')) raw.replace(".", "").replace(',', '.') else raw
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

internal fun promotionDiscountPercent(regular: String?, current: String?, label: String?): Double? {
    val previous = promotionNumericPrice(regular)
    val price = promotionNumericPrice(current)
    if (previous != null && previous > 0 && price != null) {
        return ((previous - price) / previous * 100).coerceAtLeast(0.0)
    }
    return label?.takeIf { it.trim().endsWith("%") }?.removeSuffix("%")
        ?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }
}
