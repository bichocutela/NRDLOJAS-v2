package com.example.data.acp

import org.junit.Assert.*
import org.junit.Test

class AcpBarcodeGateTest {
    @Test fun acceptsExactlyOneResultAndPreservesLeadingZeros() {
        val gate = AcpBarcodeGate()
        assertEquals("0012345678905", gate.accept(listOf(" 0012345678905 ", "0012345678905")))
        assertNull(gate.accept(listOf("7891000412855")))
    }

    @Test fun ambiguousOrEmptyFramesDoNotConsumeTheScanner() {
        val gate = AcpBarcodeGate()
        assertNull(gate.accept(listOf(null, "")))
        assertNull(gate.accept(listOf("123", "456")))
        assertEquals("500", gate.accept(listOf("500")))
    }

    @Test fun rejectsOversizedAndControlCharacters() {
        val gate = AcpBarcodeGate()
        assertNull(gate.accept(listOf("1".repeat(129), "12\n34")))
        assertEquals("ABC-123", gate.accept(listOf("ABC-123")))
    }
}
