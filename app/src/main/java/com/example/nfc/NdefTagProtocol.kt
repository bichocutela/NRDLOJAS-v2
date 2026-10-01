package com.example.nfc

/** Read-only NFC Forum Type 4 tag. Android-independent for protocol tests. */
internal class NdefTagProtocol(private val message: ByteArray) {
    private var applicationSelected = false
    private var selectedFile = 0
    private val ndef = byteArrayOf((message.size shr 8).toByte(), message.size.toByte()) + message
    private val cc = byteArrayOf(0, 15, 0x20, 0, 0xFF.toByte(), 0, 0xFF.toByte(),
        4, 6, 0xE1.toByte(), 4, 0x7F, 0xFF.toByte(), 0, 0xFF.toByte())

    fun respond(command: ByteArray): ByteArray {
        fun status(a: Int, b: Int) = byteArrayOf(a.toByte(), b.toByte())
        if (command.size < 4 || command[0].toInt() != 0) return status(0x6E, 0)
        val instruction = command[1].toInt() and 255
        val p1 = command[2].toInt() and 255
        val p2 = command[3].toInt() and 255
        if (instruction == 0xA4) {
            if (command.size < 5) return status(0x67, 0)
            val length = command[4].toInt() and 255
            if (command.size < 5 + length) return status(0x67, 0)
            val data = command.copyOfRange(5, 5 + length)
            if (p1 == 4) {
                selectedFile = 0
                applicationSelected = data.contentEquals(byteArrayOf(0xD2.toByte(), 0x76, 0, 0, 0x85.toByte(), 1, 1))
                return if (applicationSelected) status(0x90, 0) else status(0x6A, 0x82)
            }
            if (!applicationSelected) return status(0x69, 0x85)
            if (p1 == 0 && data.size == 2) {
                selectedFile = when {
                    data.contentEquals(byteArrayOf(0xE1.toByte(), 3)) -> 3
                    data.contentEquals(byteArrayOf(0xE1.toByte(), 4)) -> 4
                    else -> 0
                }
                return if (selectedFile != 0) status(0x90, 0) else status(0x6A, 0x82)
            }
            return status(0x6A, 0x86)
        }
        if (instruction != 0xB0) return status(0x6D, 0)
        if (!applicationSelected || selectedFile == 0) return status(0x69, 0x85)
        if (command.size != 5 || p1 >= 128) return status(0x67, 0)
        val file = if (selectedFile == 3) cc else ndef
        val offset = (p1 shl 8) or p2
        val length = (command[4].toInt() and 255).let { if (it == 0) 256 else it }
        if (offset >= file.size) return status(0x6B, 0)
        val end = minOf(offset + length, file.size)
        return file.copyOfRange(offset, end) + if (end < offset + length) status(0x62, 0x82) else status(0x90, 0)
    }
}
