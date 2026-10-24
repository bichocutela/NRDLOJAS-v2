import { offerCatalog, activeOffers } from './catalog.mjs';
let token = '', tokenUntil = 0;
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

let stores: number[] = [], storesUntil = 0;
async function knownStore(id: number) {
  if(storesUntil <= Date.now()) {
    const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const response = await fetch(Deno.env.get('SUPABASE_URL') + '/rest/v1/nrd_stores_cache?id=eq.true&select=payload', {
      headers:{ apikey:key, Authorization:`Bearer ${key}` }, signal:AbortSignal.timeout(5000) });
    if(!response.ok) throw new Error('STORES_UNAVAILABLE');
    const rows = await response.json();
    if(!Array.isArray(rows?.[0]?.payload?.stores)) throw new Error('STORES_UNAVAILABLE');
    stores = rows[0].payload.stores.map((store: any) => store.id);
    storesUntil = Date.now() + 300000;
  }
  return stores.includes(id);
}
const cache = new Map<string, any>();
const pending = new Map<string, Promise<any>>();
const headers = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, apikey, content-type', 'Cache-Control': 'private, max-age=60' };
async function refresh(storeId: string) {
  const request = async () => fetch('https://customer.idever.com.br/api/v1/customers/promotions?storeId=' + storeId, {
    headers: { Authorization: `Bearer ${await accessToken()}`, Accept: 'application/json' }, signal: AbortSignal.timeout(20000) });
  let response = await request();
  if(response.status === 401) { tokenUntil = 0; response = await request(); }
  if(!response.ok) throw new Error('SOURCE_UNAVAILABLE');
  const value = { storeId: Number(storeId), offers: offerCatalog(await response.json()), fetchedAt: new Date().toISOString() };
  cache.set(storeId, value);
  return value;
}
Deno.serve(async req => {
  if(req.method === 'OPTIONS') return new Response(null, { headers });
  if(req.method !== 'GET') return Response.json({ error: 'METHOD_NOT_ALLOWED' }, { status:405, headers });
  const storeId = new URL(req.url).searchParams.get('storeId') || '';
  if(!/^[1-9][0-9]{0,14}$/.test(storeId)) return Response.json({ error:'INVALID_STORE' }, { status:400, headers });
  let value = cache.get(storeId);
  try {
    if(!await knownStore(Number(storeId))) return Response.json({ error:'UNKNOWN_STORE' }, { status:400, headers });
    if(!value || Date.parse(value.fetchedAt) + 300000 <= Date.now()) {
      let task = pending.get(storeId);
      if(!task) { task = refresh(storeId).finally(() => pending.delete(storeId)); pending.set(storeId, task); }
      value = await task;
    }
    return Response.json({ ...value, offers: activeOffers(value.offers), stale:false }, { headers });
  } catch (error) {
    const reason=error instanceof Error && /^(CONFIG_UNAVAILABLE|CONFIG_INVALID|AUTH_UNAVAILABLE|AUTH_INVALID|STORES_UNAVAILABLE|SOURCE_UNAVAILABLE|INVALID_CATALOG|INVALID_ITEMS|INVALID_DATES|INVALID_ITEM)$/.test(error.message) ? error.message : 'UPSTREAM_REQUEST_FAILED';
    console.warn('Store offers unavailable:', reason, 'store:', storeId);
    if(value) return Response.json({ ...value, offers:activeOffers(value.offers), stale:true }, { headers });
    return Response.json({ error:'OFFERS_UNAVAILABLE' }, { status:503, headers:{ ...headers, 'Cache-Control':'no-store' } });
  }
});
