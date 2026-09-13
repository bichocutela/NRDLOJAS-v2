package com.example.data.acp

import com.example.data.OFFER_BANNER_CASHBACK
import com.example.data.OFFER_BANNER_CLUB
import com.example.data.OFFER_BANNER_DE_POR
import com.example.data.OFFER_BANNER_SECOND_UNIT
import com.example.data.OFFER_BANNER_STANDARD
import com.example.data.OFFER_BANNER_TAKE_PAY
import com.example.data.OFFER_BANNER_WHOLESALE
import org.junit.Assert.assertEquals
import org.junit.Test

class AcpOfferBannerKeyTest {
    @Test
    fun `every ACP offer family selects the expected administrative banner`() {
        assertEquals(OFFER_BANNER_CLUB, AcpOffer("Clube de Vantagens", "").bannerKey)
        assertEquals(OFFER_BANNER_DE_POR, AcpOffer("De/Por", "").bannerKey)
        assertEquals(OFFER_BANNER_TAKE_PAY, AcpOffer("Leve/Pague", "").bannerKey)
        assertEquals(OFFER_BANNER_SECOND_UNIT, AcpOffer("Segunda unidade", "").bannerKey)
        assertEquals(OFFER_BANNER_CASHBACK, AcpOffer("Cashback", "").bannerKey)
        assertEquals(OFFER_BANNER_CASHBACK, AcpOffer("Cashback em valor", "").bannerKey)
        assertEquals(OFFER_BANNER_WHOLESALE, AcpOffer("Atacado", "").bannerKey)
        assertEquals(OFFER_BANNER_STANDARD, AcpOffer("Preço cadastrado", "").bannerKey)
    }
}
