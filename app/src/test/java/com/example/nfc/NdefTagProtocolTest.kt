package com.example.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class NdefTagProtocolTest {
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val selectApplication = hex("00A4040007D276000085010100")
    private fun selected(message: ByteArray): NdefTagProtocol = NdefTagProtocol(message).also {
        assertArrayEquals(hex("9000"), it.respond(selectApplication))
        assertArrayEquals(hex("9000"), it.respond(hex("00A4000C02E104")))
    }
    @Test fun readsLengthAndPayloadInChunks() {
        val tag = selected(hex("D10105550461626364"))
        assertArrayEquals(hex("00099000"), tag.respond(hex("00B0000002")))
        assertArrayEquals(hex("D101059000"), tag.respond(hex("00B0000203")))
        assertArrayEquals(hex("5504616263649000"), tag.respond(hex("00B0000506")))
    }
    @Test fun exposesReadOnlyCapabilityContainer() {
        val tag = NdefTagProtocol(byteArrayOf(1))
        tag.respond(selectApplication)
        assertArrayEquals(hex("9000"), tag.respond(hex("00A4000C02E103")))
        assertArrayEquals(hex("000F2000FF00FF0406E1047FFF00FF9000"), tag.respond(hex("00B000000F")))
        assertArrayEquals(hex("6D00"), tag.respond(hex("00D600000101")))
    }
    @Test fun rejectsReadsBeforeSelectionAndMalformedCommands() {
        val tag = NdefTagProtocol(byteArrayOf(1))
        assertArrayEquals(hex("6985"), tag.respond(hex("00B0000002")))
        assertArrayEquals(hex("6700"), tag.respond(hex("00A4040007D2")))
        assertArrayEquals(hex("6E00"), tag.respond(byteArrayOf()))
    }
    @Test fun reportsEndOfFileAndInvalidOffset() {
        val tag = selected(byteArrayOf(42))
        assertArrayEquals(hex("2A6282"), tag.respond(hex("00B0000203")))
        assertArrayEquals(hex("6B00"), tag.respond(hex("00B0000301")))
    }
}
