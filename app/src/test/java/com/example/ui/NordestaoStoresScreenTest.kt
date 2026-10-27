package com.example.ui

import android.app.Application
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.NordestaoStoresCodec
import com.example.data.displayHours
import com.example.data.hoursFromLeaflet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class NordestaoStoresScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchOpensStoreWithRealAddressPhoneAndLeafletHours() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/nordestao-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        compose.setContent { MaterialTheme { NordestaoStoresScreen({}, { NordestaoStoresCodec.decode(raw) }) } }
        compose.onNodeWithText("Buscar loja, bairro ou cidade").performTextInput("igapo")
        compose.onNodeWithText("Nordestão Igapó").performClick()
        compose.onNodeWithText("Horário de funcionamento").assertExists()
        compose.onNodeWithText("Segunda a sábado: 07h às 22h\nDomingos e feriados: 07h às 20h").assertExists()
        compose.onNodeWithText("Ligar: 8436154806").performScrollTo().assertIsDisplayed()
    }
    @Test fun sourceFailureShowsRetryInsteadOfPretendingThereAreNoStores() {
        compose.setContent { MaterialTheme { NordestaoStoresScreen({}, { throw java.io.IOException("offline") }) } }
        compose.onNodeWithText("Não foi possível carregar as lojas. Tente novamente.").assertExists()
        compose.onNodeWithText("Tentar novamente").assertExists()
        compose.onNodeWithText("Nenhuma loja disponível na fonte.").assertDoesNotExist()
    }
    @Test fun mapEscapesSourceContentAndDistanceOrderingUsesCoordinates() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/nordestao-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val store = NordestaoStoresCodec.decode(raw).stores.first().copy(name = "</script><img src=x onerror=alert(1)>")
        val assets = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>().assets
        val js = assets.open("store-map/leaflet.js").bufferedReader().use { it.readText() }
        val css = assets.open("store-map/leaflet.css").bufferedReader().use { it.readText() }
        val html = storesMapHtml(listOf(store), js, css)
        org.junit.Assert.assertFalse(html.contains("</script><img"))
        org.junit.Assert.assertTrue(html.contains("textContent=s.name"))
        org.junit.Assert.assertTrue(html.contains("OpenStreetMap"))
        org.junit.Assert.assertFalse(html.contains("https://unpkg.com"))
        org.junit.Assert.assertFalse(html.contains("<script src="))
        org.junit.Assert.assertTrue(html.contains("Leaflet 1.9.4"))
        org.junit.Assert.assertTrue(html.contains("store-pin"))
        org.junit.Assert.assertEquals(0.0, storeDistance(-5.8,-35.2,-5.8,-35.2),0.001)
        org.junit.Assert.assertTrue(storeDistance(-5.8,-35.2,-5.9,-35.3) > 10.0)
    }
    @Test fun hoursMatchEveryNamedStoreAndMossoroHolidayConfirmation() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/nordestao-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val stores = NordestaoStoresCodec.decode(raw).stores
        org.junit.Assert.assertEquals(12, stores.count { it.hoursFromLeaflet })
        stores.forEach { store ->
            if(store.name.contains("Doze Anos") || store.name.contains("Abolição")) org.junit.Assert.assertEquals("Segunda a sábado: 07h às 22h\nDomingos, feriados e feriados locais: 07h às 20h",store.displayHours)
            else if(store.name.contains("Alecrim") || store.name.contains("Petrópolis") || store.name.contains("Igapó") || store.name.contains("Rota do Sol")) org.junit.Assert.assertTrue(store.displayHours.contains("Domingos e feriados: 07h às 20h"))
            else org.junit.Assert.assertTrue(store.displayHours.contains("Feriados: 07h às 22h"))
        }
        val official = stores.first().copy(businessHours="Horário especial da fonte")
        org.junit.Assert.assertEquals("Horário especial da fonte",official.displayHours)
        org.junit.Assert.assertFalse(official.hoursFromLeaflet)
    }

    @Test fun mapLocationCommandsCarryThePointAndRecenterOnlyOnExplicitRequest() {
        val script = storesMapUpdate(emptyList(), -5.81 to -35.21, 2)
        org.junit.Assert.assertTrue(script.contains("window.nrdSetLocation([-5.81,-35.21],2)"))
        org.junit.Assert.assertTrue(storesMapUpdate(emptyList(), null, 0).contains("nrdSetLocation(null,0)"))
        org.junit.Assert.assertTrue(storesMapUpdate(emptyList(), Double.NaN to 0.0, 0).contains("nrdSetLocation(null,0)"))
    }
    @Test fun mapOwnsDragAndPinchUntilTheGestureEndsOrIsCancelled() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val requests = mutableListOf<Boolean>()
        val parent = object : android.widget.FrameLayout(context) {
            override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                requests.add(disallowIntercept)
                super.requestDisallowInterceptTouchEvent(disallowIntercept)
            }
        }
        val map = StoreMapWebView(context)
        parent.addView(map)
        fun touch(action: Int) {
            val event = android.view.MotionEvent.obtain(0, 1, action, 20f, 20f, 0)
            try { map.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        touch(android.view.MotionEvent.ACTION_DOWN)
        org.junit.Assert.assertEquals(true, requests.last())
        touch(android.view.MotionEvent.ACTION_MOVE)
        org.junit.Assert.assertEquals(true, requests.last())
        touch(android.view.MotionEvent.ACTION_UP)
        org.junit.Assert.assertEquals(false, requests.last())
        touch(android.view.MotionEvent.ACTION_DOWN)
        touch(android.view.MotionEvent.ACTION_CANCEL)
        org.junit.Assert.assertEquals(false, requests.last())
        map.destroy()
    }

    @Test fun readyMapReceivesStoresAndLocationWithoutReloadingItsPage() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/nordestao-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val stores = NordestaoStoresCodec.decode(raw).stores
        val point = androidx.compose.runtime.mutableStateOf<Pair<Double,Double>?>(null)
        val request = androidx.compose.runtime.mutableIntStateOf(0)
        lateinit var root: android.view.View
        compose.setContent {
            root = androidx.compose.ui.platform.LocalView.current.rootView
            NordestaoStoresMap(stores, point.value, request.intValue, {}, {})
        }
        fun find(view: android.view.View): android.webkit.WebView? {
            if(view is android.webkit.WebView) return view
            if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        lateinit var map: android.webkit.WebView
        compose.runOnIdle {
            map = requireNotNull(find(root))
            map.webChromeClient!!.onConsoleMessage(android.webkit.ConsoleMessage("NRD_MAP_READY", "inline", 1, android.webkit.ConsoleMessage.MessageLevel.LOG))
        }
        compose.waitForIdle()
        val shadow = org.robolectric.Shadows.shadowOf(map)
        org.junit.Assert.assertTrue(shadow.lastEvaluatedJavascript.contains("16145139"))
        val firstLoad = shadow.lastLoadDataWithBaseURL
        compose.runOnIdle { point.value = -5.81 to -35.21; request.intValue = 1 }
        compose.waitForIdle()
        org.junit.Assert.assertTrue(shadow.lastEvaluatedJavascript.contains("nrdSetLocation([-5.81,-35.21],1)"))
        org.junit.Assert.assertSame(firstLoad, shadow.lastLoadDataWithBaseURL)
    }

    private class LocationSourceFake : StoreLocationSource {
        var allowed = true
        var switchedOn = true
        var available = listOf("network", "gps")
        var saved = emptyList<android.location.Location>()
        var throwsFor: String? = null
        var immediate: android.location.Location? = null
        val callbacks = mutableMapOf<String, (android.location.Location?) -> Unit>()
        val cancelled = mutableListOf<String>()
        override fun permitted() = allowed
        override fun enabled() = switchedOn
        override fun providers() = available
        override fun cached() = saved
        override fun request(provider: String, callback: (android.location.Location?) -> Unit): () -> Unit {
            if(provider == throwsFor) throw IllegalArgumentException("unavailable provider")
            callbacks[provider] = callback
            immediate?.let { callback(it) }
            return { cancelled.add(provider) }
        }
    }
    private fun position(lat: Double = -5.81, ageSeconds: Long = 1) = android.location.Location("test").apply {
        latitude = lat; longitude = -35.21; elapsedRealtimeNanos = 1_000_000_000_000L - ageSeconds * 1_000_000_000L
    }
    @Test fun locationUsesNewestRecentPositionWithoutWaitingForAnotherFix() = runTest {
        val source = LocationSourceFake().apply { saved = listOf(position(-5.8,10), position(-5.81,1)) }
        org.junit.Assert.assertEquals(StoreLocationResult.Found(-5.81 to -35.21), resolveStoreLocation(source, nowNanos={1_000_000_000_000L}))
        org.junit.Assert.assertTrue(source.callbacks.isEmpty())
    }
    @Test fun networkFailureDoesNotDiscardGpsFixAndAllRequestsAreCancelled() = runTest {
        val source = LocationSourceFake()
        val result = async { resolveStoreLocation(source, nowNanos={1_000_000_000_000L}) }
        runCurrent()
        source.callbacks.getValue("network")(null)
        org.junit.Assert.assertFalse(result.isCompleted)
        source.callbacks.getValue("gps")(position())
        org.junit.Assert.assertEquals(StoreLocationResult.Found(-5.81 to -35.21),result.await())
        org.junit.Assert.assertEquals(setOf("network","gps"),source.cancelled.toSet())
    }
    @Test fun brokenProviderDoesNotAbortTheOtherAvailableProvider() = runTest {
        val source = LocationSourceFake().apply { throwsFor = "network" }
        val result = async { resolveStoreLocation(source, nowNanos={1_000_000_000_000L}) }
        runCurrent(); source.callbacks.getValue("gps")(position())
        org.junit.Assert.assertTrue(result.await() is StoreLocationResult.Found)
        org.junit.Assert.assertEquals(listOf("gps"),source.cancelled)
    }
    @Test fun staleAndInvalidPositionsAreRejectedAndDeadlineCleansUpRequests() = runTest {
        val source = LocationSourceFake().apply { saved = listOf(position(ageSeconds=180),position(lat=100.0)) }
        val result = async { resolveStoreLocation(source, timeoutMs=1000, nowNanos={1_000_000_000_000L}) }
        advanceUntilIdle()
        org.junit.Assert.assertEquals(StoreLocationResult.Error(StoreLocationFailure.TIMEOUT),result.await())
        org.junit.Assert.assertEquals(setOf("network","gps"),source.cancelled.toSet())
    }
    @Test fun leavingScreenCancelsAllPendingLocationRequests() = runTest {
        val source = LocationSourceFake()
        val result = async { resolveStoreLocation(source, nowNanos={1_000_000_000_000L}) }
        runCurrent(); result.cancel(); runCurrent()
        org.junit.Assert.assertEquals(setOf("network","gps"),source.cancelled.toSet())
    }
    @Test fun missingPermissionAndDisabledDeviceHaveDifferentActionableFailures() = runTest {
        val denied = LocationSourceFake().apply { allowed = false }
        org.junit.Assert.assertEquals(StoreLocationResult.Error(StoreLocationFailure.PERMISSION_DENIED),resolveStoreLocation(denied))
        val disabled = LocationSourceFake().apply { switchedOn = false }
        org.junit.Assert.assertEquals(StoreLocationResult.Error(StoreLocationFailure.DISABLED),resolveStoreLocation(disabled))
        org.junit.Assert.assertTrue(denied.callbacks.isEmpty() && disabled.callbacks.isEmpty())
    }
    @Test fun approximatePermissionUsesFusedAndNetworkWhileGpsRequiresFinePermission() {
        org.junit.Assert.assertEquals(listOf("fused","network"),storeLocationProviders(listOf("gps","network","fused"),false))
        org.junit.Assert.assertEquals(listOf("gps"),storeLocationProviders(listOf("gps"),true))
        org.junit.Assert.assertEquals(emptyList<String>(),storeLocationProviders(listOf("gps"),false))
    }

    @Test fun immediateProviderReplyStillCancelsItsRequestWithoutWaitingForGps() = runTest {
        val source = LocationSourceFake().apply { immediate = position() }
        org.junit.Assert.assertTrue(resolveStoreLocation(source, nowNanos={1_000_000_000_000L}) is StoreLocationResult.Found)
        org.junit.Assert.assertEquals(listOf("network"),source.cancelled)
        org.junit.Assert.assertEquals(setOf("network"),source.callbacks.keys)
    }
    @Test fun allProvidersWithoutAFixReturnUnavailableInsteadOfCrashing() = runTest {
        val source = LocationSourceFake()
        val result = async { resolveStoreLocation(source, nowNanos={1_000_000_000_000L}) }
        runCurrent(); source.callbacks.values.toList().forEach { it(null) }
        org.junit.Assert.assertEquals(StoreLocationResult.Error(StoreLocationFailure.UNAVAILABLE),result.await())
        org.junit.Assert.assertEquals(2,source.cancelled.size)
    }

    @Test fun offersWithoutFavoriteAskForStoreWithoutFetching() {
        var calls = 0
        compose.setContent { MaterialTheme {
            NordestaoStoreOffersContent(null, 0, {}, loadOffers = { calls++; error("No store") }, resolveEan = { _, _ -> null })
        } }
        compose.onNodeWithText("Escolher loja").assertExists()
        org.junit.Assert.assertEquals(0, calls)
    }
    @Test fun changingFavoriteReplacesOffersAndKeepsTheRequestedStoreId() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/nordestao-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val stores = NordestaoStoresCodec.decode(raw).stores.take(2)
        val offersRaw = javaClass.classLoader!!.getResourceAsStream("stores/offers-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val offer = com.example.data.NordestaoStoreOffersCodec.decode(offersRaw, 16145121).offers.first().copy(
            startsAt = "2000-01-01T00:00:00.000Z", endsAt = "2100-01-01T00:00:00.000Z", image = "")
        val selected = androidx.compose.runtime.mutableStateOf(stores.first())
        val requests = mutableListOf<Long>()
        compose.setContent { MaterialTheme {
            NordestaoStoreOffersContent(selected.value, 0, {}, loadOffers = { id ->
                requests.add(id)
                com.example.data.NordestaoStoreOffersSnapshot(id, listOf(offer.copy(description = "Produto da loja $id")), "2026-10-23T16:00:00.000Z", false)
            }, resolveEan = { _, _ -> null })
        } }
        compose.onNodeWithText("Produto da loja ${stores[0].id}").assertExists()
        compose.runOnIdle { selected.value = stores[1] }
        compose.onNodeWithText("Produto da loja ${stores[1].id}").assertExists()
        compose.onNodeWithText("Produto da loja ${stores[0].id}").assertDoesNotExist()
        org.junit.Assert.assertEquals(stores.map { it.id }, requests.take(2))
    }
    @Test fun sharedOfferTextContainsExactCodesPriceValidityAndConditions() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/offers-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val offer = com.example.data.NordestaoStoreOffersCodec.decode(raw, 16145121).offers.first()
        val text = storeOfferSharingText(offer, "Nordestão Cidade Jardim", "7898377663071")
        org.junit.Assert.assertTrue(text.contains(offer.description))
        org.junit.Assert.assertTrue(text.contains("Loja: Nordestão Cidade Jardim"))
        org.junit.Assert.assertTrue(text.contains("Código: 2052914"))
        org.junit.Assert.assertTrue(text.contains("EAN: 7898377663071"))
        org.junit.Assert.assertTrue(text.contains("Preço: R$"))
        org.junit.Assert.assertTrue(text.contains("Válida até 23/10"))
        org.junit.Assert.assertTrue(text.contains("Ative a oferta"))
        val unknown = storeOfferSharingText(offer, "Nordestão Cidade Jardim", null)
        org.junit.Assert.assertFalse(unknown.contains("EAN:"))
    }

    @Test fun storeOfferDetailsExposeCopyShareAndCloseActions() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/nordestao-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val store = NordestaoStoresCodec.decode(raw).stores.first()
        val sampleRaw = javaClass.classLoader!!.getResourceAsStream("stores/offers-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val sample = com.example.data.NordestaoStoreOffersCodec.decode(sampleRaw, 16145121).offers.first().copy(
            startsAt = "2000-01-01T00:00:00.000Z", endsAt = "2100-01-01T00:00:00.000Z", image = "", images = emptyList())
        compose.setContent { MaterialTheme {
            NordestaoStoreOffersContent(store, 0, {}, loadOffers = { id ->
                com.example.data.NordestaoStoreOffersSnapshot(id, listOf(sample), "2026-10-23T16:00:00.000Z", false)
            }, resolveEan = { _, _ -> "7898377663071" })
        } }
        compose.onNodeWithText("Código: 2052914").assertExists()
        compose.onNodeWithText("EAN: 7898377663071").assertExists()
        compose.onNodeWithText("Ver condições").performClick()
        compose.onNodeWithText("Copiar").assertExists()
        compose.onNodeWithText("Compartilhar").assertExists()
        compose.onNodeWithText("Fechar").assertExists()
    }

    @Test fun storeOfferSelectorSortsAllFiveWaysAndLeavesUnknownPricesLast() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/offers-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val base = com.example.data.NordestaoStoreOffersCodec.decode(raw, 16145121).offers.first()
        val offers = listOf(base.copy(id="b", description="Banana", priceCents=500, discount=10),
            base.copy(id="a", description="Abacaxi", priceCents=200, discount=40),
            base.copy(id="c", description="Cenoura", priceCents=900, discount=20),
            base.copy(id="d", description="Damasco", priceCents=0, discount=0, showPrice=false))
        fun ids(option: OfferSortOption) = sortStoreOffers(offers, option).map { it.id }
        org.junit.Assert.assertEquals(listOf("a","b","c","d"), ids(OfferSortOption.NAME))
        org.junit.Assert.assertEquals(listOf("a","c","b","d"), ids(OfferSortOption.DISCOUNT_DESC))
        org.junit.Assert.assertEquals(listOf("d","b","c","a"), ids(OfferSortOption.DISCOUNT_ASC))
        org.junit.Assert.assertEquals(listOf("a","b","c","d"), ids(OfferSortOption.PRICE_ASC))
        org.junit.Assert.assertEquals(listOf("c","b","a","d"), ids(OfferSortOption.PRICE_DESC))
    }
}
