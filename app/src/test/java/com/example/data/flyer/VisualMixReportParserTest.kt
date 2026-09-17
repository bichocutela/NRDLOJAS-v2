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
        assertEquals("2026-09-16", offer.validFrom)
        assertEquals("2026-09-23", offer.validTo)
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
        assertEquals("2026-09-16", promo.validFrom)
        assertEquals("2026-09-30", promo.validTo)

        val club = result.offers.first { it.clubCondition == FlyerClubCondition.REQUIRED }
        assertEquals("7891150103818", club.barcodes.single())
        assertEquals(26.99, club.flyerPrice!!, 0.001)
        assertEquals("2026-09-16", club.validFrom)
        assertEquals("2026-09-22", club.validTo)
        assertTrue(club.detail.contains("2026-09-22"))
    }

    @Test
    fun `prefere promocao atual quando existe promocao anterior diferente`() {
        val text = """
            VISUAL MIX LTDA.
            Relatório de Produtos Alterados
            Produtos do dia 16/09/2026 - Abertura
            205000100
            1/01 7891234567895 PRODUTO TESTE 20,00 14,99 (10/09 a 15/09) 20,00 11,99 (16/09 a 20/09) P
        """.trimIndent()

        val result = VisualMixReportParser.parse("CENTRO_0012.pdf", text)!!
        val promo = result.offers.single()
        assertEquals(20.00, promo.regularPrice!!, 0.001)
        assertEquals(11.99, promo.flyerPrice!!, 0.001)
        assertEquals("2026-09-16", promo.validFrom)
        assertEquals("2026-09-20", promo.validTo)
        assertTrue(promo.detail.contains("2026-09-16"))
        assertTrue(promo.detail.contains("2026-09-20"))
    }

    @Test
    fun `prefere promocao e clube atuais quando historico anterior tambem existe`() {
        val text = """
            VISUAL MIX LTDA.
            Relatório de Produtos Alterados
            Produtos do dia 16/09/2026 - Abertura
            205000200
            1/01 7891234567888 PRODUTO TESTE PC 29,99 24,99 (10/09 a 15/09) 21,99 - 15/09 29,99 19,99 (16/09 a 22/09) 17,99 - 22/09 PC
        """.trimIndent()

        val result = VisualMixReportParser.parse("CLUBE_0012.pdf", text)!!
        assertEquals(2, result.offers.size)
        val promo = result.offers.first { it.clubCondition != FlyerClubCondition.REQUIRED }
        val club = result.offers.first { it.clubCondition == FlyerClubCondition.REQUIRED }
        assertEquals(29.99, promo.regularPrice!!, 0.001)
        assertEquals(19.99, promo.flyerPrice!!, 0.001)
        assertEquals(17.99, club.flyerPrice!!, 0.001)
        assertEquals("2026-09-22", promo.validTo)
        assertEquals("2026-09-22", club.validTo)
        assertTrue(promo.detail.contains("2026-09-22"))
        assertTrue(club.detail.contains("2026-09-22"))
    }

    @Test
    fun `extrai clube puro do relatorio clube`() {
        val text = """
            VISUAL MIX LTDA.
            Relatório de Produtos Alterados
            Produtos do dia 16/09/2026 - Abertura
            201063600
            1/01 7896102503708 KETCHUP HEINZ TB 397G TRAD 19,79 19,79 12,99 - 22/09 C
        """.trimIndent()

        val result = VisualMixReportParser.parse("CLUBE_0012.pdf", text)!!
        val club = result.offers.single()
        assertEquals("201063600", club.productCodes.single())
        assertEquals("7896102503708", club.barcodes.single())
        assertEquals(19.79, club.regularPrice!!, 0.001)
        assertEquals(12.99, club.flyerPrice!!, 0.001)
        assertEquals(FlyerClubCondition.REQUIRED, club.clubCondition)
        assertEquals("2026-09-16", club.validFrom)
        assertEquals("2026-09-22", club.validTo)
        assertTrue(club.detail.contains("2026-09-22"))
    }

    @Test
    fun `preserva codigo quando ele vem na propria linha`() {
        val text = """
            VISUAL MIX LTDA.
            Relatório de Produtos Alterados
            Produtos do dia 16/09/2026 - Abertura
            2033190/0 7898910185060 GOMA FRESCA DELICIA POTIGUAR PC 1KG 6,99 6,99 4,49 - 17/09 C
        """.trimIndent()

        val result = VisualMixReportParser.parse("CLUBE_0012.pdf", text)!!
        val club = result.offers.single()
        assertEquals("2033190", club.productCodes.single())
        assertEquals("7898910185060", club.barcodes.single())
        assertEquals(4.49, club.flyerPrice!!, 0.001)
        assertEquals("2026-09-17", club.validTo)
        assertTrue(club.detail.contains("2026-09-17"))
    }

    @Test
    fun `nao transforma indice curto em codigo de produto`() {
        val text = """
            VISUAL MIX LTDA.
            Relatório de Produtos Alterados
            Produtos do dia 16/09/2026 - Abertura
            1/01 7891008121629 CHOC TAB GAROTO TALENTO TB 85G DOCE LEITE 13,99 13,99 8,49 (16/09 a 21/09) P
        """.trimIndent()

        val result = VisualMixReportParser.parse("CENTRO_0012.pdf", text)!!
        val offer = result.offers.single()
        assertTrue(offer.productCodes.isEmpty())
        assertEquals("7891008121629", offer.barcodes.single())
        assertEquals(8.49, offer.flyerPrice!!, 0.001)
    }

    @Test
    fun `remonta produto quando pdf quebra a linha em varios pedacos`() {
        val text = """
            VISUAL MIX LTDA.
            Relatório de Produtos Alterados
            Produtos do dia 16/09/2026 - Abertura
            202896700
            1/01
            7891008121629
            CHOC TAB GAROTO TALENTO TB 85G DOCE LEITE
            13,99 13,99
            8,49 (16/09 a 21/09)
            P
        """.trimIndent()

        val result = VisualMixReportParser.parse("CENTRO_0012.pdf", text)!!
        val offer = result.offers.single()
        assertEquals("202896700", offer.productCodes.single())
        assertEquals("7891008121629", offer.barcodes.single())
        assertEquals(13.99, offer.regularPrice!!, 0.001)
        assertEquals(8.49, offer.flyerPrice!!, 0.001)
        assertEquals("2026-09-21", offer.validTo)
        assertTrue(offer.detail.contains("2026-09-21"))
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
