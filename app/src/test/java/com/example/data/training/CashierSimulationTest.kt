package com.example.data.training

import org.junit.Assert.*
import org.junit.Test

class CashierSimulationTest {
    private fun run(vararg keys: String, start: CashierSession = CashierSession()) = keys.fold(start) { s, k -> CashierSimulation.press(s, k) }
    private fun sale() = run("ENTRA", "3", "3", "0")
    private fun receive() = run("2", "5", "0", "0", "0", "8", "ENTRA", "TOTAL", "ANULA", start = sale())
    @Test fun opensNoteAndRecordsKnownProducts() {
        assertEquals("DISPONIVEL", CashierSimulation.operator(CashierSession()))
        val s = receive()
        assertEquals(CashierPage.RECEIVE, s.page)
        assertEquals(599L, s.total)
        assertTrue(s.noteOpen)
    }
    @Test fun cashReceiptThenDonationAndChange() {
        val s = run("1", "0", "0", "0", "DINHEIRO", "3", start = receive())
        assertEquals(CashierPage.DONATION, s.page)
        assertFalse(s.compact)
        val done = run("3", start = s)
        assertEquals(CashierPage.DONE, done.page)
        assertTrue(CashierSimulation.operator(done).contains("4,01"))
    }
    @Test fun allElectronicPaymentsSkipDonation() {
        for(path in listOf(arrayOf("1", "1", "1"), arrayOf("1", "2"), arrayOf("2", "1", "1"), arrayOf("2", "2", "3"))) {
            val selected = run("TEF", *path, start = receive())
            val done = run("SIM_APPROVE", "ENTRA", "1", start = selected)
            assertEquals(CashierPage.DONE, done.page)
            assertFalse(done.cash)
            assertEquals(done.total + done.change, done.received)
        }
    }
    @Test fun cancellationChoicesDifferForCardAndPix() {
        val card = run("TEF", "1", "1", "1", "ANULA", start = receive())
        assertEquals(CashierPage.CARD, run("1", start = card).page)
        assertEquals(CashierPage.RECEIVE, run("0", start = card).page)
        val pix = run("TEF", "2", "2", "3", "ANULA", start = receive())
        assertEquals(CashierPage.QR, run("2", start = pix).page)
        assertEquals(CashierPage.RECEIVE, run("1", start = pix).page)
    }
    @Test fun pixChangeRequiresAmountAndPinAndRespectsTime() {
        val wallet = run("TEF", "2", "2", start = receive())
        assertEquals(CashierPage.WALLET, CashierSimulation.press(wallet, "4", 20).page)
        val s = run("4", "1", "0", "0", "0", "ENTRA", "SIM_PIN", start = wallet)
        assertEquals(CashierPage.QR, s.page)
        assertEquals(1000L, s.change)
    }
    @Test fun unidentifiedCpfAsksPhoneAndExplainsSms() {
        val s = run("ENTRA", "3", "1", "1", "SIM_PIN", "ENTRA", start = CashierSession(registered = false))
        assertEquals(CashierPage.PHONE, s.page)
        assertTrue(run("SIM_PIN", start = s).notice.contains("SMS"))
    }
    @Test fun openNoteSupportsIdentificationAndFunction514() {
        assertEquals(CashierPage.IDENTIFY, run("ENTRA", start = sale()).page)
        assertEquals(CashierPage.LOYALTY, run("FUNÇÃO", "5", "1", "4", start = sale()).page)
        assertEquals(CashierPage.CNPJ_ERROR, run("ENTRA", "3", "ANULA", start = sale()).page)
    }
    @Test fun cancelledPixChangeDoesNotLeakIntoNextPayment() {
        val cancelled = run("TEF", "2", "2", "4", "1", "0", "0", "0", "ENTRA", "SIM_PIN", "ANULA", "1", start = receive())
        assertEquals(0L, cancelled.change)
        val pix = run("TEF", "2", "2", "3", start = cancelled)
        assertEquals(0L, pix.change)
        assertEquals(CashierPage.QR, pix.page)
    }
    @Test fun registeredLoyaltyCustomerIsNamedThenAskedAboutMissingProducts() {
        val named = run("ENTRA", "3", "1", "1", "SIM_PIN")
        assertEquals(CashierPage.CUSTOMER_NAME, named.page)
        assertTrue(CashierSimulation.operator(named).contains("CLIENTE DE TREINO"))
        val offers = run("ENTRA", start = named)
        assertEquals(CashierPage.OFFERS_CONFIRM, offers.page)
        val question = run("SIM_ASKED", "0", start = offers)
        assertEquals(CashierPage.MISSING, question.page)
        assertEquals(CashierPage.SALE, run("SIM_FOUND", start = question).page)
        val form = run("SIM_MISSING", start = question)
        assertEquals(CashierPage.MISSING_FORM, run("SIM_SAVE_MISSING", start = form).page)
        val saved = run("SIM_SAVE_MISSING", start = form.copy(missingName = "Treino", missingPhone = "00000000000", missingProduct = "Tomate"))
        assertEquals(CashierPage.SALE, saved.page)
        assertEquals("Tomate", saved.missingProduct)
    }
}
