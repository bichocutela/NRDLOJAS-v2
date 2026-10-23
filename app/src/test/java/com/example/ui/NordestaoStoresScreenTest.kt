package com.example.ui

import android.app.Application
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
    @Test fun hoursMatchEveryNamedStoreAndDoNotInventMossoroHours() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/nordestao-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        val stores = NordestaoStoresCodec.decode(raw).stores
        org.junit.Assert.assertEquals(10, stores.count { it.hoursFromLeaflet })
        stores.forEach { store ->
            if(store.name.contains("Doze Anos") || store.name.contains("Abolição")) org.junit.Assert.assertEquals("",store.displayHours)
            else if(store.name.contains("Alecrim") || store.name.contains("Petrópolis") || store.name.contains("Igapó") || store.name.contains("Rota do Sol")) org.junit.Assert.assertTrue(store.displayHours.contains("Domingos e feriados: 07h às 20h"))
            else org.junit.Assert.assertTrue(store.displayHours.contains("Feriados: 07h às 22h"))
        }
        val official = stores.first().copy(businessHours="Horário especial da fonte")
        org.junit.Assert.assertEquals("Horário especial da fonte",official.displayHours)
        org.junit.Assert.assertFalse(official.hoursFromLeaflet)
    }

}
