import { createRemoteJWKSet, jwtVerify } from 'npm:jose@5.9.6';
import { createHandler, boundedJson } from './handler.mjs';
import { createAuthorizer } from './access.mjs';
const project = 'appcodigo-7f245';
const jwks = createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
async function document(path: string, token?: string) {
  // Firestore REST honors the same rules as the existing app. Public config is readable
  // without a login; account grants are read with the verified user's Firebase ID token.
  const response = await fetch(`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents/${path}`,{
    signal:AbortSignal.timeout(10000),headers:token ? {Authorization:'Bearer '+token} : {}});
  if (response.status === 404) return {};
  if (!response.ok) throw Error('PERMISSIONS_UNAVAILABLE');
  return (await boundedJson(response)).fields ?? {};
}
// No cached grant: revocation and "liberar para todos" are checked against the server each call.
const appAuthorize = createAuthorizer({document, verify: async (token: string) => {
  const {payload} = await jwtVerify(token,jwks,{algorithms:['RS256'],issuer:`https://securetoken.google.com/${project}`,audience:project});
  if (!payload.sub || payload.sub.length > 128) throw Error('INVALID_IDENTITY');
  return {uid:payload.sub,email:payload.email,token};
}});
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
  credentials: () => ({login:Deno.env.get('NRD_PRICE_LOGIN'),password:Deno.env.get('NRD_PRICE_PASSWORD')}),
}));
