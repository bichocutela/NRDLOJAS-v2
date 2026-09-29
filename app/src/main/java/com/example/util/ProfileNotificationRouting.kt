package com.example.util

import com.example.R

/**
 * Shared destination and icon mapping for notification history and Android system notifications.
 */
object ProfileNotificationRouting {
    const val SECTION_PROFILE = "profile"
    const val SECTION_HOURS = "hours"
    const val SECTION_POINT = "point"
    const val SECTION_BENEFIT = "benefit"
    const val SECTION_DAYS_OFF = "days_off"

    fun profileSection(type: String): String? = when (type) {
        "PROFILE_UPDATED" -> SECTION_PROFILE
        "HOURS_UPDATED" -> SECTION_HOURS
        "POINT_UPDATED" -> SECTION_POINT
        "BENEFIT_RELEASED", "BENEFIT_PURCHASE" -> SECTION_BENEFIT
        "TODAY_OFF", "TOMORROW_OFF", "SCHEDULE_INSERTED", "SCHEDULE_CHANGED" -> SECTION_DAYS_OFF
        else -> null
    }

    fun iconRes(type: String): Int = when (type) {
        "NEW_PRODUCT" -> R.drawable.ic_notification_product_added
        "CODE_CHANGED" -> R.drawable.ic_notification_code_changed
        "SUGGESTION_FIXED" -> R.drawable.ic_notification_suggestion_fixed
        "PROMOTION_UPDATED" -> R.drawable.ic_notification_promotion
        "APP_UPDATE" -> R.drawable.ic_notification_app_update
        "PROFILE_UPDATED" -> R.drawable.ic_notification_profile
        "HOURS_UPDATED" -> R.drawable.ic_notification_hours
        "POINT_UPDATED" -> R.drawable.ic_notification_point_updated
        "BENEFIT_RELEASED" -> R.drawable.ic_notification_benefit_released
        "BENEFIT_PURCHASE" -> R.drawable.ic_notification_benefit_purchase
        "TODAY_OFF" -> R.drawable.ic_notification_today_off
        "TOMORROW_OFF" -> R.drawable.ic_notification_tomorrow_off
        "SCHEDULE_INSERTED" -> R.drawable.ic_notification_schedule_inserted
        "SCHEDULE_CHANGED" -> R.drawable.ic_notification_schedule_changed
        "NEW_INSTALLATION" -> R.drawable.ic_notification_new_installation
        else -> R.drawable.ic_notification_default
    }
}
