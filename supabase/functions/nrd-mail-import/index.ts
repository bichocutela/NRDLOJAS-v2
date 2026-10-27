import {createRemoteJWKSet,jwtVerify} from 'npm:jose@5.9.6';
import {createHandler} from './handler.mjs';
const jwks=createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
const verify=async(token:string)=>{
 const {payload}=await jwtVerify(token,jwks,{algorithms:['RS256'],issuer:'https://securetoken.google.com/appcodigo-7f245',audience:'appcodigo-7f245'});
 if(!payload.sub) throw Error('AUTH_REQUIRED'); return payload;
};
async function rest(path:string, method='GET', body?:unknown) {
 const key=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
 const response=await fetch(Deno.env.get('SUPABASE_URL')+'/rest/v1/'+path,{method,
 headers:{apikey:key,Authorization:`Bearer ${key}`,'Content-Type':'application/json',Prefer:'resolution=merge-duplicates,return=minimal'},
 ...(body === undefined ? {} : {body:JSON.stringify(body)}),signal:AbortSignal.timeout(15000)});
 if(!response.ok) throw Error('DATABASE_UNAVAILABLE');
 const text=await response.text();return text?JSON.parse(text):null;
}
const scanFields='scan_request_id,scan_finished_id,scan_finished_at,scan_seen_at,scan_failed';
const db={
 settings:async()=> (await rest('nrd_mail_settings?id=eq.true&select=key_hash'))?.[0],
 scanStatus:async()=>{
   const row=(await rest('nrd_mail_settings?id=eq.true&select='+scanFields))?.[0] || {};
   return {requestId:row.scan_request_id || null,finishedId:row.scan_finished_id || null,
     finishedAt:row.scan_finished_at || null,seenAt:row.scan_seen_at || null,failed:row.scan_failed === true};
 },
 requestScan:async()=>{
   const requestId=crypto.randomUUID();
   await rest('nrd_mail_settings?id=eq.true','PATCH',{scan_request_id:requestId});
   return requestId;
 },
 poll:async()=>{
   await rest('nrd_mail_settings?id=eq.true','PATCH',{scan_seen_at:new Date().toISOString()});
   const row=await db.scanStatus();
   return {requestId:row.requestId,finishedId:row.finishedId};
 },
 ack:async(requestId:string,failed:boolean)=>{
   // Late workers cannot acknowledge a newer request from another Master.
   await rest('nrd_mail_settings?id=eq.true&scan_request_id=eq.'+requestId,'PATCH',{
     scan_finished_id:requestId,scan_finished_at:new Date().toISOString(),scan_failed:failed});
 },
 setup:async(email:string,key_hash:string)=>rest('nrd_mail_settings?on_conflict=id','POST',{id:true,email,key_hash}),
 list:async()=>rest('nrd_mail_documents?status=eq.pending&select=id,filename,store_code,received_at,status&order=received_at.asc&limit=250'),
 get:async(id:string)=>(await rest('nrd_mail_documents?id=eq.'+id+'&select=id,filename,store_code,pdf_base64,status'))?.[0],
 enqueue:async(value:unknown)=>rest('rpc/nrd_mail_enqueue','POST',{document:value}),
 finish:async(id:string,status:string)=>rest('nrd_mail_documents?id=eq.'+id,'PATCH',{status,pdf_base64:null}),
 claimMailNotification:async()=>rest('rpc/nrd_mail_claim_notification','POST',{})
};
async function notifyMailDocuments(key:string) {
 const serviceRoleKey=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
 const response=await fetch(Deno.env.get('SUPABASE_URL')+'/functions/v1/send-fcm',{
  method:'POST',headers:{apikey:serviceRoleKey,Authorization:'Bearer '+serviceRoleKey,'Content-Type':'application/json','x-nrd-mail-key':key},
  body:JSON.stringify({action:'NOTIFY_MAIL_DOCUMENTS'}),signal:AbortSignal.timeout(12000)
 });
 if(!response.ok) throw Error('NOTIFICATION_UNAVAILABLE');
}
Deno.serve(createHandler({verify,db,notifyMailDocuments}));
