const defaults={enabled:true,productAdded:true,codeChanged:true,suggestionFixed:true,appUpdate:true,promotionUpdated:true,trainingEnabled:false,encarteMode:'OFF'};
export function webPreferences(raw={}) {
 if(!raw||typeof raw!=='object'||Array.isArray(raw))throw Error('INVALID_PREFERENCES');
 if(Object.keys(raw).some(k=>!Object.hasOwn(defaults,k)))throw Error('INVALID_PREFERENCES');
 const result={...defaults};
 for(const [key,value]of Object.entries(raw)){
  if(key==='encarteMode'){if(!['OFF','SIMPLE','DETAILED'].includes(value))throw Error('INVALID_PREFERENCES');}
  else if(typeof value!=='boolean')throw Error('INVALID_PREFERENCES');
  result[key]=value;
 }
 return result;
}
export function webInterested(type,raw={}){
 const p=webPreferences(raw);if(!p.enabled)return false;
 if(type==='NEW_ENCARTE')return p.encarteMode!=='OFF';
 const flag={NEW_PRODUCT:'productAdded',CODE_CHANGED:'codeChanged',SUGGESTION_FIXED:'suggestionFixed',APP_UPDATE:'appUpdate',NEW_TRAINING:'trainingEnabled',PROMOTIONS_UPDATED:'promotionUpdated',PROMOTION_UPDATED:'promotionUpdated'}[type];
 return flag? p[flag]:false;
}
export function createWebNotifier({url,key,project,token,fetcher=fetch}){
 return async data=>{
  const endpoint=new URL(url+'/rest/v1/web_push_subscriptions');endpoint.searchParams.set('select','token,preferences');
  const result=await fetcher(endpoint,{headers:{apikey:key,Authorization:'Bearer '+key},signal:AbortSignal.timeout(10000)});
  if(!result.ok)throw Error('WEB_SUBSCRIPTIONS_UNAVAILABLE');
  const rows=await result.json();if(!Array.isArray(rows))throw Error('INVALID_SUBSCRIPTIONS');
  const recipients=rows.filter(row=>{try{return typeof row.token==='string'&&webInterested(data.type,row.preferences??{});}catch{return false;}});
  let delivered=0;const authorization=recipients.length?'Bearer '+await token():'';
  for(let index=0;index<recipients.length;index+=4){
   const results=await Promise.allSettled(recipients.slice(index,index+4).map(row=>fetcher(`https://fcm.googleapis.com/v1/projects/${project}/messages:send`,{method:'POST',headers:{Authorization:authorization,'Content-Type':'application/json'},body:JSON.stringify({message:{token:row.token,data:{...data,url:data.type==='NEW_ENCARTE'?'https://bichocutela.github.io/?encarte='+encodeURIComponent(data.encarteId):data.type==='NEW_TRAINING'?'https://bichocutela.github.io/?treinamento='+encodeURIComponent(data.trainingId):data.url||'https://bichocutela.github.io/'},webpush:{headers:{Urgency:'normal',TTL:'86400'}}}}),signal:AbortSignal.timeout(10000)})));
   delivered+=results.filter(r=>r.status==='fulfilled'&&r.value.ok).length;
  }
  return {recipients:recipients.length,delivered};
 };
}
