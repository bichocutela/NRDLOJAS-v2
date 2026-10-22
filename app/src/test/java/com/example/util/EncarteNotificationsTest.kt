package com.example.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class EncarteNotificationsTest {
    private val data = mapOf("encarteId" to "66738", "encarteName" to "Encarte Nordestão Saudável",
        "startDate" to "2026-10-22T00:00:00", "endDate" to "2026-10-31T23:59:59")
    private val now = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear(); set(2026, 9, 22, 12, 0, 0)
    }.timeInMillis
    @Test fun onePersistedModeReplacesTheOtherAndDefaultsToOff() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("encarte_notifications", 0).edit().clear().commit()
        assertEquals(EncarteNotifications.Mode.OFF, EncarteNotifications.mode(context))
        EncarteNotifications.saveMode(context, EncarteNotifications.Mode.SIMPLE)
        assertEquals(EncarteNotifications.Mode.SIMPLE, EncarteNotifications.mode(context))
        EncarteNotifications.saveMode(context, EncarteNotifications.Mode.DETAILED)
        assertEquals(EncarteNotifications.Mode.DETAILED, EncarteNotifications.mode(context))
        EncarteNotifications.saveMode(context, EncarteNotifications.Mode.OFF)
        assertNull(EncarteNotifications.content(EncarteNotifications.mode(context), data, now))
    }
    @Test fun simpleAndDetailedProduceExactlyOneDifferentMessage() {
        val simple = EncarteNotifications.content(EncarteNotifications.Mode.SIMPLE, data, now)!!
        assertEquals("Novo encarte disponível", simple.first)
        assertFalse(simple.second.contains(data.getValue("encarteName")))
        val detailed = EncarteNotifications.content(EncarteNotifications.Mode.DETAILED, data, now)!!
        assertEquals("Novo Encarte: Encarte Nordestão Saudável", detailed.first)
        assertTrue(detailed.second.contains("22/10 a 31/10"))
        assertEquals("encartes", NotificationHelper.channelIdForType("NEW_ENCARTE"))
    }
    @Test fun expiredFutureInvalidAndDuplicateEventsAreRejected() {
        assertNull(EncarteNotifications.content(EncarteNotifications.Mode.SIMPLE, data + ("endDate" to "2026-10-21T23:59:59"), now))
        assertNull(EncarteNotifications.content(EncarteNotifications.Mode.SIMPLE, data + ("startDate" to "2026-10-23T00:00:00"), now))
        assertNull(EncarteNotifications.content(EncarteNotifications.Mode.DETAILED, data + ("encarteId" to "bad"), now))
        val context = ApplicationProvider.getApplicationContext<Application>()
        EncarteNotifications.markDelivered(context, "66738")
        assertTrue(EncarteNotifications.alreadyDelivered(context, "66738"))
        assertFalse(EncarteNotifications.alreadyDelivered(context, "66739"))
    }
}
