package com.example.data

data class AssistantSettings(
    val enabled: Boolean = true,
    val catalogOnly: Boolean = true,
    val welcomeMessage: String = "Olá! Posso ajudar a encontrar produtos e códigos no catálogo.",
    val maxContextProducts: Int = 25
)
