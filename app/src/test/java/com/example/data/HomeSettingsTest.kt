package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Test

class HomeSettingsTest {

    @Test
    fun headerIcons_areHiddenByDefault() {
        val settings = HomeSettings()

        assertFalse(settings.showDrawerIcon)
        assertFalse(settings.showNotificationIcon)
    }
}
