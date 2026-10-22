package com.example.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.training.*

@Composable
internal fun CashierLearning(config: TrainingConfig) {
    var session by remember { mutableStateOf(CashierSession()) }
    var enlarged by remember { mutableStateOf(false) }
    val press: (String) -> Unit = { session = CashierSimulation.press(session, it, java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Fortaleza")).get(java.util.Calendar.HOUR_OF_DAY)) }
    val customer = CashierSimulation.customer(session)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("APRENDER", fontWeight = FontWeight.Bold)
            Text("Observe os dois visores e use as teclas do caixa.")
        }
        OutlinedCard(onClick = { enlarged = true }, modifier = Modifier.width(144.dp).aspectRatio(1f)) {
            Column(Modifier.padding(8.dp)) {
                Text("VISOR DO CLIENTE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(customer, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 7, modifier = Modifier.weight(1f))
                if (session.page == CashierPage.QR) PracticeQr(Modifier.size(40.dp))
                Text("Toque para ampliar", fontSize = 10.sp)
            }
        }
    }
    if(enlarged) Dialog(onDismissRequest = { enlarged = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(16.dp)) {
                Text("VISOR DO CLIENTE", fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Text(customer)
                    if (session.page == CashierPage.QR) PracticeQr(Modifier.fillMaxWidth().aspectRatio(1f))
                }
                TextButton(onClick = { enlarged = false }) { Text("Fechar visor ampliado") }
            }
        }
    }
    TrainingDisplay(CashierSimulation.operator(session), "", false)
    Text(when(session.page) {
        CashierPage.AVAILABLE -> "Pressione ENTRAR, a tecla amarela, para iniciar."
        CashierPage.CLUB, CashierPage.LOYALTY -> "Pergunte ao cliente e escolha 1 — Sim ou 3 — Não."
        CashierPage.CLUB_METHOD -> "Escolha 1 — Magnética, 2 — Cartão ou 3 — CPF."
        CashierPage.IDENTIFY -> "Pergunte se deseja Nota Potiguar. 1 — CPF na nota, 3 — CNPJ na nota, 0 — Sair e abrir/retomar a nota."
        CashierPage.SALE -> "Digite um código dos balões e pressione ENTRAR. TOTAL encerra os itens; ENTRAR sem código identifica o cliente; FUNÇÃO + 514 abre Nordestão pra Você."
        CashierPage.PARKING -> "Bipe o cartão de estacionamento ou pressione ANULA se o cliente não tiver."
        CashierPage.RECEIVE -> "Dinheiro: digite o valor recebido em centavos (5000 = R$ 50,00) e pressione DINHEIRO. Para cartões ou Pix, pressione TEF."
        CashierPage.TEF, CashierPage.DEBIT_FORM, CashierPage.DEBIT_TYPE, CashierPage.CREDIT_FORM, CashierPage.CREDIT_TYPE, CashierPage.WALLET -> "Olhe o visor do cliente: toque nele para ampliar e escolher a opção correta. Cartões alimentação também podem seguir o caminho débito, conforme o roteiro da loja."
        CashierPage.CPF, CashierPage.CNPJ -> "Peça ao cliente para digitar o documento no PINpad. Use o botão de simulação abaixo; não informe dados reais."
        CashierPage.CUSTOMER_NAME -> "Diga o nome do cliente: ${session.customerName}. Pressione ENTRAR para praticar a pergunta sobre ativação de ofertas."
        CashierPage.OFFERS_CONFIRM -> "${session.customerName}, você já ativou as ofertas? Faça essa pergunta antes de perguntar se deseja Nota Potiguar. Esta é uma orientação verbal; o simulador não ativa ofertas reais."
        CashierPage.MISSING -> "${session.customerName}, procurou tudo que procurava ou faltou algum produto? Se faltou, dê a nota e anote nome, telefone e produto. Esta é uma orientação verbal, sem tecla numérica atribuída."
        CashierPage.MISSING_FORM -> "Pratique a anotação usando dados fictícios. Anote nome, telefone e o produto que faltou antes de seguir."
        CashierPage.UNREGISTERED -> "Cliente não cadastrado. Pressione ENTRAR para simular a próxima tela de telefone."
        CashierPage.PHONE -> "Peça DDD + celular no PINpad e informe: você receberá um SMS para concluir o cadastro em casa."
        CashierPage.CNPJ_ERROR -> "Cliente desistiu do CNPJ: digite 0 para sair. O retorno à identificação é demonstrativo, ainda a confirmar."
        CashierPage.CARD -> "Peça para aproximar, inserir ou passar o cartão. ANULA simula o pedido de cancelamento."
        CashierPage.QR -> "QR demonstrativo no visor do cliente. Aguarde a autorização; ANULA abre a confirmação de cancelamento."
        CashierPage.PIX_CHANGE -> "Pix Troco até as 20h: digite em centavos o troco solicitado e pressione ENTRAR."
        CashierPage.PIX_CONFIRM -> "Peça ao cliente para confirmar o troco no PINpad."
        CashierPage.CANCEL_CARD -> "Cancelar pagamento? 0 — Sim; 1 — Não, tentar o cartão novamente."
        CashierPage.CANCEL_PIX -> "Cancelar QR? 1 — Sim; 2 — Não."
        CashierPage.AUTHORIZED -> "Pagamento autorizado na simulação. Pressione ENTRAR para avançar ao cupom."
        CashierPage.RECEIPT -> "1 — Cupom resumido; 3 — Cupom detalhado."
        CashierPage.DONATION -> "Somente em dinheiro: 1 — Sim; 3 — Não. O restante da doação ainda não foi informado."
        CashierPage.DONE -> "Confira o cupom e o troco. ENTRAR inicia outro atendimento."
        CashierPage.FUNCTION -> "Digite 514 para abrir Nordestão pra Você."
        CashierPage.LOYALTY_CPF -> "1 — CPF; 0 — Sair para Identifica Cliente."
        CashierPage.STAMPS -> "Etapa condicional de campanha. Texto e cálculo de selos ainda aguardam referência. ENTRAR continua."
        else -> "Use o controle abaixo para simular a leitura do cartão."
    })
    if(session.notice.isNotBlank()) Text(session.notice, color = MaterialTheme.colorScheme.primary)
    if(session.page == CashierPage.AVAILABLE) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = !session.registered, onCheckedChange = { session = session.copy(registered = !it) })
            Text("Praticar CPF não cadastrado")
        }
        Text("Preços fictícios de treino; itens a 1 unidade. Selos e descontos aguardam regras e valores informados.", fontSize = 12.sp)
    }
    if(session.page == CashierPage.SALE) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CashierSimulation.products.forEach { p -> SuggestionChip(onClick = { session = session.copy(input = p.code) }, label = { Text("${p.name}\n${p.code}", fontSize = 10.sp) }) }
    }
    when(session.page) {
        CashierPage.OFFERS_CONFIRM -> Button(onClick = { press("SIM_ASKED") }) { Text("Pergunta sobre ofertas realizada") }
        CashierPage.MISSING -> {
            Button(onClick = { press("SIM_MISSING") }) { Text("Cliente informa que faltou produto") }
            OutlinedButton(onClick = { press("SIM_FOUND") }) { Text("Cliente encontrou tudo") }
        }
        CashierPage.MISSING_FORM -> {
            OutlinedTextField(value = session.missingName, onValueChange = { session = session.copy(missingName = it.take(100)) }, label = { Text("Nome fictício") })
            OutlinedTextField(value = session.missingPhone, onValueChange = { session = session.copy(missingPhone = it.take(20)) }, label = { Text("Telefone fictício") })
            OutlinedTextField(value = session.missingProduct, onValueChange = { session = session.copy(missingProduct = it.take(160)) }, label = { Text("Produto que faltou") })
            Button(onClick = { press("SIM_SAVE_MISSING") }) { Text("Concluir anotação de treino") }
        }
        CashierPage.CPF, CashierPage.CNPJ, CashierPage.PHONE, CashierPage.PIX_CONFIRM -> Button(onClick = { press("SIM_PIN") }) { Text("Simular confirmação no PINpad") }
        CashierPage.MAGNETIC, CashierPage.AGREEMENT, CashierPage.PARKING -> Button(onClick = { press("SIM_CARD") }) { Text("Simular leitura do cartão") }
        CashierPage.CARD, CashierPage.QR -> Button(onClick = { press("SIM_APPROVE") }) { Text("Simular pagamento autorizado") }
        else -> Unit
    }
    val keyboardPress: (String) -> Unit = { id ->
        config.keys.find { it.id == id }?.let { key -> press(key.label.replace("\n", " ")) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyboardBlock(config.left, config, Color(0xFFBAD47A), Modifier.weight(1f), keyboardPress)
        KeyboardBlock(config.right, config, Color(0xFF35404A), Modifier.weight(1f), keyboardPress, true)
    }
    KeyboardBlock(config.bottom, config, Color(0xFF93C4E3), Modifier.fillMaxWidth(), keyboardPress)
    TextButton(onClick = { session = CashierSession() }) { Text("Reiniciar atendimento") }
}

@Composable
private fun PracticeQr(modifier: Modifier) {
    val bitmap = remember {
        val matrix = com.google.zxing.MultiFormatWriter().encode(
            "NRD TREINAMENTO - SIMULACAO SEM PAGAMENTO", com.google.zxing.BarcodeFormat.QR_CODE, 256, 256)
        android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 256) for (x in 0 until 256)
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }.asImageBitmap()
    }
    Image(bitmap, contentDescription = "QR demonstrativo de treinamento, sem pagamento", modifier = modifier)
}
