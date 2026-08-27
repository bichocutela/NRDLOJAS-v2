package com.example.data

data class ThemeBackground(
    val id: String,
    val label: String,
    val url: String,
    val isActive: Boolean = false
)

val SupportedThemeKeys = listOf("multicolor", "red", "gold", "green", "blue", "orange")
