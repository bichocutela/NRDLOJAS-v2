package com.example.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.NordestaoStoresCodec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class NordestaoStoresScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchOpensStoreWithRealAddressPhoneAndMissingHoursLabel() {
        val raw = javaClass.classLoader!!.getResourceAsStream("stores/nordestao-observed-2026-10-23.json")!!.bufferedReader().use { it.readText() }
        compose.setContent { MaterialTheme { NordestaoStoresScreen({}, { NordestaoStoresCodec.decode(raw) }) } }
        compose.onNodeWithText("Buscar loja, bairro ou cidade").performTextInput("igapo")
        compose.onNodeWithText("Nordestão Igapó").performClick()
        compose.onNodeWithText("Horário de funcionamento").assertExists()
        compose.onNodeWithText("Não informado pela fonte. Consulte a unidade antes de se deslocar.").assertExists()
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
        val html = storesMapHtml(listOf(store))
        org.junit.Assert.assertFalse(html.contains("</script><img"))
        org.junit.Assert.assertTrue(html.contains("textContent=s.name"))
        org.junit.Assert.assertTrue(html.contains("OpenStreetMap"))
        org.junit.Assert.assertEquals(0.0, storeDistance(-5.8,-35.2,-5.8,-35.2),0.001)
        org.junit.Assert.assertTrue(storeDistance(-5.8,-35.2,-5.9,-35.3) > 10.0)
    }
}
