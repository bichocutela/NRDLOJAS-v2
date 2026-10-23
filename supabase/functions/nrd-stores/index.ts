import { storeCatalog } from './catalog.mjs';
const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'public, max-age=60',
  'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, apikey, content-type' };
let token = '', tokenUntil = 0;
let cache: { stores: unknown[]; fetchedAt: string } | undefined;
let cacheUntil = 0;
let pending: Promise<void> | undefined;
async function accessToken() {
  if (token && tokenUntil > Date.now()) return token;
  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
  const response = await fetch(Deno.env.get('SUPABASE_URL') + '/rest/v1/rpc/nrd_encartes_credentials', {
    method: 'POST', headers: { 'Content-Type': 'application/json', apikey: serviceKey, Authorization: `Bearer ${serviceKey}` },
    body: '{}', signal: AbortSignal.timeout(8000) });
  if (!response.ok) throw new Error('CONFIG_UNAVAILABLE');
  const config = await response.json();
  if (!config?.clientId || !config?.clientSecret || config.tenantName !== 'nordestao') throw new Error('CONFIG_INVALID');
  const auth = await fetch('https://auth.idever.com.br/connect/token', {
    method: 'POST', body: new URLSearchParams({ grant_type: 'client_credentials', client_id: config.clientId,
      client_secret: config.clientSecret, scope: 'b2c_api:full', tenant_name: config.tenantName }), signal: AbortSignal.timeout(12000) });
  if (!auth.ok) throw new Error('AUTH_UNAVAILABLE');
  const result = await auth.json();
  if (typeof result.access_token !== 'string' || !result.access_token) throw new Error('AUTH_INVALID');
  token = result.access_token;
  tokenUntil = Date.now() + Math.max(0, Math.min(Number(result.expires_in) || 0, 86400) - 60) * 1000;
  return token;
}
async function refresh() {
  const request = async () => fetch('https://customer.idever.com.br/api/v1/stores', {
    headers: { Authorization: `Bearer ${await accessToken()}`, Accept: 'application/json' }, signal: AbortSignal.timeout(12000) });
  let response = await request();
  if (response.status === 401) { tokenUntil = 0; response = await request(); }
  if (!response.ok) throw new Error('SOURCE_UNAVAILABLE');
  const stores = storeCatalog(await response.json());
  cache = { stores, fetchedAt: new Date().toISOString() };
  cacheUntil = Date.now() + 300000;
}
Deno.serve(async req => {
  if (req.method === 'OPTIONS') return new Response(null, { headers });
  if (req.method !== 'GET') return Response.json({ error: 'METHOD_NOT_ALLOWED' }, { status: 405, headers });
  try {
    if (!cache || cacheUntil <= Date.now()) {
      if (!pending) pending = refresh().finally(() => { pending = undefined; });
      await pending;
    }
    return Response.json(cache, { headers });
  } catch {
    // No raw upstream bodies, credentials, or customer information are exposed.
    return Response.json({ error: 'STORES_UNAVAILABLE' }, { status: 503, headers: { ...headers, 'Cache-Control': 'no-store' } });
  }
});
