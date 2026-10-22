package com.example.ui.training

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.training.TrainingRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TrainingScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun hubOpensNativeSimulatorWithAllThreeModesAndBothDisplays() {
        val config = TrainingRepository.defaults(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { MaterialTheme { TrainingScreen(config, {}, preview = true) } }
        compose.onNodeWithText("Treinamento de Caixa").assertExists()
        compose.onNodeWithText("Abrir simulador").performClick()
        compose.onNodeWithText("Aprender").assertExists()
        compose.onNodeWithText("Prova").performClick()
        compose.onNodeWithText("Caixa Livre").performClick()
        compose.onNodeWithText("VISOR DO OPERADOR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("VISOR DO CLIENTE").performScrollTo().assertIsDisplayed()
    }
    @Test fun aprenderStartsAvailableAndCustomerDisplayExpands() {
        val config = TrainingRepository.defaults(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { MaterialTheme { TrainingScreen(config, {}, preview = true) } }
        compose.onNodeWithText("Abrir simulador").performClick()
        compose.onNodeWithText("DISPONIVEL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Toque para ampliar").performScrollTo().performClick()
        compose.onNodeWithText("Fechar visor ampliado").assertIsDisplayed().performClick()
        compose.onNodeWithText("DISPONIVEL").assertExists()
    }
}
