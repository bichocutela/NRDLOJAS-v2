export const stores = new Set(['0001','0005','0006','0008','0009','0013','0015','0016','0031','0032','0033','0034','0035','0036','0037','0038','0039','0040','0041','0042','0043','0044','0045','0046']);
export function documentStore(filename) {
  const match = /^(?:CENTRO|CLUBE)_([0-9]{4})\.pdf$/i.exec(filename || '');
  if(!match) throw Error('INVALID_FILENAME');
  if(match[1] === '0012') return null;
  if(!stores.has(match[1])) throw Error('UNKNOWN_STORE');
  return match[1];
}
export async function digest(value) {
  return [...new Uint8Array(await crypto.subtle.digest('SHA-256', typeof value === 'string' ? new TextEncoder().encode(value) : value))].map(x=>x.toString(16).padStart(2,'0')).join('');
}
export function createHandler({verify, db, random = () => [...crypto.getRandomValues(new Uint8Array(32))].map(x=>x.toString(16).padStart(2,'0')).join('')}) {
  const reply = (status, value) => Response.json(value, {status, headers:{'Cache-Control':'no-store'}});
  return async req => {
    if(req.method !== 'POST') return reply(405,{error:'METHOD_NOT_ALLOWED'});
    try {
      // Authenticate before reading an attachment body. The ingestion key cannot read or publish.
      const key = req.headers.get('x-nrd-mail-key');
      let master = false;
      if(!key) { try { master = (await verify(req.headers.get('x-firebase-token') || '')).email === 'mestre@nrdlojas.com'; } catch {} }
      const settings = key ? await db.settings() : null;
      if(key ? !settings || !/^[a-f0-9]{64}$/.test(key) || await digest(key) !== settings.key_hash : !master) return reply(403,{error:'ACCESS_DENIED'});
      const length = Number(req.headers.get('content-length') || 0);
      if(length > 4300000) return reply(413,{error:'DOCUMENT_TOO_LARGE'});
      const reader = req.body?.getReader(); let bytes=0; const chunks=[];
      if(!reader) return reply(400,{error:'INVALID_REQUEST'});
      while(true) { const {value,done}=await reader.read(); if(done) break; bytes+=value.length; if(bytes>4300000){await reader.cancel();return reply(413,{error:'DOCUMENT_TOO_LARGE'});}chunks.push(value); }
      const raw = new Uint8Array(bytes); let position=0; for(const chunk of chunks){raw.set(chunk,position);position+=chunk.length;}
      let body; try { body=JSON.parse(new TextDecoder().decode(raw)); } catch { return reply(400,{error:'INVALID_REQUEST'}); }
      if(!body || Array.isArray(body) || typeof body !== 'object') return reply(400,{error:'INVALID_REQUEST'});
      if(key) {
        if(body.operation === 'poll') {
          if(Object.keys(body).length !== 1) return reply(400,{error:'INVALID_REQUEST'});
          return reply(200,await db.poll());
        }
        if(body.operation === 'ack') {
          if(Object.keys(body).sort().join(',') !== 'failed,operation,requestId' ||
              typeof body.failed !== 'boolean' || !/^[0-9a-f-]{36}$/i.test(body.requestId || ''))
            return reply(400,{error:'INVALID_REQUEST'});
          await db.ack(body.requestId,body.failed);
          return reply(200,{ok:true});
        }
        if(body.operation !== 'ingest') return reply(403,{error:'ACCESS_DENIED'});
        const code = documentStore(body.filename);
        if(code === null) return reply(200,{ok:true,ignored:true,reason:'CITY_GARDEN_USES_ACP'});
        if(typeof body.pdf !== 'string' || body.pdf.length>4194304 || !/^[A-Za-z0-9+/]+={0,2}$/.test(body.pdf)) return reply(400,{error:'INVALID_PDF'});
        let data; try { data=Uint8Array.from(atob(body.pdf), c=>c.charCodeAt(0)); } catch {return reply(400,{error:'INVALID_PDF'});}
        if(data.length>3*1024*1024 || new TextDecoder().decode(data.slice(0,5)) !== '%PDF-') return reply(400,{error:'INVALID_PDF'});
        const buffer = new Uint8Array(data.length + 4); buffer.set(new TextEncoder().encode(code)); buffer.set(data,4);
        const id = await digest(buffer);
        const result = await db.enqueue({id,filename:body.filename,store_code:code,pdf_base64:body.pdf});
        return reply(200,{ok:true,id,duplicate:!result});
      }
      if(body.operation === 'setup') {
        const email = String(body.email || '').trim().toLowerCase();
        if(!/^[a-z0-9._%+-]+@gmail\.com$/.test(email)) return reply(400,{error:'INVALID_EMAIL'});
        const secret = random(); await db.setup(email,await digest(secret));
        return reply(200,{email,key:secret});
      }
      if(body.operation === 'scan' && Object.keys(body).length === 1) {
        return reply(200,{requestId:await db.requestScan()});
      }
      if(body.operation === 'status' && Object.keys(body).length === 1) {
        return reply(200,await db.scanStatus());
      }
      if(body.operation === 'list') return reply(200,{documents:await db.list()});
      if(body.operation === 'download' && /^[a-f0-9]{64}$/.test(body.id || '')) {
        const row = await db.get(body.id);
        return row?.status === 'pending' && row.pdf_base64 ? reply(200,row) : reply(404,{error:'DOCUMENT_UNAVAILABLE'});
      }
      if(['published','reject'].includes(body.operation) && /^[a-f0-9]{64}$/.test(body.id || '')) {
        await db.finish(body.id,body.operation === 'published' ? 'published' : 'rejected'); return reply(200,{ok:true});
      }
      return reply(400,{error:'INVALID_REQUEST'});
    } catch(error) {
      if(['INVALID_FILENAME','UNKNOWN_STORE'].includes(error?.message)) return reply(400,{error:error.message});
      return reply(503,{error:'MAIL_IMPORT_UNAVAILABLE'});
    }
  };
}
