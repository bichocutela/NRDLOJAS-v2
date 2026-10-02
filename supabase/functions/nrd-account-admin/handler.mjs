// Dependencies are injected so authorization and partial failures can be tested offline.
export function createHandler({verify, lookup, grant, disable, removeUser, removeGrant}) {
  const reply = (status, data) => new Response(JSON.stringify(data), {status, headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
  return async req => {
    if (req.method !== 'POST') return reply(405,{error:'METHOD_NOT_ALLOWED'});
    let master;
    try {
      const token = req.headers.get('x-firebase-token');
      if (!token || token.length > 8192) return reply(401,{error:'UNAUTHORIZED'});
      master = await verify(token);
      if (master.email !== 'mestre@nrdlojas.com') return reply(403,{error:'FORBIDDEN'});
      const current = await lookup(master.uid);
      if (!current || current.disabled || current.email !== master.email ||
          Number(current.validSince ?? 0) > Number(master.authTime ?? 0)) return reply(403,{error:'FORBIDDEN'});
    } catch { return reply(401,{error:'UNAUTHORIZED'}); }
    let uid;
    try {
      const reader = req.body?.getReader();
      if (!reader) throw Error('EMPTY');
      const chunks=[]; let size=0;
      while (true) {
        const {value,done}=await reader.read(); if(done)break;
        size+=value.length; if(size>1024){await reader.cancel();throw Error('TOO_LARGE');} chunks.push(value);
      }
      const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
      const body=JSON.parse(new TextDecoder().decode(bytes));
      uid=body.uid;
      if (Object.keys(body).length !== 1 || typeof uid !== 'string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(uid) || uid === master.uid) throw Error('INVALID_UID');
    } catch { return reply(400,{error:'INVALID_REQUEST'}); }
    let blocked=false;
    try {
      const access=await grant(uid);
      if (!access) return reply(200,{deleted:true});
      if (typeof access.login !== 'string' || !/^[a-z0-9._-]{3,64}$/.test(access.login) || ['admin','mestre'].includes(access.login)) return reply(403,{error:'PROTECTED_ACCOUNT'});
      const target=await lookup(uid);
      if (target && target.email !== `${access.login}@usuarios.nrdlojas.com`) return reply(403,{error:'PROTECTED_ACCOUNT'});
      // Revoking the document immediately denies protected server calls even while an ID token remains valid.
      await disable(uid);blocked=true;
      if(target) await removeUser(uid);
      await removeGrant(uid);
      return reply(200,{deleted:true});
    } catch { return reply(503,{error:blocked?'DELETE_INCOMPLETE':'SERVICE_UNAVAILABLE'}); }
  };
}
