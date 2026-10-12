package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.util.NotificationAppearance

@Composable
fun DynamicNotificationSettings() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationAppearance.enabled(context)) }
    var source by remember { mutableStateOf(NotificationAppearance.source(context)) }
    var style by remember { mutableStateOf(NotificationAppearance.style(context)) }
    fun save() = NotificationAppearance.save(context, enabled, source, style)
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("Notificações dinâmicas", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Usar a interface nativa do aparelho", Modifier.weight(1f))
        Switch(enabled, { enabled = it; save() })
    }
    Text("A apresentação depende da marca, da versão do sistema e das permissões. Avisos de produtos continuam disponíveis na notificação normal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (enabled) {
        NotificationChoice("Aparência", source, listOf("app" to "Seguir o NRD", "system" to "Claro/escuro do celular")) { source = it; save() }
        NotificationChoice("Estilo do cartão", style, listOf("solid" to "Sólido", "tonal" to "Tonal NRD", "gradient" to "Gradiente", "glass" to "Glass Soft")) { style = it; save() }
        val palette = NotificationAppearance.palette(context)
        val background = Color(palette.background)
        val end = if (style in setOf("gradient", "glass")) Color(palette.accent).copy(alpha = .10f) else Color.Transparent
        Surface(shape = RoundedCornerShape(26.dp), color = background, contentColor = Color(palette.foreground)) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(end, Color.Transparent))).padding(18.dp)) {
                Text("NRD · Produto adicionado", style = MaterialTheme.typography.titleSmall)
                Text("Descrição do produto", style = MaterialTheme.typography.bodyMedium)
                Text("Código · Ir pro app", style = MaterialTheme.typography.labelMedium)
            }
        }
        Text("Prévia do cartão. Cores e estilos são aplicados onde o sistema permite. Glass Soft usa um brilho visual; a transparência real depende do aparelho.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotificationChoice(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(options.firstOrNull { it.first == selected }?.second.orEmpty(), {}, readOnly = true,
            label = { Text(label) }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(key); expanded = false }) }
        }
    }
}
