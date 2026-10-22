package com.example.data.training

import java.util.Locale

/** Local educational state only. No payment, CPF lookup or SMS is sent. */
enum class CashierPage { AVAILABLE, CLUB, CLUB_METHOD, MAGNETIC, AGREEMENT, CPF, CNPJ, CNPJ_ERROR, UNREGISTERED, PHONE, CUSTOMER_NAME, OFFERS_CONFIRM, MISSING, MISSING_FORM, LOYALTY, LOYALTY_CPF, IDENTIFY, SALE, FUNCTION, PARKING, STAMPS, RECEIVE, TEF, DEBIT_FORM, DEBIT_TYPE, CREDIT_FORM, CREDIT_TYPE, WALLET, PIX_CHANGE, PIX_CONFIRM, CARD, QR, AUTHORIZED, CANCEL_CARD, CANCEL_PIX, RECEIPT, DONATION, DONE }
data class PracticeItem(val code: String, val name: String, val cents: Long)
data class CashierSession(
    val page: CashierPage = CashierPage.AVAILABLE, val input: String = "",
    val items: List<PracticeItem> = emptyList(), val noteOpen: Boolean = false,
    val cash: Boolean = false, val received: Long = 0, val change: Long = 0,
    val loyalty: Boolean = false, val registered: Boolean = true, val stamps: Boolean = false,
    val cpfReturn: CashierPage = CashierPage.IDENTIFY, val paymentReturn: CashierPage = CashierPage.CARD,
    val compact: Boolean = true, val notice: String = "",
    val customerName: String = "", val missingName: String = "", val missingPhone: String = "", val missingProduct: String = ""
) { val total get() = items.sumOf { it.cents } }
object CashierSimulation {
    val products = listOf(PracticeItem("250008", "TOMATE", 599), PracticeItem("250095", "ABACAXI", 799), PracticeItem("253827", "BANANA PRATA", 499))
    fun money(cents: Long) = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", cents / 100.0)
    private fun amount(input: String): Long? = input.replace(".", "").toLongOrNull()?.takeIf { it in 1..100000000 }
    fun press(s: CashierSession, key: String, hour: Int = 12): CashierSession {
        fun go(p: CashierPage) = s.copy(page = p, input = "", notice = "")
        val digit = key.length == 1 && key[0].isDigit()
        if (key == "LIMPA") return s.copy(input = "", notice = "")
        if (key == "ANULA" || key == "VOLTA") return when(s.page) {
            CashierPage.PARKING -> go(if(s.stamps) CashierPage.STAMPS else CashierPage.RECEIVE)
            CashierPage.CARD -> s.copy(page = CashierPage.CANCEL_CARD, paymentReturn = CashierPage.CARD)
            CashierPage.QR -> s.copy(page = CashierPage.CANCEL_PIX, paymentReturn = CashierPage.QR)
            CashierPage.CNPJ -> go(CashierPage.CNPJ_ERROR)
            CashierPage.CPF, CashierPage.PHONE, CashierPage.UNREGISTERED -> go(s.cpfReturn)
            else -> s.copy(notice = "Esta ação ainda não foi detalhada para esta tela.")
        }
        return when(s.page) {
            CashierPage.AVAILABLE -> if(key == "ENTRA") go(CashierPage.CLUB) else s
            CashierPage.CLUB -> when(key) { "1" -> go(CashierPage.CLUB_METHOD); "3" -> go(CashierPage.LOYALTY); else -> s }
            CashierPage.CLUB_METHOD -> when(key) { "1" -> go(CashierPage.MAGNETIC); "2" -> go(CashierPage.AGREEMENT); "3" -> go(CashierPage.CPF); else -> s }
            CashierPage.MAGNETIC, CashierPage.AGREEMENT -> if(key == "SIM_CARD") go(CashierPage.LOYALTY) else s
            CashierPage.LOYALTY -> when(key) { "1" -> go(CashierPage.LOYALTY_CPF); "3" -> go(CashierPage.IDENTIFY); else -> s }
            CashierPage.LOYALTY_CPF -> when(key) { "1" -> s.copy(page = CashierPage.CPF, cpfReturn = CashierPage.IDENTIFY, loyalty = true, input = ""); "0" -> go(CashierPage.IDENTIFY); else -> s }
            CashierPage.CUSTOMER_NAME -> if(key == "ENTRA") go(CashierPage.OFFERS_CONFIRM) else s
            CashierPage.OFFERS_CONFIRM -> if(key == "SIM_ASKED") go(CashierPage.IDENTIFY) else s
            CashierPage.MISSING -> when(key) { "SIM_MISSING" -> go(CashierPage.MISSING_FORM).copy(missingName = s.customerName); "SIM_FOUND" -> go(CashierPage.SALE).copy(noteOpen = true); else -> s }
            CashierPage.MISSING_FORM -> if(key == "SIM_SAVE_MISSING" && s.missingName.isNotBlank() && s.missingPhone.isNotBlank() && s.missingProduct.isNotBlank()) go(CashierPage.SALE).copy(noteOpen = true, notice = "Anotação de treino concluída: ${s.missingName}, ${s.missingPhone}, ${s.missingProduct}. Entregue a nota conforme o procedimento da loja.") else s.copy(notice = "Preencha nome, telefone e produto que faltou.")
            CashierPage.IDENTIFY -> when(key) { "1" -> s.copy(page = CashierPage.CPF, cpfReturn = CashierPage.SALE, loyalty = false); "3" -> go(CashierPage.CNPJ); "0" -> s.copy(page = if(s.customerName.isNotBlank()) CashierPage.MISSING else CashierPage.SALE, noteOpen = true, input = ""); else -> s }
            CashierPage.CPF -> if(key == "SIM_PIN") {
                if(s.loyalty && !s.registered) go(CashierPage.UNREGISTERED)
                else if(s.loyalty) s.copy(page = CashierPage.CUSTOMER_NAME, customerName = "CLIENTE DE TREINO", input = "")
                else s.copy(page = if(s.cpfReturn == CashierPage.SALE && s.customerName.isNotBlank()) CashierPage.MISSING else s.cpfReturn, noteOpen = s.noteOpen || s.cpfReturn == CashierPage.SALE, input = "")
            } else s
            CashierPage.UNREGISTERED -> if(key == "ENTRA") go(CashierPage.PHONE) else s
            CashierPage.PHONE -> if(key == "SIM_PIN") s.copy(page = s.cpfReturn, input = "", notice = "Oriente o cliente: você receberá um SMS para concluir o cadastro em casa. (SMS apenas simulado.)") else s
            CashierPage.CNPJ -> if(key == "SIM_PIN") s.copy(page = if(s.customerName.isNotBlank()) CashierPage.MISSING else CashierPage.SALE, noteOpen = true) else s
            CashierPage.CNPJ_ERROR -> if(key == "0") go(CashierPage.IDENTIFY) else s
            CashierPage.SALE -> when {
                digit -> s.copy(input = (s.input + key).take(6), notice = "")
                key == "ENTRA" && s.input.isNotEmpty() -> products.find { it.code == s.input }?.let { s.copy(items = s.items + it, input = "", notice = "Produto registrado") } ?: s.copy(notice = "Código não encontrado. Use os códigos dos balões.")
                key == "ENTRA" -> go(CashierPage.IDENTIFY)
                key == "FUNÇÃO" -> go(CashierPage.FUNCTION)
                key == "TOTAL" && s.items.isNotEmpty() -> go(CashierPage.PARKING)
                else -> s
            }
            CashierPage.FUNCTION -> when { digit -> (s.input + key).let { if(it == "514") go(CashierPage.LOYALTY) else s.copy(input = it.take(3)) }; else -> s }
            CashierPage.PARKING -> if(key == "SIM_CARD") go(if(s.stamps) CashierPage.STAMPS else CashierPage.RECEIVE) else s
            CashierPage.STAMPS -> if(key == "ENTRA") go(CashierPage.RECEIVE) else s
            CashierPage.RECEIVE -> when {
                digit -> s.copy(input = (s.input + key).take(9))
                key == "TEF" -> go(CashierPage.TEF).copy(cash = false, change = 0, received = 0)
                key == "DINHEIRO" -> amount(s.input)?.takeIf { it >= s.total }?.let { s.copy(page = CashierPage.RECEIPT, cash = true, received = it, input = "") } ?: s.copy(notice = "Digite o valor recebido em centavos, suficiente para a compra.")
                else -> s
            }
            CashierPage.TEF -> when(key) { "1" -> go(CashierPage.DEBIT_FORM); "2" -> go(CashierPage.CREDIT_FORM); else -> s.copy(notice = "Este tipo de TEF ainda não tem roteiro informado.") }
            CashierPage.DEBIT_FORM -> when(key) { "1" -> go(CashierPage.DEBIT_TYPE); "2" -> go(CashierPage.CARD); else -> s }
            CashierPage.DEBIT_TYPE, CashierPage.CREDIT_TYPE -> if(key == "1") go(CashierPage.CARD) else s.copy(notice = "Roteiro Libercard ainda não informado.")
            CashierPage.CREDIT_FORM -> when(key) { "1" -> go(CashierPage.CREDIT_TYPE); "2" -> go(CashierPage.WALLET); else -> s }
            CashierPage.WALLET -> when(key) { "3" -> go(CashierPage.QR); "4" -> if(hour < 20) go(CashierPage.PIX_CHANGE) else s.copy(notice = "Pix Troco disponível somente até as 20h, conforme a regra da loja."); else -> s }
            CashierPage.PIX_CHANGE -> when { digit -> s.copy(input = (s.input + key).take(9)); key == "ENTRA" -> amount(s.input)?.let { s.copy(page = CashierPage.PIX_CONFIRM, change = it, input = "") } ?: s; else -> s }
            CashierPage.PIX_CONFIRM -> if(key == "SIM_PIN") go(CashierPage.QR) else s
            CashierPage.CARD, CashierPage.QR -> if(key == "SIM_APPROVE") go(CashierPage.AUTHORIZED).copy(received = s.total + s.change, cash = false) else s
            CashierPage.AUTHORIZED -> if(key == "ENTRA") go(CashierPage.RECEIPT) else s
            CashierPage.CANCEL_CARD -> when(key) { "0" -> go(CashierPage.RECEIVE).copy(change = 0, received = 0); "1" -> go(s.paymentReturn); else -> s }
            CashierPage.CANCEL_PIX -> when(key) { "1" -> go(CashierPage.RECEIVE).copy(change = 0, received = 0); "2" -> go(s.paymentReturn); else -> s }
            CashierPage.RECEIPT -> if(key == "1" || key == "3") s.copy(page = if(s.cash) CashierPage.DONATION else CashierPage.DONE, compact = key == "1") else s
            CashierPage.DONATION -> when(key) { "3" -> go(CashierPage.DONE); "1" -> s.copy(notice = "Fluxo de doação ainda aguardando as próximas telas. Nenhum valor foi doado."); else -> s }
            CashierPage.DONE -> if(key == "ENTRA") CashierSession(registered = s.registered, stamps = s.stamps) else s
        }
    }
    fun operator(s: CashierSession): String = when(s.page) {
        CashierPage.AVAILABLE -> "DISPONIVEL"
        CashierPage.CLUB -> "Clube da Gente?\n1-Sim  3-Nao"
        CashierPage.CLUB_METHOD -> "1)MAGNETIC 2)CARTAO\n3)CPF(FUNC)"
        CashierPage.MAGNETIC -> "PASSE O CARTAO"
        CashierPage.AGREEMENT -> "CARTAO CONVENIADO"
        CashierPage.LOYALTY -> "NORDESTAO PRA VOCE\n1-Sim  3-Nao"
        CashierPage.LOYALTY_CPF -> "NORDESTAO PRA VOCE\n1-CPF 0-SAIR"
        CashierPage.IDENTIFY -> "IDENTIFICA CLIENTE\n1-CPF 3-CNPJ 0-SAI"
        CashierPage.CPF -> "INFORME CPF NO PIN"
        CashierPage.CNPJ -> "INFORME CNPJ NO PIN"
        CashierPage.CNPJ_ERROR -> "CNPJ CLIENTE"
        CashierPage.UNREGISTERED -> "NAO CADASTRADO"
        CashierPage.PHONE -> "DDD+TELEFONE\n(PIN PAD)"
        CashierPage.CUSTOMER_NAME, CashierPage.OFFERS_CONFIRM -> s.customerName
        CashierPage.MISSING -> "CONSULTA DO OPERADOR"
        CashierPage.MISSING_FORM -> "ANOTAR PRODUTO EM FALTA"
        CashierPage.SALE -> if(s.input.isNotEmpty()) "1,000 X CODIGO\n${s.input}" else s.items.lastOrNull()?.let { "1 X ${it.name}\n${money(it.cents)}" } ?: "1,000 X CODIGO"
        CashierPage.FUNCTION -> "FUNCAO\n${s.input}"
        CashierPage.PARKING -> "CARTAO ESTACIONAMENTO"
        CashierPage.STAMPS -> "CAMPANHA DE SELOS\nTexto/quantidade a configurar"
        CashierPage.RECEIVE -> "A REC     ${money(s.total)}\n${s.input.takeIf { it.isNotEmpty() }?.let { money(it.toLong()) }.orEmpty()}"
        CashierPage.TEF, CashierPage.DEBIT_FORM, CashierPage.DEBIT_TYPE, CashierPage.CREDIT_FORM, CashierPage.CREDIT_TYPE, CashierPage.WALLET -> "ESCOLHA OPCAO"
        CashierPage.PIX_CHANGE -> "VALOR PIX TROCO\n${s.input}"
        CashierPage.PIX_CONFIRM -> "CONFIRME NO PIN PAD"
        CashierPage.CARD -> "Aproxime, insira ou\npasse o cartao na leitora"
        CashierPage.QR -> "Aguarde, em processamento"
        CashierPage.AUTHORIZED -> "AUTORIZADO"
        CashierPage.CANCEL_CARD -> "OPCAO: 0=SIM 1=NAO"
        CashierPage.CANCEL_PIX -> "DESEJA CANCELAR O QR\n1-SIM 2-NAO"
        CashierPage.RECEIPT -> "Cupom Resumido?\n1-Sim 3-Nao"
        CashierPage.DONATION -> "REALIZAR DOACAO ?\n1-SIM 3-NAO"
        CashierPage.DONE -> "CUPOM ${if(s.compact) "RESUMIDO" else "DETALHADO"}\n${if(s.cash) "TROCO ${money(s.received - s.total)}" else "PAGAMENTO CONCLUIDO"}"
    }
    fun customer(s: CashierSession): String = when(s.page) {
        CashierPage.TEF -> "Tipos de TEF\n001 - DEBITO\n002 - CREDITO\n005 - CARTAO TRICARD\n008 - TRICARD 3 X S/JUROS\n015 - LIBERCARD\n019 - CREDITO 3 X S/JUROS\n030 - NORDCARD ROTATIVO\n031 - NORDCARD ATE 3 X S/JUROS\n032 - NORDCARD 4 OU 5 X C/JUROS\n033 - NORDCARD BAZ/ELE/PAP 2 ATE 6 X S/JUROS"
        CashierPage.DEBIT_FORM -> "Selecione a forma de pagamento\n1:Cartao de Debito\n2:Refeicao Eletronico"
        CashierPage.DEBIT_TYPE -> "Selecione o tipo do Cartao de Debito\n1:Cartao de Debito\n2:Libercard"
        CashierPage.CREDIT_FORM -> "Selecione a forma de pagamento\n1:Cartao de Credito\n2:Carteira Digital"
        CashierPage.CREDIT_TYPE -> "Selecione o tipo do Cartao de Credito\n1:Cartao de Credito\n2:Libercard"
        CashierPage.WALLET -> "Carteira Digital\n1:Izio\n2:PicPay\n3:PIX\n4:Pix Troco"
        CashierPage.QR -> "PIX — SIMULACAO\nValor: ${money(s.total + s.change)}\nQR demonstrativo, sem cobrança"
        CashierPage.CARD -> "Aproxime, insira ou passe o cartao na leitora"
        CashierPage.PIX_CONFIRM -> "Confirme no PINpad\nTroco solicitado: ${money(s.change)}"
        CashierPage.RECEIVE, CashierPage.RECEIPT, CashierPage.DONATION, CashierPage.DONE -> "TOTAL DA COMPRA: ${money(s.total)}\nVALOR RECEBIDO: ${money(s.received)}\nVALOR A PAGAR: ${money((s.total-s.received).coerceAtLeast(0))}\nTROCO: ${money(if(s.cash) (s.received-s.total).coerceAtLeast(0) else s.change)}"
        else -> "Nordestão pra Você\n" + (s.items.lastOrNull()?.let { "CODIGO: ${it.code}\nDESCRICAO: ${it.name}\nQUANTIDADE: 1\nPRECO UNITARIO: ${money(it.cents)}\nPRECO TOTAL: ${money(it.cents)}\n" }.orEmpty()) + "SUBTOTAL: ${money(s.total)}\n" + s.items.mapIndexed { i, p -> "${i+1} ${p.name} ${money(p.cents)}" }.joinToString("\n")
    }
}
