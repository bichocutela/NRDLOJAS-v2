package com.example.ui

internal fun isNoveltyVersionEligible(
    installedVersion: String,
    target: String?,
    referenceVersion: String?
): Boolean = when (target) {
    "new" -> installedVersion == referenceVersion
    "previous" -> referenceVersion?.takeIf(String::isNotBlank)
        ?.let { compareNumericVersions(installedVersion, it) < 0 } ?: false
    else -> true
}

private fun compareNumericVersions(left: String, right: String): Int {
    fun parts(value: String): List<java.math.BigInteger> =
        Regex("\\d+").findAll(value).map { it.value.toBigInteger() }.toList()

    val leftParts = parts(left)
    val rightParts = parts(right)
    for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
        val comparison = (leftParts.getOrNull(index) ?: java.math.BigInteger.ZERO)
            .compareTo(rightParts.getOrNull(index) ?: java.math.BigInteger.ZERO)
        if (comparison != 0) return comparison
    }
    return 0
}
