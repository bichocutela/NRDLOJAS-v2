import {boundedJson} from '../nrd-price-gateway/handler.mjs';
import {encodeFields,decodeFields} from './documents.mjs';
export function createControlHandler({verify,authorize,documents}) {
 const reply=(status,data)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
 return async req=>{
  if(req.method!=='POST')return reply(405,{error:'METHOD_NOT_ALLOWED'});
  let user=null,body;
  try {const token=req.headers.get('x-firebase-token');if(token)user=await verify(token);}catch{return reply(401,{error:'AUTH_REQUIRED'});}
  try {body=await boundedJson(req,900000);}catch{return reply(400,{error:'INVALID_REQUEST'});}
  const master=user?.email==='mestre@nrdlojas.com';
  try {
   if(body.operation==='access'&&Object.keys(body).length===1){
    if(master)return reply(200,{enabled:true,profile:true,promotions:true,prices:true,publicAccess:false});
    return reply(200,await authorize(req));
   }
   if(body.operation==='list'&&Object.keys(body).length===1){
    if(!master)return reply(403,{error:'ACCESS_DENIED'});
    return reply(200,{items:(await documents.list('restricted_access')).map(row=>({uid:row.path.split('/')[1],...decodeFields(row.fields)}))});
   }
   const path=documents.valid(body.path);
   if(body.operation==='read'&&Object.keys(body).sort().join(',')==='operation,path'){
    if(!master){
     if(!path.startsWith('config/')||path==='config/restricted_access')return reply(403,{error:'ACCESS_DENIED'});
     const access=await authorize(req);
     if(!access.promotions&&!access.prices)return reply(403,{error:'ACCESS_DENIED'});
    }
    return reply(200,{data:decodeFields(await documents.fields(path))});
   }
   if(!master)return reply(403,{error:'ACCESS_DENIED'});
   if(body.operation!=='write'||Object.keys(body).some(k=>!['operation','path','data','merge'].includes(k))||!body.data||Array.isArray(body.data)||typeof body.data!=='object'||typeof body.merge!=='boolean'||path.startsWith('access_sessions/'))return reply(400,{error:'INVALID_REQUEST'});
   if(path==='config/restricted_access'&&(Object.keys(body.data).join(',')!=='publicAccess'||typeof body.data.publicAccess!=='boolean'))return reply(400,{error:'INVALID_REQUEST'});
   if(path.startsWith('restricted_access/')&&(Object.keys(body.data).some(k=>!['login','enabled','profile','promotions','prices'].includes(k))||Object.entries(body.data).some(([k,v])=>k==='login'?typeof v!=='string'||! /^[a-z0-9._-]{3,64}$/.test(v):typeof v!=='boolean')))return reply(400,{error:'INVALID_REQUEST'});
   // CAS makes read-modify-write of store enablement and validity safe across masters/devices.
   for(let attempt=0;attempt<4;attempt++){
    const previous=await documents.get(path);
    const fields=encodeFields(body.data);
    if(body.merge&&previous&&!previous.deleted){
     for(const [key,value]of Object.entries(fields))if(value.mapValue&&previous.fields[key]?.mapValue)value.mapValue.fields={...previous.fields[key].mapValue.fields,...value.mapValue.fields};
    }
    const saved=await documents.write(path,fields,{merge:body.merge,version:previous?.version??null,insertOnly:!previous});
    if(saved)return reply(200,{ok:true});
   }
   return reply(409,{error:'RETRY'});
  }catch(error){return reply(error?.message==='INVALID_DOCUMENT'?400:503,{error:error?.message==='INVALID_DOCUMENT'?'INVALID_REQUEST':'CONTROL_UNAVAILABLE'});}
 };
}
