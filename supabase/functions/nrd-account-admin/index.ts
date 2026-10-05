import { createRemoteJWKSet, jwtVerify, importPKCS8, SignJWT } from 'npm:jose@5.9.6';
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
const base=`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents/restricted_access/`;
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
    const doc=await call(base+encodeURIComponent(uid));
    return doc?{login:doc.fields?.login?.stringValue}:null;
  };
const adminHandler=createHandler({verify,lookup,grant,
  disable: (uid: string) => call(base+encodeURIComponent(uid)+'?updateMask.fieldPaths=enabled','PATCH',{fields:{enabled:{booleanValue:false}}}),
  updateUser: (uid: string, email: string, password?: string) => call(`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:update`,'POST',{
    localId:uid,email,...(password===undefined?{}:{password}),
  }),
  updateLogin: (uid: string, login: string) => call(base+encodeURIComponent(uid)+'?updateMask.fieldPaths=login','PATCH',{fields:{login:{stringValue:login}}}),
  removeUser: (uid: string) => call(`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:delete`,'POST',{localId:uid}),
  removeGrant: (uid: string) => call(base+encodeURIComponent(uid),'DELETE'),
});
const sessions=`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents/access_sessions/`;
async function sessionWrite(uid: string, method: string, version?: string, body?: unknown) {
  const query=version?'currentDocument.updateTime='+encodeURIComponent(version):'currentDocument.exists=false';
  const response=await fetch(sessions+encodeURIComponent(uid)+'?'+query,{method,signal:AbortSignal.timeout(10000),
    headers:{Authorization:'Bearer '+await token(),'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
  if([409,412,404].includes(response.status))return false;
  if(!response.ok) {
    const data=await response.json().catch(()=>null);
    if(['FAILED_PRECONDITION','ALREADY_EXISTS','ABORTED','NOT_FOUND'].includes(data?.error?.status))return false;
    throw Error('SESSION_WRITE_FAILED');
  }
  return true;
}
const sessionHandler=createSessionHandler({verify,lookup,grant,
  hash: async (value: string) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)))).map(v=>v.toString(16).padStart(2,'0')).join(''),
  readSession: async (uid: string) => {
    const doc=await call(sessions+encodeURIComponent(uid));
    return doc?{deviceHash:doc.fields?.deviceHash?.stringValue,version:doc.updateTime}:null;
  },
  writeSession: (uid: string, value: {deviceHash:string;authTime:number}, version?: string) => sessionWrite(uid,'PATCH',version,{fields:{deviceHash:{stringValue:value.deviceHash},authTime:{integerValue:String(value.authTime)}}}),
  deleteSession: (uid: string, version: string) => sessionWrite(uid,'DELETE',version),
});
Deno.serve(req => new URL(req.url).pathname.endsWith('/session')?sessionHandler(req):adminHandler(req));
