package com.example.data

/**
 * Store scale labels confirmed in NRD: 2 + six-digit PLU starting with 25.
 * Accept only complete EAN-13 or 20-digit extended labels, never partial input.
 * Weight/price fields belong to the package and are not product identifiers.
 */
internal fun storeProductLookupCode(value: String): String {
    val clean = value.trim()
    if (clean.length != 13 && clean.length != 20) return clean
    if (!clean.startsWith("225") || clean.any { it !in '0'..'9' }) return clean
    if (clean.length == 13) {
        val sum = clean.take(12).mapIndexed { index, digit ->
            (digit - '0') * if (index % 2 == 0) 1 else 3
        }.sum()
        if ((10 - sum % 10) % 10 != clean.last() - '0') return clean
    }
    return clean.substring(1, 7)
}
