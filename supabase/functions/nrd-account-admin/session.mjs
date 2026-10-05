// A random device capability is hashed server-side. Optimistic Firestore writes
// serialize competing claims; no expiry releases a session without explicit logout.
export function createSessionHandler({verify,lookup,grant,readSession,writeSession,deleteSession,hash}) {
  const reply=(status,data)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
  return async req=>{
    if(req.method!=='POST')return reply(405,{error:'METHOD_NOT_ALLOWED'});
    let user,body;
    try {
      const token=req.headers.get('x-firebase-token');
      if(!token || token.length>8192)throw Error();
      user=await verify(token);
      const current=await lookup(user.uid);
      if(!current || current.disabled || current.email!==user.email || Number(current.validSince??0)>user.authTime)throw Error();
    } catch{return reply(401,{error:'UNAUTHORIZED'});}
    try {
      const reader=req.body?.getReader();if(!reader)throw Error();
      const chunks=[];let size=0;
      while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>1024){await reader.cancel();throw Error();}chunks.push(value);}
      const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
      body=JSON.parse(new TextDecoder().decode(bytes));
      if(!['claim','release'].includes(body.action) || typeof body.deviceToken!=='string' || !/^[a-f0-9]{64}$/.test(body.deviceToken) || Object.keys(body).some(k=>!['action','deviceToken'].includes(k)))throw Error();
    }catch{return reply(400,{error:'INVALID_REQUEST'});}
    try {
      if(typeof user.email!=='string' || !user.email.endsWith('@usuarios.nrdlojas.com'))return reply(403,{error:'FORBIDDEN'});
      const access=await grant(user.uid);
      if(!access || user.email!==`${access.login}@usuarios.nrdlojas.com`)return reply(403,{error:'FORBIDDEN'});
      const deviceHash=await hash(body.deviceToken);
      for(let attempt=0;attempt<4;attempt++) {
        const session=await readSession(user.uid);
        if(session && session.deviceHash!==deviceHash)return reply(409,{error:'DEVICE_IN_USE'});
        if(body.action==='release') {
          if(!session)return reply(200,{released:true});
          if(await deleteSession(user.uid,session.version))return reply(200,{released:true});
        }else {
          if(await writeSession(user.uid,{deviceHash,authTime:user.authTime},session?.version))return reply(200,{claimed:true});
        }
      }
      return reply(503,{error:'RETRY'});
    }catch{return reply(503,{error:'SERVICE_UNAVAILABLE'});}
  };
}
