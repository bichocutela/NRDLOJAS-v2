// Fixed, one-time migration. Only the private scheduler capability can invoke it.
import {importPKCS8,SignJWT} from 'npm:jose@5.9.6';
import {ControlDocuments} from '../nrd-promotion-control/documents.mjs';
import {SupabaseCache} from '../nrd-price-gateway/supabase-cache.mjs';
import {boundedJson} from '../nrd-price-gateway/handler.mjs';
const options={url:Deno.env.get('SUPABASE_URL'),key:Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')};
const documents=new ControlDocuments(options),cache=new SupabaseCache(options);
const base='https://firestore.googleapis.com/v1/projects/appcodigo-7f245/databases/(default)/documents/';
Deno.serve(async req=>{
 const reply=(status:number,body:unknown)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
 try {
  const secret=req.headers.get('x-nrd-scheduler-token')??'';
  if(req.method!=='POST'||!/^[a-f0-9]{64}$/.test(secret))return reply(401,{error:'UNAUTHORIZED'});
  const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(secret)))).map(v=>v.toString(16).padStart(2,'0')).join('');
  if(hash!==(await cache.get('promotion_scheduler'))?.payload.hash)return reply(401,{error:'UNAUTHORIZED'});
  if((await cache.get('promotion_control_migration'))?.payload.ready)return reply(200,{ready:true});
  const account=JSON.parse(Deno.env.get('FIREBASE_SERVICE_ACCOUNT')??'{}');
  if(account.project_id!=='appcodigo-7f245')throw Error('INVALID_PROJECT');
  const key=await importPKCS8(account.private_key,'RS256');
  const assertion=await new SignJWT({scope:'https://www.googleapis.com/auth/datastore'}).setProtectedHeader({alg:'RS256',typ:'JWT'}).setIssuer(account.client_email).setSubject(account.client_email).setAudience('https://oauth2.googleapis.com/token').setIssuedAt().setExpirationTime('1h').sign(key);
  const oauth=await fetch('https://oauth2.googleapis.com/token',{method:'POST',signal:AbortSignal.timeout(10000),body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion})});
  const token=(await boundedJson(oauth)).access_token;if(!oauth.ok||!token)throw Error('OAUTH_FAILED');
  let imported=0;
  const rows=[];
  for(const collection of ['config','restricted_access','access_sessions']){
   let pageToken='';
   for(let page=0;page<100;page++){
    const query=new URLSearchParams({pageSize:'100',...(pageToken?{pageToken}:{})});
    const response=await fetch(base+collection+'?'+query,{signal:AbortSignal.timeout(25000),headers:{Authorization:'Bearer '+token}});
    if(!response.ok)throw Error(response.status===429?'SOURCE_QUOTA':'SOURCE_UNAVAILABLE');
    const body=await boundedJson(response);
    for(const doc of body.documents??[]){
     const path=doc.name.split('/documents/')[1];
     if(collection==='config'&&!/^config\/(restricted_access|promotion_stores|acpOfferValidity|promotion_[a-zA-Z0-9_-]+)$/.test(path))continue;
     rows.push({path,fields:doc.fields??{}});
    }
    pageToken=body.nextPageToken??'';if(!pageToken)break;
    if(page===99)throw Error('SOURCE_TOO_LARGE');
   }
  }
  // Read the entire source before writing. No partial source becomes authoritative.
  const paths=new Set(rows.map(row=>row.path));
  for(const collection of ['config','restricted_access','access_sessions'])for(const old of await documents.list(collection))if(!paths.has(old.path))await documents.write(old.path,{},{deleted:true});
  for(const row of rows){await documents.write(row.path,row.fields);imported++;}
  await cache.write('promotion_control_migration',{ready:true,count:imported,completedAt:Date.now()});
  return reply(200,{ready:true,count:imported});
 }catch(error){console.warn('CONTROL_MIGRATION',error?.message==='SOURCE_QUOTA'?'SOURCE_QUOTA':'UNAVAILABLE');return reply(503,{ready:false});}
});
