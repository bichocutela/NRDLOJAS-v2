package com.example.data.flyer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualMixReportParserTest {
    @Test
    fun `extrai ean preco promocional e vigencia do centro`() {
        val text = """
            VISUAL MIX LTDA. Data: 15/09/2026
            Relatório de Produtos Alterados Hora: 20:45:25
            Produtos do dia 16/09/2026 - Abertura
            Código Código Automação prod.desc Preço Anterior Promoção Anterior Clube Anterior Preço Atual Promoção Atual Clube Atual Flags
            200814600
            1/01 7891010503024 ABS SEMPRE LIVRE ADAPT NORMAL C/ABAS C/8 SUAVE . 5,79 5,79 3,49 (16/09 a 23/09) P
        """.trimIndent()

        val result = VisualMixReportParser.parse("CENTRO_0012.pdf", text)
        assertNotNull(result)
        val offer = result!!.offers.single()
        assertEquals("7891010503024", offer.barcodes.single())
        assertEquals("200814600", offer.productCodes.single())
        assertEquals(5.79, offer.regularPrice!!, 0.001)
        assertEquals(3.49, offer.flyerPrice!!, 0.001)
        assertEquals(FlyerOfferType.DE_POR, offer.type)
        assertTrue(offer.detail.contains("2026-09-16"))
        assertTrue(offer.detail.contains("2026-09-23"))
    }

    @Test
    fun `extrai promocao e clube quando flags sao PC`() {
        val text = """
            VISUAL MIX LTDA. Data: 15/09/2026
            Relatório de Produtos Alterados Hora: 20:54:27
            Produtos do dia 16/09/2026 - Abertura
            Código Código Automação prod.desc Preço Anterior Promoção Anterior Clube Anterior Preço Atual Promoção Atual Clube Atual Flags
            203292501
            0/01 7891150103818 KIT SHAMPOO DOVE 350 ML + CD 175ML UV REPAIR GLOW 39,99 32,90 (12/09 a 30/09) 39,99 32,90 (16/09 a 30/09) 26,99 - 22/09 PC
        """.trimIndent()

        val result = VisualMixReportParser.parse("CLUBE_0012.pdf", text)!!
        assertEquals(2, result.offers.size)

        val promo = result.offers.first { it.clubCondition != FlyerClubCondition.REQUIRED }
        assertEquals(32.90, promo.flyerPrice!!, 0.001)
        assertEquals(FlyerOfferType.DE_POR, promo.type)

        val club = result.offers.first { it.clubCondition == FlyerClubCondition.REQUIRED }
        assertEquals("7891150103818", club.barcodes.single())
        assertEquals(26.99, club.flyerPrice!!, 0.001)
        assertTrue(club.detail.contains("2026-09-22"))
    }

    @Test
    fun `ignora alteracao simples de preco sem P ou C`() {
        val text = """
            VISUAL MIX LTDA.
            Relatório de Produtos Alterados
            Produtos do dia 16/09/2026 - Abertura
            256230/01 2562300 BATATA FRITA NORD KG 59,90 39,90 V
        """.trimIndent()

        val result = VisualMixReportParser.parse("CENTRO_0012.pdf", text)!!
        assertTrue(result.offers.isEmpty())
    }
}
