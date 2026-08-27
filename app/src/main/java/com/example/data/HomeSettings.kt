package com.example.data

/**
 * Valores publicados pelo Painel Mestre. Campos nulos mantêm compatibilidade
 * com documentos antigos que ainda não possuem a configuração da Home.
 */
data class RemoteHomeSettings(
    val showCategories: Boolean? = null,
    val showMostUsed: Boolean? = null,
    val showHistory: Boolean? = null,
    val showFavorites: Boolean? = null,
    val mostUsedLimit: Int? = null,
    val carouselIntervalSeconds: Int? = null
)

/**
 * Configuração efetiva usada pela Home depois de aplicar os padrões locais
 * quando uma opção remota ainda não foi publicada.
 */
data class HomeSettings(
    val showCategories: Boolean = true,
    val showMostUsed: Boolean = true,
    val showHistory: Boolean = true,
    val showFavorites: Boolean = true,
    val mostUsedLimit: Int = 8,
    val carouselIntervalSeconds: Int = 5
)
