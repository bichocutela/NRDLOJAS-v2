import { createRemoteJWKSet, jwtVerify, importPKCS8, SignJWT } from 'npm:jose@5.9.6';
import { createHandler } from './handler.mjs';
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
  if(!response.ok)throw Error('FIREBASE_FAILED');
  return method==='DELETE'?{}:response.json();
}
async function lookup(uid: string) {
  const data=await call(`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:lookup`,'POST',{localId:[uid]});
  return data?.users?.[0] ?? null;
}
Deno.serve(createHandler({
  verify: async (value: string) => {
    const {payload}=await jwtVerify(value,jwks,{algorithms:['RS256'],issuer:`https://securetoken.google.com/${project}`,audience:project});
    if(!payload.sub || !/^[a-zA-Z0-9_-]{1,128}$/.test(payload.sub) || typeof payload.auth_time !== 'number')throw Error('INVALID_IDENTITY');
    return {uid:payload.sub,email:payload.email,authTime:payload.auth_time};
  },
  lookup,
  grant: async (uid: string) => {
    const doc=await call(base+encodeURIComponent(uid));
    return doc?{login:doc.fields?.login?.stringValue}:null;
  },
  disable: (uid: string) => call(base+encodeURIComponent(uid)+'?updateMask.fieldPaths=enabled','PATCH',{fields:{enabled:{booleanValue:false}}}),
  removeUser: (uid: string) => call(`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:delete`,'POST',{localId:uid}),
  removeGrant: (uid: string) => call(base+encodeURIComponent(uid),'DELETE'),
}));
