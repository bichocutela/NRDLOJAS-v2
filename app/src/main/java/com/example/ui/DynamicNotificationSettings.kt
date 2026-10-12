package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
        NotificationChoice("Cor dos destaques", source, listOf("app" to "Tema do NRD", "system" to "Tema do celular")) { source = it; save() }
        NotificationChoice("Destaque do botão", style, listOf("solid" to "Neutro", "tonal" to "Tonal NRD", "gradient" to "Suave", "glass" to "Discreto")) { style = it; save() }
        val palette = NotificationAppearance.palette(context)
        val background = Color(palette.background)
        Surface(shape = RoundedCornerShape(26.dp), color = background, contentColor = Color(palette.foreground)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("NRD · Produto adicionado", style = MaterialTheme.typography.titleSmall)
                Text("Descrição do produto", style = MaterialTheme.typography.bodyMedium)
                Text("Código: 20279", style = MaterialTheme.typography.labelMedium,
                    color = Color(com.example.util.NativeNotificationColors.text(palette.dark).body))
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(20.dp),
                    color = Color(com.example.util.NativeNotificationColors.button(palette.dark, palette.accent, style)),
                    contentColor = Color(palette.foreground)) {
                    Text("Ir pro app", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text("O fundo e as letras seguem a superfície nativa. Na HyperIsland expandida, o fundo permanece escuro. O tema escolhido personaliza apenas os destaques permitidos pelo sistema.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
