export function createHandler({readEvent, claim, send, finish, release}) {
  const reply=(status,value)=>new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
  return async request=>{
    if(request.method!=='POST')return reply(405,{error:'METHOD_NOT_ALLOWED'});
    const proof=request.headers.get('x-installation-proof');
    if(!proof || !/^[0-9a-f]{64}$/.test(proof))return reply(401,{error:'INVALID_PROOF'});
    let lease;
    try {
      // The random proof is never stored in the cloud: the immutable event's ID is its SHA-256.
      const event=await readEvent(proof);
      if(!event)return reply(401,{error:'INVALID_PROOF'});
      if(!/^[0-9a-f]{64}$/.test(event.deviceIdHash) || !Number.isInteger(event.appVersionCode) ||
        event.appVersionCode<1 || event.appVersionCode>2147483647 ||
        !/^v?\d+\.\d+\.\d+$/.test(event.appVersion) ||
        !Number.isFinite(Date.parse(event.createdAt)) || !Number.isFinite(Date.parse(event.firstSeenAt)))return reply(400,{error:'INVALID_EVENT'});
      const eventId=event.deviceIdHash+'_'+event.appVersionCode;
      const result=await claim(eventId);
      if(result.sent)return reply(200,{delivered:true});
      if(!result.lease)return reply(409,{error:'DELIVERY_PENDING'});
      lease=result.lease;
      const fresh=event.createdAt===event.firstSeenAt;
      await send({eventId,type:'NEW_INSTALLATION',
        title:fresh?'Nova instalação do NRD V2':'Aplicativo atualizado',
        body:fresh?`Um aparelho novo instalou o NRD V2 ${event.appVersion}.`:`Um aparelho atualizou para o NRD V2 ${event.appVersion}.`});
      await finish(lease);
      return reply(200,{delivered:true});
    } catch {
      if(lease)await release(lease).catch(()=>{});
      return reply(503,{error:'DELIVERY_UNAVAILABLE'});
    }
  };
}
