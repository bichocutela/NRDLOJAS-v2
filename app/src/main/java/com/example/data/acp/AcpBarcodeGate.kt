package com.example.data.acp

import java.util.concurrent.atomic.AtomicBoolean

/** One result per camera opening; ambiguous frames must not pick an arbitrary product. */
internal class AcpBarcodeGate {
    private val consumed = AtomicBoolean(false)

    fun accept(values: List<String?>): String? {
        val codes = values.mapNotNull { it?.trim()?.takeIf { code ->
            code.isNotEmpty() && code.length <= 128 && code.all { char -> char.code in 32..126 }
        } }.distinct()
        return codes.singleOrNull()?.takeIf { consumed.compareAndSet(false, true) }
    }
}
