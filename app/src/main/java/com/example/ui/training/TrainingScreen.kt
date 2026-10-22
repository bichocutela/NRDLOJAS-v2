package com.example.ui.training

import android.view.SoundEffectConstants
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.training.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(config: TrainingConfig, onBack: () -> Unit, preview: Boolean = false) {
    val missions = config.missions.filter { it.enabled }
    var opened by remember { mutableStateOf(false) }
    var state by remember(config.revision) { mutableStateOf(TrainingState(missions.firstOrNull()?.id.orEmpty())) }
    androidx.activity.compose.BackHandler(enabled = opened) { opened = false }
    val mission = missions.find { it.id == state.missionId } ?: missions.firstOrNull()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    Scaffold(topBar = { TopAppBar(title = { Text(if (preview) "Treinamento — prévia" else "Academia NRD") }, navigationIcon = {
        IconButton(onClick = { if (opened) opened = false else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
    }, actions = { if (!preview) TrainingNotificationBell(config.visibility == TrainingVisibility.ALL) }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (config.visibility == TrainingVisibility.TEST) Text("Modo Teste — somente o Mestre")
            if (!opened) {
                Text("Treinamento de Caixa", style = MaterialTheme.typography.headlineMedium)
                Text("Pratique as rotinas do caixa com o teclado do PDV, visor do operador e visor do cliente.")
                Text("Aprender: siga as instruções. Prova: teste sua sequência. Caixa Livre: explore as teclas.")
                Button(onClick = { opened = true }, enabled = mission != null, modifier = Modifier.fillMaxWidth()) { Text("Abrir simulador") }
                missions.forEach { m ->
                    OutlinedCard(onClick = { state = TrainingState(m.id, completed = state.completed); opened = true }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text((if (m.id in state.completed) "✓ " else "") + m.title, fontWeight = FontWeight.Bold)
                            Text(m.subtitle)
                            Text("${m.steps.size} etapas")
                        }
                    }
                }
            } else if (mission != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrainingMode.entries.forEach { mode ->
                        FilterChip(selected = state.mode == mode, onClick = { state = TrainingState(mission.id, mode, completed = state.completed) },
                            label = { Text(when (mode) { TrainingMode.LEARN -> "Aprender"; TrainingMode.EXAM -> "Prova"; TrainingMode.FREE -> "Caixa Livre" }) })
                    }
                }
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(mission.title) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    missions.forEach { m -> DropdownMenuItem(text = { Text(m.title) }, onClick = { expanded = false; state = TrainingState(m.id, completed = state.completed) }) }
                }
                if (state.mode == TrainingMode.LEARN) {
                    CashierLearning(config)
                } else {
                if (state.mode != TrainingMode.FREE) {
                    LinearProgressIndicator(progress = { if (state.finished) 1f else state.step.toFloat() / mission.steps.size }, modifier = Modifier.fillMaxWidth())
                    Text("${if (state.finished) mission.steps.size else state.step + 1} de ${mission.steps.size} • ${state.errors} erro(s)")
                    if (state.finished) Text("Missão concluída! " + "★".repeat(if (state.errors == 0) 3 else if (state.errors <= 2) 2 else 1), fontWeight = FontWeight.Bold)
                    else if (state.mode == TrainingMode.LEARN) Text(mission.steps[state.step].instruction)
                }
                val key = config.keys.find { it.id == state.displayKey }
                val step = mission.steps.getOrNull(state.step)
                val operator = when { key != null -> key.operatorText; state.finished && state.mode != TrainingMode.FREE -> "MISSÃO CONCLUÍDA"; state.mode == TrainingMode.FREE -> "FECHADO PARA BALANÇA"; else -> step?.operatorText.orEmpty() }
                val customer = when { key != null -> key.customerText; state.finished && state.mode != TrainingMode.FREE -> "TREINAMENTO CONCLUÍDO"; state.mode == TrainingMode.FREE -> "CAIXA FECHADO"; else -> step?.customerText.orEmpty() }
                TrainingDisplay(TrainingEngine.render(operator, state, key, mission), key?.operatorImage ?: step?.operatorImage.orEmpty(), false)
                TrainingDisplay(TrainingEngine.render(customer, state, key, mission), key?.customerImage ?: step?.customerImage.orEmpty(), true)
                val press: (String) -> Unit = { id ->
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val next = TrainingEngine.press(config, state, id)
                    if (next.errors > state.errors) scope.launch {
                        snackbar.showSnackbar(if (state.mode == TrainingMode.EXAM) "Sequência incorreta. A missão foi reiniciada." else "Tente a tecla ${config.keys.find { it.id == step?.keyId }?.label.orEmpty()}.")
                    }
                    state = next
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyboardBlock(config.left, config, Color(0xFFBAD47A), Modifier.weight(1f), press)
                    KeyboardBlock(config.right, config, Color(0xFF35404A), Modifier.weight(1f), press, true)
                }
                KeyboardBlock(config.bottom, config, Color(0xFF93C4E3), Modifier.fillMaxWidth(), press)
                TextButton(onClick = { state = TrainingEngine.reset(state) }) { Text("Reiniciar missão") }
                if (state.history.isNotEmpty()) Text(state.history.take(6).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Simulador educativo. Sequências demonstrativas: confirme os procedimentos oficiais da loja. Nenhuma operação real é realizada no PDV.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun TrainingDisplay(text: String, image: String, customer: Boolean) {
    Column(Modifier.fillMaxWidth().background(if (customer) Color(0xFF101820) else Color(0xFFC8D77D), RoundedCornerShape(12.dp)).padding(16.dp)) {
        Text(if (customer) "VISOR DO CLIENTE" else "VISOR DO OPERADOR", color = if (customer) Color(0xFFE8F8FF) else Color(0xFF526126), fontSize = 11.sp)
        Text(text, color = if (customer) Color(0xFFE8F8FF) else Color(0xFF526126), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.heightIn(min = 50.dp))
        if (image.isNotBlank()) AsyncImage(model = TrainingImageUrl.normalize(image), contentDescription = "Imagem do visor", modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp))
    }
}

@Composable
internal fun KeyboardBlock(rows: List<List<String>>, config: TrainingConfig, color: Color, modifier: Modifier, onPress: (String) -> Unit, numeric: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { id ->
                    val key = config.keys.firstOrNull { it.id == id }
                    val background = if (numeric && index == 0) Color(0xFFF2D464) else color
                    Button(onClick = { onPress(id) }, enabled = !key?.label.isNullOrBlank(), modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                        shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(3.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = if (numeric && index > 0) Color.White else Color(0xFF15202B))) {
                        Text(key?.label.orEmpty(), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
