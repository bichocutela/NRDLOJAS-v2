import { createRemoteJWKSet, jwtVerify, importPKCS8, SignJWT } from 'npm:jose@5.9.6';
import { createHandler, boundedJson } from './handler.mjs';
import { ServerCache } from './server-cache.mjs';
import { createCategorizer } from './categories.mjs';
import { createAuthorizer } from './access.mjs';
const project = 'appcodigo-7f245';
const jwks = createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
let serviceToken = '', serviceExpires = 0;
async function firestoreToken() {
  if (serviceToken && Date.now() < serviceExpires) return serviceToken;
  const account = JSON.parse(Deno.env.get('FIREBASE_SERVICE_ACCOUNT') ?? '{}');
  if (account.project_id !== project) { console.warn('GATEWAY_FIREBASE_CONFIG',!!account.private_key,!!account.client_email,account.project_id===project); throw Error('INVALID_FIREBASE_PROJECT'); }
  const key = await importPKCS8(account.private_key,'RS256');
  const assertion = await new SignJWT({scope:'https://www.googleapis.com/auth/datastore'})
    .setProtectedHeader({alg:'RS256',typ:'JWT'}).setIssuer(account.client_email).setSubject(account.client_email)
    .setAudience('https://oauth2.googleapis.com/token').setIssuedAt().setExpirationTime('1h').sign(key);
  const response = await fetch('https://oauth2.googleapis.com/token',{method:'POST',signal:AbortSignal.timeout(10000),
    body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion})});
  const data = await boundedJson(response);
  if (!response.ok || typeof data.access_token !== 'string') { console.warn('GATEWAY_FIREBASE_OAUTH_HTTP',response.status); throw Error('FIREBASE_UNAVAILABLE'); }
  serviceToken = data.access_token; serviceExpires = Date.now()+50*60_000; return serviceToken;
}
async function document(path: string) {
  // Existing server-side Firebase service account supplies project quota credentials.
  // User identity and explicit grants are still checked before any ACP consultation.
  const token = await firestoreToken();
  const response = await fetch(`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents/${path}`,{
    signal:AbortSignal.timeout(10000),headers:{Authorization:'Bearer '+token}});
  if (response.status === 404) return {};
  if (!response.ok) {
    const failure = await boundedJson(response).catch(() => ({}));
    console.warn('GATEWAY_FIRESTORE_HTTP',response.status,failure.error?.status ?? 'UNKNOWN');
    throw Error(response.status === 429 ? 'PERMISSIONS_RATE_LIMITED' : 'PERMISSIONS_UNAVAILABLE');
  }
  return (await boundedJson(response)).fields ?? {};
}
// No cached grant: revocation and "liberar para todos" are checked against the server each call.
const appAuthorize = createAuthorizer({document,
  hash: async (value: string) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)))).map(v=>v.toString(16).padStart(2,'0')).join(''),
  verify: async (token: string) => {
  const {payload} = await jwtVerify(token,jwks,{algorithms:['RS256'],issuer:`https://securetoken.google.com/${project}`,audience:project});
  if (!payload.sub || payload.sub.length > 128) throw Error('INVALID_IDENTITY');
  return {uid:payload.sub,email:payload.email,authTime:payload.auth_time,token};
}});
const serverCache = new ServerCache({project, token:firestoreToken});
const categorizer = createCategorizer({cache:serverCache, apiKey:Deno.env.get('GEMINI_API_KEY'),
  model:Deno.env.get('PROMOTION_GEMINI_MODEL') ?? 'gemini-2.5-flash',
  background:(promise:Promise<unknown>) => EdgeRuntime.waitUntil(promise)});
const githubKeys = createRemoteJWKSet(new URL('https://token.actions.githubusercontent.com/.well-known/jwks'));
Deno.serve(createHandler({
  authorize: async (req: Request) => {
    const ciToken = req.headers.get('x-github-oidc-token');
    if (!ciToken) return appAuthorize(req);
    const {payload} = await jwtVerify(ciToken,githubKeys,{algorithms:['RS256'],issuer:'https://token.actions.githubusercontent.com',audience:'nrd-acp-security-check'});
    const ref = payload.ref;
    if (payload.repository !== 'bichocutela/NRDLOJAS-v2' || payload.repository_id !== '1332397983' || payload.repository_owner_id !== '186314089' ||
        !['refs/heads/codex/acp-server-security','refs/heads/main'].includes(String(ref)) ||
        payload.workflow_ref !== `bichocutela/NRDLOJAS-v2/.github/workflows/acp-security.yml@${ref}` ||
        !['push','workflow_dispatch'].includes(String(payload.event_name))) throw Error('INVALID_CI_IDENTITY');
    return {allowed:true,master:false,probe:true};
  },
  enrichPromotions: categorizer,
  credentials: () => ({login:Deno.env.get('NRD_PRICE_LOGIN'),password:Deno.env.get('NRD_PRICE_PASSWORD')}),
}));
