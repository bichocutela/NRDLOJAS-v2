package com.example.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/** Payload exists only while the master's experimental panel is active. */
internal object InstallLinkNfcSession {
    @Volatile var message: ByteArray? = null
        private set
    fun start(url: String) {
        require(url.startsWith("https://") && url.length < 8000)
        message = NdefMessage(arrayOf(NdefRecord.createUri(url))).toByteArray()
    }
    fun stop() { message = null }
}

class InstallLinkNfcService : HostApduService() {
    private var payload: ByteArray? = null
    private var protocol: NdefTagProtocol? = null
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val current = InstallLinkNfcSession.message
        if (current == null || commandApdu == null) {
            payload = null
            protocol = null
            return byteArrayOf(0x6A, 0x82.toByte())
        }
        if (payload !== current) {
            payload = current
            protocol = NdefTagProtocol(current)
        }
        return protocol!!.respond(commandApdu)
    }
    override fun onDeactivated(reason: Int) {
        payload = null
        protocol = null
    }
}
