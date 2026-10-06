import {importPKCS8, SignJWT, createRemoteJWKSet, jwtVerify} from 'npm:jose@5.9.6';
import {createHandler} from './handler.mjs';
const project='appcodigo-7f245';
const base=`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents`;
let cached='',expires=0;
async function token() {
  if(cached && Date.now()<expires)return cached;
  const account=JSON.parse(Deno.env.get('FIREBASE_SERVICE_ACCOUNT') ?? '{}');
  if(account.project_id!==project)throw Error('INVALID_PROJECT');
  const key=await importPKCS8(account.private_key,'RS256');
  const assertion=await new SignJWT({scope:'https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/iam.test'})
    .setProtectedHeader({alg:'RS256',typ:'JWT'}).setIssuer(account.client_email).setSubject(account.client_email)
    .setAudience('https://oauth2.googleapis.com/token').setIssuedAt().setExpirationTime('1h').sign(key);
  const response=await fetch('https://oauth2.googleapis.com/token',{method:'POST',signal:AbortSignal.timeout(10000),
    body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion})});
  const data=await response.json();if(!response.ok || typeof data.access_token!=='string')throw Error('OAUTH_FAILED');
  cached=data.access_token;expires=Date.now()+50*60_000;return cached;
}
async function call(url: string,method='GET',body?: unknown) {
  return fetch(url,{method,signal:AbortSignal.timeout(15000),headers:{Authorization:'Bearer '+await token(),'Content-Type':'application/json'},
    ...(body===undefined?{}:{body:JSON.stringify(body)})});
}
async function read(path: string) {
  const response=await call(base+'/'+path);
  if(response.status===404)return null;
  if(!response.ok){
    const detail=await response.json().catch(()=>({}));
    const reasons=(detail.error?.details ?? []).map((item: {reason?: string})=>item.reason).filter((reason: unknown)=>typeof reason==='string' && /^[A-Z_]+$/.test(reason));
    console.error('installation_firestore_read_failed', JSON.stringify({status:response.status,reasons}));
    throw Error('READ_HTTP_'+response.status);
  }
  return response.json();
}
async function digest(value: string) {
  const data=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value));
  return Array.from(new Uint8Array(data)).map(v=>v.toString(16).padStart(2,'0')).join('');
}
function leaseFields(sent: boolean,until: number) {
  return {fields:{sent:{booleanValue:sent},leaseUntil:{timestampValue:new Date(until).toISOString()}}};
}
async function updateLease(lease: {id: string;updateTime: string},sent: boolean) {
  const response=await call(`${base}/app_installation_deliveries/${lease.id}?currentDocument.updateTime=${encodeURIComponent(lease.updateTime)}`,'PATCH',leaseFields(sent,0));
  if(!response.ok)throw Error('FINALIZE_FAILED');
}
const handler=createHandler({
  readEvent: async (proof: string)=>{
    const doc=await read('app_installation_events/'+await digest(proof));
    if(!doc)return null;
    const f=doc.fields ?? {};
    return {deviceIdHash:f.deviceIdHash?.stringValue,appVersion:f.appVersion?.stringValue,
      appVersionCode:Number(f.appVersionCode?.integerValue),firstSeenAt:f.firstSeenAt?.timestampValue,createdAt:f.createdAt?.timestampValue};
  },
  claim: async (id: string)=>{
    const current=await read('app_installation_deliveries/'+id);
    if(current?.fields?.sent?.booleanValue===true)return {sent:true};
    if(current && Date.parse(current.fields?.leaseUntil?.timestampValue)>Date.now())return {};
    const url=current?`${base}/app_installation_deliveries/${id}?currentDocument.updateTime=${encodeURIComponent(current.updateTime)}`:
      `${base}/app_installation_deliveries?documentId=${id}`;
    const response=await call(url,current?'PATCH':'POST',leaseFields(false,Date.now()+120_000));
    if([409,412].includes(response.status))return {};
    if(!response.ok)throw Error('CLAIM_FAILED');
    const doc=await response.json();return {lease:{id,updateTime:doc.updateTime}};
  },
  send: async (data: Record<string,string>)=>{
    const response=await call(`https://fcm.googleapis.com/v1/projects/${project}/messages:send`,'POST',{
      message:{topic:'master_updates',data,android:{priority:'high',ttl:'86400s'}}});
    if(!response.ok)throw Error('FCM_FAILED');
  },
  finish:(lease: {id: string;updateTime: string})=>updateLease(lease,true),
  release:(lease: {id: string;updateTime: string})=>updateLease(lease,false),
});
// CI can only perform a read-only readiness probe, never publish an installation.
const githubKeys=createRemoteJWKSet(new URL('https://token.actions.githubusercontent.com/.well-known/jwks'));
Deno.serve(async request=>{
  const ci=request.headers.get('x-github-oidc-token');
  if(!ci)return handler(request);
  try {
    const {payload}=await jwtVerify(ci,githubKeys,{algorithms:['RS256'],issuer:'https://token.actions.githubusercontent.com',audience:'nrd-installation-event-check'});
    if(request.method!=='POST' || payload.repository!=='bichocutela/NRDLOJAS-v2' || payload.repository_id!=='1332397983' ||
      payload.repository_owner_id!=='186314089' || payload.ref!=='refs/heads/main' ||
      payload.workflow_ref!=='bichocutela/NRDLOJAS-v2/.github/workflows/main.yml@refs/heads/main' ||
      !['push','workflow_dispatch'].includes(String(payload.event_name)))throw Error('UNAUTHORIZED');
  }catch{return Response.json({error:'UNAUTHORIZED'},{status:403});}
  let readinessStage = 'oauth';
  try {
    await token();
    readinessStage = 'iam';
    const permissions=['datastore.entities.get','datastore.entities.create','datastore.entities.update','cloudmessaging.messages.create'];
    const response=await call(`https://cloudresourcemanager.googleapis.com/v1/projects/${project}:testIamPermissions`,'POST',{permissions});
    const result=await response.json();
    if(!response.ok)throw Error('IAM_HTTP_'+response.status);
    const missingPermissions=permissions.filter(p=>!result.permissions?.includes(p));
    if(missingPermissions.length){
      console.error('installation_readiness_missing_permissions', JSON.stringify(missingPermissions));
      throw Error('MISSING_PERMISSIONS');
    }
    readinessStage = 'firestore';
    await read('app_installation_deliveries/readiness_probe');
    return Response.json({ready:true});
  }catch(error){
    const message=error instanceof Error ? error.message : '';
    const code=/^[A-Z_]+(?:_[0-9]{3})?$/.test(message) ? message : 'DEPENDENCY_FAILED';
    console.error('installation_readiness_failed', JSON.stringify({stage:readinessStage,code}));
    return Response.json({error:'SERVER_NOT_READY'},{status:503});
  }
});
