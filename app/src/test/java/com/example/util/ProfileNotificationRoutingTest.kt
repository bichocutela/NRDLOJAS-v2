package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileNotificationRoutingTest {
    @Test
    fun profileEventsOpenTheMatchingSection() {
        assertEquals(ProfileNotificationRouting.SECTION_PROFILE, ProfileNotificationRouting.profileSection("PROFILE_UPDATED"))
        assertEquals(ProfileNotificationRouting.SECTION_HOURS, ProfileNotificationRouting.profileSection("HOURS_UPDATED"))
        assertEquals(ProfileNotificationRouting.SECTION_POINT, ProfileNotificationRouting.profileSection("POINT_UPDATED"))
        assertEquals(ProfileNotificationRouting.SECTION_BENEFIT, ProfileNotificationRouting.profileSection("BENEFIT_RELEASED"))
        assertEquals(ProfileNotificationRouting.SECTION_BENEFIT, ProfileNotificationRouting.profileSection("BENEFIT_PURCHASE"))
        assertEquals(ProfileNotificationRouting.SECTION_DAYS_OFF, ProfileNotificationRouting.profileSection("TODAY_OFF"))
        assertEquals(ProfileNotificationRouting.SECTION_DAYS_OFF, ProfileNotificationRouting.profileSection("TOMORROW_OFF"))
        assertEquals(ProfileNotificationRouting.SECTION_DAYS_OFF, ProfileNotificationRouting.profileSection("SCHEDULE_INSERTED"))
        assertEquals(ProfileNotificationRouting.SECTION_DAYS_OFF, ProfileNotificationRouting.profileSection("SCHEDULE_CHANGED"))
    }

    @Test
    fun unrelatedEventsDoNotOpenTheProfile() {
        assertEquals(null, ProfileNotificationRouting.profileSection("NEW_PRODUCT"))
        assertEquals(null, ProfileNotificationRouting.profileSection("PROMOTION_UPDATED"))
        assertEquals(null, ProfileNotificationRouting.profileSection("UNKNOWN"))
    }

    @Test
    fun profileNotificationTypesHaveDistinctIcons() {
        val types = listOf(
            "PROFILE_UPDATED", "HOURS_UPDATED", "POINT_UPDATED",
            "BENEFIT_RELEASED", "BENEFIT_PURCHASE", "TODAY_OFF",
            "TOMORROW_OFF", "SCHEDULE_INSERTED", "SCHEDULE_CHANGED"
        )
        val iconIds = types.map(ProfileNotificationRouting::iconRes)
        assertEquals("Every profile notification must have its own icon", iconIds.size, iconIds.toSet().size)
        assertNotEquals(ProfileNotificationRouting.iconRes("HOURS_UPDATED"), ProfileNotificationRouting.iconRes("POINT_UPDATED"))
    }
}
