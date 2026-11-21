package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LocalExpressiveStyle

@Composable
internal fun expressiveFunctionIconColor(label: String): Color {
    if (!LocalExpressiveStyle.current.enabled) return MaterialTheme.colorScheme.primary
    val text = label.lowercase(java.util.Locale.ROOT)
    return when {
        "perfil" in text -> Color(0xFF1976D2)
        "treinamento" in text -> Color(0xFF7B1FA2)
        "promo" in text -> Color(0xFFE53935)
        "encarte" in text -> Color(0xFF00897B)
        "preço" in text || "pesquis" in text -> Color(0xFF1565C0)
        "loja" in text -> Color(0xFF2E7D32)
        "sobre" in text -> Color(0xFF0288D1)
        "config" in text || "mestre" in text -> Color(0xFF5E35B1)
        "produto" in text || "catálogo" in text -> Color(0xFFEF6C00)
        "categoria" in text -> Color(0xFFE91E63)
        "backup" in text || "import" in text -> Color(0xFF00897B)
        "pend" in text || "sugest" in text -> Color(0xFFF57C00)
        "aba" in text -> Color(0xFF3949AB)
        "intelig" in text || "avançad" in text -> Color(0xFF7B1FA2)
        else -> Color(0xFF00838F)
    }
}
