package com.example.data.flyer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VisualMixReviewStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val a = FlyerAnalysisResult("a.pdf", null, null, emptyList(), emptyList(), "pdf", "a.pdf")
    private val b = a.copy(name = "b.pdf", sourceLabel = "b.pdf")

    @Before fun reset() {
        context.getSharedPreferences("visual_mix_review", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun finalizationSurvivesReloadAndPreservesOtherDocumentAndConfirmedOffers() {
        val offer = FlyerOffer(type = FlyerOfferType.DE_POR, sourceDescription = "Produto")
        VisualMixReviewStore.markConfirmed(context, offer)
        VisualMixReviewStore.saveSession(context, "a", a, setOf("a"))
        VisualMixReviewStore.saveSession(context, "b", b, setOf("b"))
        VisualMixReviewStore.saveDraft(context, a)
        assertTrue(VisualMixReviewStore.finalizeSession(context, "a", a))
        assertEquals(listOf("b"), VisualMixReviewStore.loadSessions(context).map { it.first })
        assertFalse(VisualMixReviewStore.hasDraft(context))
        assertEquals("a", VisualMixReviewStore.finalizedSessions(context).single().first)
        assertTrue(VisualMixReviewStore.isConfirmed(context, offer))
        assertTrue(VisualMixReviewStore.reopenSession(context, "a", a))
        assertTrue(VisualMixReviewStore.finalizedSessions(context).isEmpty())
        assertEquals(setOf("a", "b"), VisualMixReviewStore.loadSessions(context).map { it.first }.toSet())
    }

    @Test fun finalizingAnotherDocumentPreservesCurrentDraft() {
        VisualMixReviewStore.saveDraft(context, b)
        assertTrue(VisualMixReviewStore.finalizeSession(context, "a", a))
        assertEquals("b.pdf", VisualMixReviewStore.loadDraft(context)?.name)
    }
}
