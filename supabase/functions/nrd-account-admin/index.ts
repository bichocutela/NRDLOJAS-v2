import { createRemoteJWKSet, jwtVerify, importPKCS8, SignJWT } from 'npm:jose@5.9.6';
import {SupabaseCache} from '../nrd-price-gateway/supabase-cache.mjs';
import {ControlDocuments} from '../nrd-promotion-control/documents.mjs';
import { createHandler } from './handler.mjs';
import { createSessionHandler } from './session.mjs';
const project = 'appcodigo-7f245';
const jwks = createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
let serviceToken = '', expires = 0;
async function token() {
  if (serviceToken && Date.now() < expires) return serviceToken;
  const account = JSON.parse(Deno.env.get('FIREBASE_SERVICE_ACCOUNT') ?? '{}');
  if (account.project_id !== project) throw Error('INVALID_PROJECT');
  const key = await importPKCS8(account.private_key, 'RS256');
  const assertion = await new SignJWT({scope:'https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/identitytoolkit'})
    .setProtectedHeader({alg:'RS256',typ:'JWT'}).setIssuer(account.client_email).setSubject(account.client_email)
    .setAudience('https://oauth2.googleapis.com/token').setIssuedAt().setExpirationTime('1h').sign(key);
  const response = await fetch('https://oauth2.googleapis.com/token',{method:'POST',signal:AbortSignal.timeout(10000),
    body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion})});
  const data=await response.json();
  if(!response.ok || typeof data.access_token !== 'string') throw Error('OAUTH_FAILED');
  serviceToken=data.access_token;expires=Date.now()+50*60_000;return serviceToken;
}
const settings={url:Deno.env.get('SUPABASE_URL'),key:Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')};
const primary=new ControlDocuments(settings),cache=new SupabaseCache(settings);
const ready=async()=>!!(await cache.get('promotion_control_migration'))?.payload.ready;
const documentBase=`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents/`;
const documents={
 get:async(path:string)=>{
  if(await ready())return primary.get(path);
  const doc=await call(documentBase+path);
  return doc?{fields:doc.fields??{},version:doc.updateTime,deleted:false}:null;
 },
 fields:async(path:string)=>{
  const row=await documents.get(path);return row&&!row.deleted?row.fields:{};
 },
 write:async(path:string,fields:Record<string,unknown>,options:any={})=>{
  if(await ready())return primary.write(path,fields,options);
  const query=new URLSearchParams();
  if(options.version)query.set('currentDocument.updateTime',String(options.version));
  else if(options.insertOnly)query.set('currentDocument.exists','false');
  if(options.merge)for(const field of Object.keys(fields))query.append('updateMask.fieldPaths',field);
  const response=await fetch(documentBase+path+'?'+query,{method:options.deleted?'DELETE':'PATCH',signal:AbortSignal.timeout(10000),headers:{Authorization:'Bearer '+await token(),'Content-Type':'application/json'},...(options.deleted?{}:{body:JSON.stringify({fields})})});
  if([409,412,404].includes(response.status))return null;
  if(!response.ok)throw Error('FIREBASE_FAILED');
  return {ok:true};
 }
};
async function call(url: string, method='GET', body?: unknown) {
  const response=await fetch(url,{method,signal:AbortSignal.timeout(10000),headers:{Authorization:'Bearer '+await token(),'Content-Type':'application/json'},
    ...(body === undefined ? {} : {body:JSON.stringify(body)})});
  if(response.status===404 && method==='GET')return null;
  if(!response.ok) {
    const error=await response.json().catch(()=>null);
    throw Error(error?.error?.message==='EMAIL_EXISTS'?'EMAIL_EXISTS':'FIREBASE_FAILED');
  }
  return method==='DELETE'?{}:response.json();
}
async function lookup(uid: string) {
  const data=await call(`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:lookup`,'POST',{localId:[uid]});
  return data?.users?.[0] ?? null;
}
const verify = async (value: string) => {
    const {payload}=await jwtVerify(value,jwks,{algorithms:['RS256'],issuer:`https://securetoken.google.com/${project}`,audience:project});
    if(!payload.sub || !/^[a-zA-Z0-9_-]{1,128}$/.test(payload.sub) || typeof payload.auth_time !== 'number')throw Error('INVALID_IDENTITY');
    return {uid:payload.sub,email:payload.email,authTime:payload.auth_time};
  };
const grant = async (uid: string) => {
    const fields=await documents.fields(`restricted_access/${uid}`);
    const doc=Object.keys(fields).length?{fields}:null;
    return doc?{login:doc.fields?.login?.stringValue}:null;
  };
const adminHandler=createHandler({verify,lookup,grant,
  disable: (uid: string) => documents.write(`restricted_access/${uid}`,{enabled:{booleanValue:false}},{merge:true}),
  updateUser: (uid: string, email: string, password?: string) => call(`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:update`,'POST',{
    localId:uid,email,...(password===undefined?{}:{password}),
  }),
  updateLogin: (uid: string, login: string) => documents.write(`restricted_access/${uid}`,{login:{stringValue:login}},{merge:true}),
  removeUser: (uid: string) => call(`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:delete`,'POST',{localId:uid}),
  removeGrant: (uid: string) => documents.write(`restricted_access/${uid}`,{},{deleted:true}),
});
const sessionHandler=createSessionHandler({verify,lookup,grant,
  hash: async (value: string) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)))).map(v=>v.toString(16).padStart(2,'0')).join(''),
  readSession: async (uid: string) => {
    const doc=await documents.get(`access_sessions/${uid}`);
    return doc&&!doc.deleted?{deviceHash:doc.fields?.deviceHash?.stringValue,version:String(doc.version)}:null;
  },
  writeSession: async (uid: string, value: {deviceHash:string;authTime:number}, version?: string) => {
    const previous=version?null:await documents.get(`access_sessions/${uid}`);
    if (!version && previous && !previous.deleted) return false;
    return !!await documents.write(`access_sessions/${uid}`,{deviceHash:{stringValue:value.deviceHash},authTime:{integerValue:String(value.authTime)}},
      {version:version?(await ready()?Number(version):version):previous?.version??null,insertOnly:!version&&!previous});
  },
  deleteSession: async (uid: string, version: string) => !!await documents.write(`access_sessions/${uid}`,{},{version:await ready()?Number(version):version,deleted:true}),
});
Deno.serve(req => new URL(req.url).pathname.endsWith('/session')?sessionHandler(req):adminHandler(req));
