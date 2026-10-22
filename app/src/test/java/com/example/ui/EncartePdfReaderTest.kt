package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class EncartePdfReaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readerUsesRemainingHeightAndPinchZoomCanBeReset() {
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        compose.setContent { MaterialTheme { EncartePageCanvas(bitmap, 0, Modifier.requiredSize(320.dp, 720.dp)) } }
        val canvas = compose.onNodeWithTag("encarte-page-canvas")
        // Regression: no 620dp cap and no empty oversized card below the page.
        canvas.assertHeightIsAtLeast(650.dp)
        canvas.performTouchInput {
            pinch(center - Offset(30f, 0f), center + Offset(30f, 0f),
                center - Offset(90f, 0f), center + Offset(90f, 0f))
        }
        canvas.assert(SemanticsMatcher("Página ampliada") {
            it.config[SemanticsProperties.StateDescription] != "Zoom 100%"
        })
        compose.onNodeWithText("Ajustar").performClick()
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
    }

    @Test fun doubleTapZoomsAtTappedAreaAndSecondDoubleTapResets() {
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        compose.setContent { MaterialTheme { EncartePageCanvas(bitmap, 0, Modifier.requiredSize(320.dp, 480.dp)) } }
        val canvas = compose.onNodeWithTag("encarte-page-canvas")
        canvas.performTouchInput { doubleClick(center * 0.5f) }
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 250%"))
        canvas.performTouchInput { doubleClick(center) }
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
    }

    @Test fun landscapeUsesAllAvailableHeight() {
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        compose.setContent { MaterialTheme { EncartePageCanvas(bitmap, 0, Modifier.requiredSize(720.dp, 320.dp)) } }
        compose.onNodeWithTag("encarte-page-canvas").assertHeightIsAtLeast(250.dp)
    }

    @Test fun pinchPreservesFocusAndPanIsClampedToFittedPage() {
        val viewport = IntSize(400, 800)
        val image = IntSize(400, 600)
        val zoom = EncarteTransform().update(2f, Offset.Zero, Offset(100f, 200f), viewport, image)
        assertEquals(Offset(100f, 200f), zoom.offset)
        val moved = zoom.update(1f, Offset(9999f, -9999f), Offset(200f, 400f), viewport, image)
        assertEquals(Offset(200f, -200f), moved.offset)
        assertEquals(6f, moved.update(100f, Offset.Zero, Offset.Zero, viewport, image).scale)
        assertEquals(EncarteTransform(), moved.update(0.01f, Offset.Zero, Offset.Zero, viewport, image))
        assertEquals(EncarteTransform(), EncarteTransform().update(Float.NaN, Offset.Zero, Offset.Zero, viewport, image))
    }
}
