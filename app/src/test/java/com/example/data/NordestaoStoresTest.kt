package com.example.data

import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], application = android.app.Application::class)
class NordestaoStoresTest {
    private fun fixture() = javaClass.classLoader!!.getResourceAsStream("stores/nordestao-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
    @Test fun decodesAllObservedOfficialStoresAndKeepsMissingHoursMissing() {
        val snapshot = NordestaoStoresCodec.decode(fixture())
        assertEquals(12, snapshot.stores.size)
        assertTrue(snapshot.stores.any { it.name == "Nordestão Doze Anos" })
        val store = snapshot.stores.first { it.name == "Nordestão Igapó" }
        assertTrue(store.businessHours.isEmpty())
        assertEquals(listOf("8436154806"), store.phones)
        assertTrue(store.address.contains("Tomaz Landim"))
        assertTrue(store.mapUrl!!.startsWith("https://www.google.com/maps/search/"))
        assertTrue(store.matches("igapo")); assertTrue(store.matches("natal")); assertFalse(store.matches("inexistente"))
    }
    @Test fun sourceUpdatesReplaceTheSnapshotIncludingRemovals() {
        val root = org.json.JSONObject(fixture())
        val first = root.getJSONArray("stores").getJSONObject(0)
        first.put("businessHours", "Segunda a sábado: 8h às 20h")
        root.put("stores", org.json.JSONArray().put(first))
        val next = NordestaoStoresCodec.decode(root.toString())
        assertEquals(1,next.stores.size)
        assertEquals("Segunda a sábado: 8h às 20h",next.stores[0].businessHours)
        root.put("stores",org.json.JSONArray())
        assertTrue(NordestaoStoresCodec.decode(root.toString()).stores.isEmpty())
    }
    @Test fun invalidResponseDoesNotMasqueradeAsEmptyDirectory() {
        assertThrows(Exception::class.java) { NordestaoStoresCodec.decode("{}") }
        val root = org.json.JSONObject(fixture()); root.getJSONArray("stores").getJSONObject(0).put("id",0)
        assertThrows(Exception::class.java) { NordestaoStoresCodec.decode(root.toString()) }
    }
    @Test fun offersRemainBoundToTheRequestedStoreAndPreserveCashbackPrices() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/offers-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val snapshot = NordestaoStoreOffersCodec.decode(raw, 16145121)
        assertEquals(4, snapshot.offers.size)
        val cashback = snapshot.offers.first { it.cashback.isNotBlank() }
        assertTrue(cashback.priceCents > 0)
        assertTrue(cashback.matches(cashback.code))
        assertThrows(IllegalArgumentException::class.java) { NordestaoStoreOffersCodec.decode(raw, 18055359) }
        val restored = NordestaoStoreOffersCodec.decode(NordestaoStoreOffersCodec.encode(snapshot), 16145121)
        assertEquals(snapshot.offers, restored.offers)
        assertTrue(restored.stale)
        val offer = snapshot.offers.first()
        assertFalse(offer.active(0))
        assertFalse(offer.active(Long.MAX_VALUE))
    }
}
