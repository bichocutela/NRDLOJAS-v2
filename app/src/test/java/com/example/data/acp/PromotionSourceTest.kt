package com.example.data.acp

import com.example.data.promotions.AcpPromotionsRepository
import com.example.data.promotions.PromotionCategory
import com.example.data.promotions.PromotionStores
import com.example.data.flyer.*
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PromotionSourceTest {
    @Test fun categoriesUseProductNounInsteadOfIngredients() {
        assertEquals("Hortifruti", PromotionCategory.forDescription("MAÇÃ FUJI KG"))
        assertEquals("Mercearia", PromotionCategory.forDescription("Molho de tomate tradicional"))
        assertEquals("Mercearia", PromotionCategory.forDescription("Arroz com frango"))
        assertEquals("Frios e laticínios", PromotionCategory.forDescription("Leite integral"))
        assertEquals("Limpeza", PromotionCategory.forDescription("Detergente maçã"))
        assertEquals("Outras ofertas", PromotionCategory.forDescription("Produto sem categoria conhecida"))
    }

    @Test fun onlyCityGardenStartsEnabled() {
        assertEquals(listOf("0012"), PromotionStores.defaults.filter { it.enabled }.map { it.code })
    }

    @Test fun validityIsInclusiveAndUsesFortalezaDate() {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("America/Fortaleza")
        }.parse("2026-10-07 23:59")!!.time
        assertTrue(AcpPromotionsRepository.active("07/10/2026", "2026-10-07", time))
        assertFalse(AcpPromotionsRepository.active("2026-10-08", null, time))
        assertFalse(AcpPromotionsRepository.active(null, "2026-10-06", time))
        assertNull(AcpPromotionsRepository.isoDate("31/02/2026"))
    }

    @Test fun importedOffersKeepIndividualDatesAndOnlyRealDePorPrices() {
        val offer = FlyerOffer(type = FlyerOfferType.DE_POR, sourceDescription = "Arroz branco",
            productCodes = listOf("1234"), flyerPrice = 5.0, regularPrice = 8.0,
            validFrom = "2026-10-01", validTo = "2026-10-08")
        val analysis = FlyerAnalysisResult("Relatório", "2026-10-01", "2026-10-31",
            listOf(offer, offer.copy(type = FlyerOfferType.FLYER_PRICE), offer.copy(flyerPrice = 9.0)),
            emptyList(), "visual_mix", "relatorio.pdf")
        val records = PromotionStores.records(analysis)
        assertEquals(1, records.size)
        assertEquals("2026-10-08", records.single().to)
        assertEquals(5.0, records.single().price, 0.0)
    }
    @Test fun otherStoresPublishClubWithoutAnAcpMatchWhileCityGardenKeepsDePorPath() {
        val offer = FlyerOffer(type=FlyerOfferType.FLYER_PRICE, sourceDescription="Produto clube", productCodes=listOf("1234"),
            regularPrice=10.0, flyerPrice=8.0, clubCondition=FlyerClubCondition.REQUIRED,
            validFrom="2026-10-24", validTo="2026-10-31")
        val analysis=FlyerAnalysisResult("CLUBE_0005.pdf",null,null,listOf(offer),emptyList(),"visual_mix","CLUBE_0005.pdf")
        assertEquals("Clube",PromotionStores.records(analysis).single().family)
        assertEquals(FlyerMatchStatus.UNRESOLVED,offer.matchStatus)
        assertTrue(PromotionStores.records(analysis,includeClub=false).isEmpty())
    }
    @Test fun dailyDeltasKeepCurrentAndFutureCampaignsAndDoNotEraseOtherProductsOrFamilies() {
        val now=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse("2026-10-24")!!.time + 12*60*60*1000
        val base=com.example.data.promotions.StorePromotionRecord("1234","","Arroz",5.0,8.0,"2026-10-24","2026-10-31")
        val previous=listOf(base,base.copy(code="5678"),base.copy(family="Clube"),base.copy(code="old",to="2026-10-23"))
        val incoming=listOf(base.copy(price=4.0,from="2026-10-25"))
        val merged=com.example.data.promotions.mergeStoreRecords(previous,incoming,now)
        assertEquals(4,merged.size)
        assertTrue(merged.any{it.code=="5678"})
        assertTrue(merged.any{it.family=="Clube"})
        assertEquals(2,merged.count{it.code=="1234" && it.family=="De/Por"})
        assertEquals(merged,com.example.data.promotions.mergeStoreRecords(merged,incoming,now))
    }
    @Test fun unavailableAcpCannotBlockOtherStoreDocumentsAndCancellationIsNotSwallowed() = kotlinx.coroutines.runBlocking {
        val cached=com.example.data.Promotion("0012|1","De/Por","",null,null,null,
            listOf(com.example.data.PromotionProduct("1","Produto","5","8",null,"0012")))
        val document=cached.copy(id="0005|2",products=listOf(cached.products.first().copy(code="2",storeCode="0005")))
        val stores=listOf(com.example.data.promotions.PromotionStore("0012"),com.example.data.promotions.PromotionStore("0005"))
        val result=com.example.data.promotions.loadStorePromotionSnapshots(stores,listOf(cached)) {
            if(it.code=="0012") error("ACP offline") else listOf(document)
        }
        assertEquals(setOf("0012|1","0005|2"),result.map{it.id}.toSet())
        assertTrue(runCatching {
            com.example.data.promotions.loadStorePromotionSnapshots(stores,emptyList()) { throw kotlinx.coroutines.CancellationException() }
        }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }
}
