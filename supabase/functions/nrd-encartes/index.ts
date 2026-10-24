import { encartePdf } from './assets.mjs';
import { natalCatalog, validDate } from './catalog.mjs';
import { notifyCatalog, notificationPayload } from './notifications.mjs';
import { sendLeaflet } from './fcm.ts';

// Platform JWT verification accepts the app's public anon key; no customer login.
const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'public, max-age=300',
  'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, apikey, content-type' };
const base = Deno.env.get('SUPABASE_URL')!;
const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
async function database(path: string, body: unknown, method = 'POST') {
  const response = await fetch(`${base}/rest/v1/${path}`, { method,
    headers: { apikey: key, Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body), signal: AbortSignal.timeout(8000) });
  if (!response.ok) throw new Error('Database unavailable');
  return response.status === 204 ? null : response.json();
}
async function configuration() { return database('rpc/nrd_encartes_credentials', {}); }
let token = '', tokenUntil = 0;
let cache: { tabloids: Array<{ id: number; name: string; startDate: string; endDate: string }>; fetchedAt: string } | undefined;
let cacheUntil = 0;
let pending: Promise<void> | undefined;

async function accessToken() {
  if (token && tokenUntil > Date.now()) return token;
  const config = await configuration();
  if (!config?.clientId || !config?.clientSecret || config.tenantName !== 'nordestao') throw new Error('Configuration unavailable');
  const response = await fetch('https://auth.idever.com.br/connect/token', {
    method: 'POST', body: new URLSearchParams({ grant_type: 'client_credentials',
      client_id: config.clientId, client_secret: config.clientSecret, scope: 'b2c_api:full', tenant_name: 'nordestao' }),
    signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) throw new Error('Source authentication unavailable');
  const result = await response.json();
  if (typeof result.access_token !== 'string' || !result.access_token) throw new Error('Invalid source response');
  token = result.access_token;
  tokenUntil = Date.now() + Math.max(0, Math.min(Number(result.expires_in) || 0, 86400) - 60) * 1000;
  return token;
}
async function refresh() {
  let response = await fetch('https://customer.idever.com.br/api/v1/tabloids', {
    headers: { Authorization: `Bearer ${await accessToken()}`, Accept: 'application/json' }, signal: AbortSignal.timeout(12000),
  });
  if (response.status === 401) {
    tokenUntil = 0;
    response = await fetch('https://customer.idever.com.br/api/v1/tabloids', {
      headers: { Authorization: `Bearer ${await accessToken()}`, Accept: 'application/json' }, signal: AbortSignal.timeout(12000),
    });
  }
  if (!response.ok) throw new Error('Source unavailable');
  const tabloids = natalCatalog(await response.json());
  cache = { tabloids, fetchedAt: new Date().toISOString() };
  cacheUntil = Date.now() + 300000;
}
Deno.serve(async req => {
  if (req.method === 'OPTIONS') return new Response(null, { headers });
  if (req.method === 'POST') {
    try {
      const config = await configuration();
      const supplied = req.headers.get('x-nrd-encartes-scheduler');
      if (!config?.schedulerKey || supplied !== config.schedulerKey)
        return new Response(JSON.stringify({ error: 'FORBIDDEN' }), { status: 403, headers: { ...headers, 'Cache-Control': 'no-store' } });
      const body = await req.json().catch(() => ({}));
      if (!['sync', 'seed', 'probe'].includes(body.mode ?? 'sync')) return new Response(null, { status: 400 });
      // Fetch a fresh authoritative snapshot. Caller cannot submit a title, ID or recipient.
      await refresh();
      const catalog = cache!.tabloids.filter(t => validDate(t.endDate) >= Date.now());
      if (body.mode === 'probe') {
        if (catalog[0]) await sendLeaflet(notificationPayload(catalog[0]), true);
        return new Response(JSON.stringify({ ready: true, fcmValidated: catalog.length > 0 }), { headers: { ...headers, 'Cache-Control': 'no-store' } });
      }
      const result = await notifyCatalog(catalog, {
        baseline: body.mode === 'seed',
        claim: (items: unknown[], baseline: boolean) => database('rpc/nrd_claim_encartes', { items, baseline }),
        send: sendLeaflet,
        acknowledge: (event: any) => database(`nrd_encarte_events?id=eq.${event.id}&claim_id=eq.${event.claim_id}`,
          { notified_at: new Date().toISOString(), claimed_until: null }, 'PATCH'),
        release: (event: any) => database(`nrd_encarte_events?id=eq.${event.id}&claim_id=eq.${event.claim_id}`,
          { claimed_until: null }, 'PATCH'),
      });
      return new Response(JSON.stringify(result), { headers: { ...headers, 'Cache-Control': 'no-store' } });
    } catch {
      return new Response(JSON.stringify({ error: 'ENCARTES_NOTIFICATION_SYNC_FAILED' }), { status: 503,
        headers: { ...headers, 'Cache-Control': 'no-store' } });
    }
  }
  if (req.method !== 'GET') return new Response(JSON.stringify({ error: 'METHOD_NOT_ALLOWED' }), { status: 405, headers });
  try {
    if (!cache || cacheUntil < Date.now()) {
      if (!pending) pending = refresh().finally(() => { pending = undefined; });
      await pending;
    }
    const assetId = new URL(req.url).searchParams.get('asset');
    if (assetId !== null) return await encartePdf(assetId, cache!.tabloids);
    // Recheck expiry even while the metadata cache is warm.
    return new Response(JSON.stringify({ ...cache, tabloids: cache!.tabloids.filter(t => validDate(t.endDate) >= Date.now()) }), { headers });
  } catch {
    // Never return upstream response bodies, tenant tokens or secret configuration.
    return new Response(JSON.stringify({ error: 'ENCARTES_UNAVAILABLE' }), { status: 503,
      headers: { ...headers, 'Cache-Control': 'no-store' } });
  }
});
