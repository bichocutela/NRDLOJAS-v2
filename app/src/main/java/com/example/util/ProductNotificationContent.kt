package com.example.util

data class ProductNotificationContent(val description: String, val code: String, val barcode: String) {
    val detail: String get() = buildString {
        append(description)
        if (code.isNotBlank()) append("\nCódigo: $code")
        if (barcode.isNotBlank()) append("\nCódigo de barras: $barcode")
    }
    companion object {
        fun from(description: String, code: String?, barcode: String?): ProductNotificationContent {
            val cleanCode = code.orEmpty().trim().take(60)
            // NRD renders its internal code as CODE_128. Never invent an EAN.
            return ProductNotificationContent(description.trim().take(240), cleanCode,
                barcode?.trim()?.takeIf { it.isNotBlank() }?.take(60) ?: cleanCode)
        }
    }
}
