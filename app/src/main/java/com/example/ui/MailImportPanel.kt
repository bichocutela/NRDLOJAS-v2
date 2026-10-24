package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.StoreCatalog
import com.example.data.flyer.FlyerAnalysisResult
import com.example.data.promotions.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MailImportPanel() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var connector by remember { mutableStateOf<String?>(null) }
    var documents by remember { mutableStateOf<List<MailImportDocument>>(emptyList()) }
    var reviews by remember { mutableStateOf<Map<String, FlyerAnalysisResult>>(emptyMap()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var detail by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf<MailImportDocument?>(null) }
    var publishConfirm by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    fun launchJob(job: suspend () -> Unit) {
        scope.launch {
            busy=true; error=false
            try { job() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) { error=true; message=failure.message ?: "Não foi possível concluir. Tente novamente." }
            finally { busy=false }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Relatórios por e-mail", style=MaterialTheme.typography.titleLarge)
            Text("Encaminhe o e-mail com os PDFs CENTRO e CLUBE para o Gmail configurado. Revise e publique as outras lojas sem ACP. Cidade Jardim (0012) permanece no fluxo atual.")
            TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Ocultar configuração do Gmail" else "Configurar Gmail") }
            if(expanded) {
                OutlinedTextField(email,{email=it},label={Text("Gmail exclusivo para os relatórios")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Text("A configuração usa um projeto privado do Google Apps Script na sua conta. A autorização é feita no Google, sem informar senha ao NRD.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled=!busy && email.trim().endsWith("@gmail.com"),onClick={launchJob {
                    connector=MailImportRepository.setup(context,email.trim())
                    message="Conector gerado. O conector anterior, se houver, foi desativado. Copie o código e o manifesto no projeto privado do Google."
                }}) {Text("Gerar conector (substitui anterior)")}
                connector?.let { code ->
                    TextButton(onClick={clipboard.setText(AnnotatedString(code));message="Código do conector copiado."}) {Text("1. Copiar código do conector")}
                    TextButton(onClick={
                        val manifest=context.assets.open("mail-import/appsscript.json").bufferedReader().use{it.readText()}
                        clipboard.setText(AnnotatedString(manifest));message="Manifesto copiado. No Google, exiba o arquivo appsscript.json nas configurações do projeto e substitua o conteúdo."
                    }) {Text("2. Copiar manifesto appsscript.json")}
                    TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://script.google.com/home/start")))}.onFailure{message="Abra script.google.com no navegador."}}) {Text("Abrir Google Apps Script")}
                    Text("No projeto privado, cole o código em Código.gs e o manifesto em appsscript.json. Execute ativarNRD e autorize a conta Gmail informada. Depois, o conector verifica novos anexos a cada 15 minutos. A primeira leitura considera os últimos 30 dias. Não publique o projeto como aplicativo Web.",style=MaterialTheme.typography.bodySmall)
                }
            }
            Button(enabled=!busy,onClick={launchJob {
                documents=MailImportRepository.list()
                message="${documents.size} documento(s) aguardando revisão."
            }}) {Text("Buscar documentos recebidos")}
            if(documents.isNotEmpty()) {
                Text("${documents.size} documento(s) pendente(s). Confira a loja e a validade de cada relatório antes de publicar.")
                OutlinedButton(enabled=!busy,onClick={launchJob {
                    var failures=0
                    documents.forEachIndexed { index, document ->
                        if(document.id !in reviews) {
                            message="Lendo ${index+1} de ${documents.size}: ${document.filename}"
                            try { reviews=reviews+(document.id to MailImportRepository.analyze(context,document)) }
                            catch(cancelled: CancellationException){throw cancelled}
                            catch(_: Exception){failures++}
                        }
                    }
                    error=failures>0
                    message="${reviews.size} relatório(s) lido(s); $failures falha(s). Use Revisar para conferir ou tentar novamente."
                }}) {Text("Ler todos os documentos")}
                documents.forEach { document ->
                    val analysis=reviews[document.id]
                    val records=analysis?.let { PromotionStores.records(it) }.orEmpty()
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(checked=document.id in selected,enabled=!busy && records.isNotEmpty(),onCheckedChange={checked->selected=if(checked)selected+document.id else selected-document.id})
                        Column(Modifier.weight(1f)) {
                            Text(document.filename)
                            Text(StoreCatalog.labelFor(document.storeCode),style=MaterialTheme.typography.labelMedium)
                            Text(if(analysis==null) "Aguardando leitura" else "${records.size} oferta(s) válidas • ${analysis.offers.size-records.size} pendente(s) ou não suportada(s)",style=MaterialTheme.typography.bodySmall)
                            TextButton(enabled=!busy,onClick={launchJob {
                                if(document.id !in reviews) reviews=reviews+(document.id to MailImportRepository.analyze(context,document))
                                detail=document.id
                            }}) {Text("Revisar")}
                            TextButton(enabled=!busy,onClick={discard=document}) {Text("Descartar da fila")}
                        }
                    }
                }
                Text("Marque somente os documentos conferidos. Serão publicadas apenas as ofertas válidas de cada documento; as linhas pendentes não serão publicadas.",style=MaterialTheme.typography.bodySmall)
                Button(enabled=!busy && selected.isNotEmpty(),onClick={publishConfirm=true}) {Text("Publicar documentos conferidos (${selected.size})")}
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank()) Text(message,color=if(error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
        }
    }
    discard?.let { document ->
        AlertDialog(onDismissRequest={discard=null},title={Text("Descartar ${document.filename}?")},
            text={Text("O documento será retirado da fila do NRD. O e-mail original no Gmail e as ofertas já publicadas serão preservados.")},
            confirmButton={TextButton(enabled=!busy,onClick={discard=null;launchJob {
                MailImportRepository.finish(document,false)
                documents=MailImportRepository.list(); selected=selected-document.id; reviews=reviews-document.id
                message="Documento retirado da fila."
            }}){Text("Descartar")}},dismissButton={TextButton(onClick={discard=null}){Text("Voltar")}})
    }
    if(publishConfirm) AlertDialog(onDismissRequest={if(!busy)publishConfirm=false},title={Text("Publicar documentos conferidos?")},text={
        val chosen=documents.filter{it.id in selected}
        val omitted=chosen.sumOf{doc->reviews[doc.id]?.let{it.offers.size-PromotionStores.records(it).size} ?: 0}
        Text("Serão publicados ${chosen.size} documento(s). $omitted linha(s) inválida(s) ou não suportada(s) ficarão pendentes e não serão publicadas. As demais ofertas vigentes serão preservadas. Confira se as lojas indicadas estão corretas.")
    },confirmButton={TextButton(enabled=!busy,onClick={publishConfirm=false;launchJob {
                    var successes=0
                    var pending=0
                    for(document in documents.filter{it.id in selected}) {
                        val analysis=reviews[document.id] ?: continue
                        val records=PromotionStores.records(analysis)
                        require(records.all { AcpPromotionsRepository.isoDate(it.from)!=null && AcpPromotionsRepository.isoDate(it.to)!=null }) {"Confira as datas de ${document.filename}."}
                        message="Publicando ${document.filename}…"
                        PromotionStores.publish(document.storeCode,analysis)
                        if(analysis.offers.size == records.size) MailImportRepository.finish(document,true) else pending++
                        selected=selected-document.id; successes++
                    }
                    documents=MailImportRepository.list()
                    message="$successes documento(s) publicado(s); $pending com linhas pendentes continuam na fila. As outras ofertas vigentes foram preservadas."
                
    }}){Text("Publicar")}},dismissButton={TextButton(onClick={publishConfirm=false}){Text("Voltar à revisão")}})
    detail?.let { id ->
        val document=documents.firstOrNull{it.id==id}
        val analysis=reviews[id]
        if(document!=null && analysis!=null) AlertDialog(onDismissRequest={detail=null},title={Text(document.filename)},text={
            LazyColumn(Modifier.heightIn(max=420.dp)) {
                item {
                    Text("Loja indicada pelo nome do anexo: ${StoreCatalog.labelFor(document.storeCode)}. Confira com o documento original.")
                    analysis.warnings.forEach{Text(it)}
                }
                items(analysis.offers) { offer ->
                    val record=PromotionStores.records(analysis.copy(offers=listOf(offer))).firstOrNull()
                    Column(Modifier.padding(vertical=8.dp)) {
                        Text(offer.sourceDescription)
                        if(offer.detail.isNotBlank()) Text(offer.detail,style=MaterialTheme.typography.bodySmall)
                        Text("Código: ${offer.productCodes.firstOrNull().orEmpty()} • EAN: ${offer.barcodes.firstOrNull().orEmpty()}",style=MaterialTheme.typography.bodySmall)
                        if(record!=null) {
                            Text("${record.family}: de R$ %.2f por R$ %.2f".format(java.util.Locale("pt","BR"),record.previous,record.price))
                            Text("${record.from.orEmpty()} até ${record.to.orEmpty()}")
                        } else Text("Pendente: confira os dados ou a condição da oferta. Esta linha não será publicada.",color=MaterialTheme.colorScheme.error)
                    }
                }
            }
        },confirmButton={TextButton(onClick={detail=null}){Text("Fechar")}})
    }
}
