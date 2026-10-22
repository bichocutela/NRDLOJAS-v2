package com.example.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProductPhotoViewerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun imageTapOpensPopupAndCloseDismissesIt() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(48.dp).testTag("thumbnail")
                    .expandProductPhoto("content://nrd-test/product", "Coca-Cola"))
            }
        }
        compose.onNodeWithTag("thumbnail").performClick()
        compose.onNodeWithTag("product-photo-viewer").assertExists()
        compose.onNodeWithContentDescription("Foto ampliada de Coca-Cola").assertExists()
        compose.onNodeWithTag("product-photo-canvas").performTouchInput {
            pinch(center - Offset(30f, 0f), center + Offset(30f, 0f),
                center - Offset(90f, 0f), center + Offset(90f, 0f))
        }
        compose.onNodeWithTag("product-photo-canvas").assert(SemanticsMatcher("Zoom ampliado") {
            it.config.getOrNull(SemanticsProperties.StateDescription) != "Zoom 100%"
        })
        compose.onNodeWithText("Redefinir").performClick()
        compose.onNodeWithTag("product-photo-canvas").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
        compose.onNodeWithContentDescription("Fechar imagem").performClick()
        compose.onNodeWithTag("product-photo-viewer").assertDoesNotExist()
    }

    @Test fun absentPhotoDoesNotExposeAnExpansionAction() {
        compose.setContent {
            Box(Modifier.size(48.dp).testTag("thumbnail").expandProductPhoto(null, "Sem foto"))
        }
        compose.onNodeWithTag("thumbnail").assertHasNoClickAction()
        compose.onNodeWithTag("product-photo-viewer").assertDoesNotExist()
    }

    @Test fun pinchAndPanStayWithinBoundsAndZoomOutResetsPosition() {
        val viewport = IntSize(200, 300)
        val zoomed = ProductPhotoTransform().update(2f, Offset(999f, -999f), viewport)
        assertEquals(2f, zoomed.scale)
        assertEquals(Offset(100f, -150f), zoomed.offset)
        assertEquals(5f, zoomed.update(100f, Offset.Zero, viewport).scale)
        assertEquals(ProductPhotoTransform(), zoomed.update(0.01f, Offset(50f, 50f), viewport))
        assertEquals(ProductPhotoTransform(), ProductPhotoTransform().update(Float.NaN, Offset(Float.NaN, Float.NaN), viewport))
    }
}
