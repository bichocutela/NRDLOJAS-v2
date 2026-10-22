package com.example.data

data class NotificationSettings(
    val enabled: Boolean = true,
    val productAddedEnabled: Boolean = true,
    val codeChangedEnabled: Boolean = true,
    val suggestionFixedEnabled: Boolean = true,
    val appUpdateEnabled: Boolean = true,
    val promotionUpdatedEnabled: Boolean = true
)
