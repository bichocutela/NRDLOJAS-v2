import { offerCatalog, activeOffers } from './catalog.mjs';
import { createOfferFailover } from './failover.mjs';
import { createPortalSession } from './portal-session.mjs';
import { createPortalOffers } from './portal-catalog.mjs';
import { createIntegrationLogins } from '../_shared/integration-logins.mjs';

async function serverJson(path: string, options: RequestInit = {}) {
  const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
  const response = await fetch(Deno.env.get('SUPABASE_URL') + '/rest/v1/' + path, {
    ...options, headers: { apikey: key, Authorization: `Bearer ${key}`, 'Content-Type': 'application/json',
      ...options.headers }, signal: AbortSignal.timeout(8000) });
  if (!response.ok) throw new Error('SERVER_STORAGE_UNAVAILABLE');
  return response.status === 204 || response.headers.get('Content-Length') === '0' ? null : response.json();
}

const integrationLogins = createIntegrationLogins({ url:Deno.env.get('SUPABASE_URL'),
  key:Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') });
const portal = createPortalSession({
  onHttpFailure: (path: string, status: number) => console.warn('Portal source HTTP status:', path, status),
  getCredentials: async () => { const value = await integrationLogins.get('offers');
    return { user:value.login, password:value.password, revision:value.revision }; } });
const authenticatedOffers = createPortalOffers({ session: portal,
  beforeQuery: () => integrationLogins.get('offers', true),
  lock: (owner: string, operation: string, original?: number, selected?: number) => serverJson('rpc/nrd_portal_offer_lock', {
    method: 'POST', body: JSON.stringify({ p_owner: owner, p_operation: operation,
      p_original: original ?? null, p_selected: selected ?? null }) }) });

function sourceFailure(source: string, storeId: number, error: unknown) {
  const allowed = new Set(['CONFIG_UNAVAILABLE','CONFIG_INVALID','AUTH_UNAVAILABLE','AUTH_INVALID',
    'SOURCE_UNAVAILABLE','INVALID_CATALOG','INVALID_ITEMS','INVALID_DATES','INVALID_ITEM',
    'SERVER_STORAGE_UNAVAILABLE','PORTAL_AUTH_UNAVAILABLE','PORTAL_RECONNECT_DEFERRED',
    'PORTAL_SOURCE_UNAVAILABLE','PORTAL_BUSY','PORTAL_LEASE_LOST','PORTAL_STORE_INVALID',
    'PORTAL_STORE_MISMATCH','PORTAL_RESTORE_FAILED']);
  const reason = error instanceof Error && allowed.has(error.message) ? error.message : 'UPSTREAM_REQUEST_FAILED';
  console.warn('Store offers source unavailable:', source, reason, 'store:', storeId);
}
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
const headers = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, apikey, content-type', 'Cache-Control': 'private, max-age=60' };
async function primaryOffers(storeId: number) {
  const request = async () => fetch('https://customer.idever.com.br/api/v1/customers/promotions?storeId=' + storeId, {
    headers: { Authorization: `Bearer ${await accessToken()}`, Accept: 'application/json' }, signal: AbortSignal.timeout(20000) });
  let response = await request();
  if(response.status === 401) { tokenUntil = 0; response = await request(); }
  if(!response.ok) throw new Error('SOURCE_UNAVAILABLE');
  return { storeId, offers: offerCatalog(await response.json()) };
}
const gateway = createOfferFailover({
  primary: async (storeId: number) => {
    try { return await primaryOffers(storeId); }
    catch (error) { sourceFailure('primary', storeId, error); throw error; }
  },
  backup: async (storeId: number) => {
    try { return await authenticatedOffers(storeId); }
    catch (error) { sourceFailure('authenticated', storeId, error); throw error; }
  },
  readCache: async (storeId: number) => {
    const rows = await serverJson('nrd_store_offer_cache?store_id=eq.' + storeId + '&select=payload');
    return rows?.[0]?.payload ?? null;
  },
  writeCache: async (value: any) => serverJson('nrd_store_offer_cache?on_conflict=store_id', {
    method: 'POST', headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
    body: JSON.stringify({ store_id: value.storeId, payload: value, fetched_at: value.fetchedAt }) })
});
Deno.serve(async req => {
  if(req.method === 'OPTIONS') return new Response(null, { headers });
  if(req.method !== 'GET') return Response.json({ error: 'METHOD_NOT_ALLOWED' }, { status:405, headers });
  const storeId = new URL(req.url).searchParams.get('storeId') || '';
  if(!/^[1-9][0-9]{0,14}$/.test(storeId)) return Response.json({ error:'INVALID_STORE' }, { status:400, headers });
  try {
    if(!await knownStore(Number(storeId))) return Response.json({ error:'UNKNOWN_STORE' }, { status:400, headers });
    const value = await gateway.get(Number(storeId));
    return Response.json({ ...value, offers: activeOffers(value.offers) }, { headers });
  } catch {
    return Response.json({ error:'OFFERS_UNAVAILABLE' }, { status:503, headers:{ ...headers, 'Cache-Control':'no-store' } });
  }
});
