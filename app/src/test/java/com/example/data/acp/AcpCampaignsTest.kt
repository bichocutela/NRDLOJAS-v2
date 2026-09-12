package com.example.data.acp

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AcpCampaignsTest {
    private fun product(json: String): AcpProduct = AcpProductParser.page(
        JSONObject("""{"items":[$json],"pageIndex":0,"totalPages":1}"""), 0
    ).items.single()

    @Test fun secondUnitPromotionComesFromCampaignProductRule() {
        val p = product("""{"id":"p1","code":"123","barCode":"789","description":"Teste","value":45.49}""")
        val campaign = AcpCampaignParser.page(JSONObject("""
            {"items":[{
              "id":"c1","code":"50SEG","name":"50% na segunda unidade","active":true,
              "startDate":"2026-09-10","endDate":"2026-09-15",
              "products":[{"product":{"id":"p1","code":"123","barCode":"789"},"secondUnitDiscount":50}]
            }]}
        """)).single()
        val offer = campaign.offersFor(p).single()
        assertEquals("Segunda unidade", offer.title)
        assertEquals("50% DE DESCONTO", offer.headline)
        assertEquals("R$ 45,49", offer.referencePrice?.brl())
        assertTrue(offer.detail.contains("50%"))
        assertTrue(offer.detail.contains("50SEG"))
    }

    @Test fun secondUnitCanBeReadFromExplicitCampaignTextOnlyWhenProductIsLinked() {
        val p = product("""{"id":"p2","code":"456","barCode":"999","description":"Teste 2","value":10}""")
        val campaign = AcpCampaignParser.page(JSONObject("""
            {"items":[{
              "id":"c2","name":"Oferta especial","description":"50% de desconto na segunda unidade",
              "products":[{"product":{"id":"p2","code":"456","barCode":"999"}}]
            }]}
        """)).single()
        assertEquals("50% DE DESCONTO", campaign.offersFor(p).single().headline)
    }

    @Test fun levePagueCanBeRecoveredFromExplicitCampaignDescription() {
        val p = product("""{"id":"p3","code":"789","description":"Teste 3","value":12}""")
        val campaign = AcpCampaignParser.page(JSONObject("""
            {"items":[{
              "id":"c3","description":"Leve 3 Pague 2",
              "products":[{"product":{"id":"p3","code":"789"}}]
            }]}
        """)).single()
        val offer = campaign.offersFor(p).single()
        assertEquals("Leve/Pague", offer.title)
        assertTrue(offer.detail.contains("Leve 3, pague 2"))
        assertTrue(offer.detail.contains("R$ 8,00"))
    }

    @Test fun nestedDataItemsAreAccepted() {
        val campaigns = AcpCampaignParser.page(JSONObject("""{"data":{"items":[{"id":"c4","name":"Nested","products":[]}]}}"""))
        assertEquals(1, campaigns.size)
        assertEquals("Nested", campaigns.single().name)
    }

    @Test fun integrationInfoUsesObservedSynchronizationContract() {
        val info = AcpCampaignParser.integration(JSONObject("""
            {"status":2,"lastRun":"2026-09-12T02:25:08","lastCompleteRun":"2026-09-12T02:25:08","message":null,"id":1}
        """))
        assertEquals(2, info.status)
        assertEquals("2026-09-12T02:25:08", info.lastRun)
        assertEquals("2026-09-12T02:25:08", info.lastCompleteRun)
        assertNull(info.message)
        assertEquals("1", info.id)
    }
}
