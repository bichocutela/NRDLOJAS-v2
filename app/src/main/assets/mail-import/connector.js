/* NRD — conector privado desta caixa Gmail. Não publique este projeto como aplicativo Web.
 * 1. Cole este código em um projeto privado no script.google.com.
 * 2. Substitua appsscript.json pelo manifesto fornecido pelo NRD.
 * 3. Execute ativarNRD, confira a conta e autorize o Google.
 * O conector somente lê mensagens; não envia, apaga nem publica promoções.
 */
const NRD = __NRD_CONFIG__;
function ativarNRD() {
  if(Gmail.Users.getProfile('me').emailAddress.toLowerCase() !== NRD.email) throw Error('Abra o projeto na conta Gmail configurada no NRD.');
  const triggers = ScriptApp.getProjectTriggers().filter(t=>t.getHandlerFunction()==='receberRelatoriosNRD');
  if(!triggers.length) ScriptApp.newTrigger('receberRelatoriosNRD').timeBased().everyMinutes(15).create();
  receberRelatoriosNRD();
}
function desativarNRD() {
  ScriptApp.getProjectTriggers().filter(t=>t.getHandlerFunction()==='receberRelatoriosNRD').forEach(t=>ScriptApp.deleteTrigger(t));
}
function receberRelatoriosNRD() {
  const lock = LockService.getScriptLock(); if(!lock.tryLock(1000)) return;
  try {
    if(Gmail.Users.getProfile('me').emailAddress.toLowerCase() !== NRD.email) throw Error('Conta Gmail diferente da configurada.');
    const properties = PropertiesService.getScriptProperties();
    const done = JSON.parse(properties.getProperty('NRD_DONE') || '[]');
    const params = {q:'has:attachment filename:pdf newer_than:30d',maxResults:10};
    const page = properties.getProperty('NRD_PAGE'); if(page) params.pageToken=page;
    let result;
    try {result=Gmail.Users.Messages.list('me',params);} catch(error) {properties.deleteProperty('NRD_PAGE');throw error;}
    const deadline = Date.now()+240000;
    let complete=true, sent=0; const failures=[];
    for(const entry of result.messages || []) {
      if(done.indexOf(entry.id)>=0) continue;
      if(Date.now()>deadline){complete=false;break;}
      const message = Gmail.Users.Messages.get('me',entry.id,{format:'full'});
      const attachments=[];
      function visit(part) {
        if(/^(CENTRO|CLUBE)_[0-9]{4}\.pdf$/i.test(part.filename || '')) attachments.push(part);
        (part.parts || []).forEach(visit);
      }
      visit(message.payload || {});
      let all=true;
      for(const part of attachments) {
        if(/_0012\.pdf$/i.test(part.filename)) continue; // Cidade Jardim permanece ACP.
        if(Date.now()>deadline){all=false;complete=false;break;}
        const body = part.body || {};
        if(body.size>3145728){failures.push('PDF excede 3 MB: '+part.filename);all=false;continue;}
        const encoded = body.attachmentId ? Gmail.Users.Messages.Attachments.get('me',entry.id,body.attachmentId).data : body.data;
        if(!encoded) throw Error('Anexo indisponível: '+part.filename);
        const pdf = Utilities.base64Encode(Utilities.base64DecodeWebSafe(encoded));
        const response = UrlFetchApp.fetch(NRD.url,{method:'post',contentType:'application/json',
          headers:{'x-nrd-mail-key':NRD.key},payload:JSON.stringify({operation:'ingest',filename:part.filename,pdf:pdf}),muteHttpExceptions:true});
        const status=response.getResponseCode();
        let reply; try {reply=JSON.parse(response.getContentText());} catch(error){throw Error('Resposta inválida do NRD.');}
        if(status===400 || status===413){failures.push(part.filename+': '+(reply.error || status));all=false;continue;}
        if(status!==200 || reply.ok!==true) throw Error('NRD recusou '+part.filename+' ('+status+'). Confira a conexão, o limite da fila ou reconfigure o conector.');
        sent++;
      }
      if(all){done.push(entry.id);properties.setProperty('NRD_DONE',JSON.stringify(done.slice(-2000)));}
      if(!complete)break;
    }
    if(complete && result.nextPageToken) properties.setProperty('NRD_PAGE',result.nextPageToken);
    else if(complete) properties.deleteProperty('NRD_PAGE');
    console.log(sent+' anexo(s) recebido(s) ou já existente(s). Confira a fila no painel Mestre.');
    if(failures.length) throw Error('Há anexos pendentes: '+failures.join('; '));
  } finally {lock.releaseLock();}
}
