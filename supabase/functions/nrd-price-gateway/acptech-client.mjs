const DEFAULT_URL = 'https://lolnlmlnvspipzxatgvj.supabase.co';
const DEFAULT_STORE = 'nordestao12';
// Public browser key exposed by the ACP Tech portal; ACP credentials remain server-only.
const DEFAULT_ANON_KEY = 'sb_publishable_kslyfs4uh1omFnKSjf_tyg_qfjh-N3Q';

function encodeIn(values) {
  return `(${values.map(value => JSON.stringify(String(value))).join(',')})`;
}

export function createAcpTechClient({fetcher = fetch, boundedJson, credentials, config = {}}) {
  const supabaseUrl = String(config.url || DEFAULT_URL).replace(/\/$/, '');
  const storeSlug = String(config.storeSlug || DEFAULT_STORE);
  const anonKey = String(config.anonKey || DEFAULT_ANON_KEY);
  let session = null;
  let sessionAt = 0;
  let signingIn = null;
  let campaignsCache = null;
  let campaignsAt = 0;

  function headers(token) {
    if (!anonKey) throw Error('ACPTECH_ANON_KEY_NOT_CONFIGURED');
    return {Accept: 'application/json', apikey: anonKey, Authorization: `Bearer ${token || anonKey}`};
  }

  async function json(url, options = {}) {
    const response = await fetcher(url, {
      ...options,
      redirect: 'error',
      signal: AbortSignal.timeout(20000),
      headers: {...headers(options.token), ...(options.headers || {})}
    });
    const body = await boundedJson(response);
    if (!response.ok) throw Error('ACPTECH_UPSTREAM_FAILURE');
    return body;
  }

  async function signIn() {
    if (session && Date.now() - sessionAt < 10 * 60_000) return session;
    if (signingIn) return signingIn;
    signingIn = (async () => {
      const secret = credentials();
      if (!secret?.login || !secret?.password) throw Error('NOT_READY');
      const resolved = await json(`${supabaseUrl}/functions/v1/store-auth`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({action: 'resolve_email', username: secret.login, storeSlug})
      });
      if (!resolved?.email || resolved.error) throw Error('ACPTECH_LOGIN_FAILURE');
      const auth = await json(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({email: resolved.email, password: secret.password})
      });
      if (!auth?.access_token) throw Error('ACPTECH_LOGIN_FAILURE');
      const boot = await json(`${supabaseUrl}/functions/v1/store-auth`, {
        method: 'POST', token: auth.access_token,
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({action: 'bootstrap', storeSlug})
      });
      const storeId = boot?.user?.store_id;
      // Some ACP Tech store users have no chain_id in bootstrap. The product
      // table is keyed by chain_id, and an unfiltered read times out.
      let chainId = boot?.user?.chain_id || null;
      if (!storeId) throw Error('ACPTECH_STORE_NOT_FOUND');
      if (!chainId) {
        const stores = await json(`${supabaseUrl}/rest/v1/stores?select=chain_id&id=eq.${encodeURIComponent(storeId)}&limit=1`, {
          token: auth.access_token
        });
        chainId = Array.isArray(stores) ? stores[0]?.chain_id || null : null;
      }
      if (!chainId) throw Error('ACPTECH_CHAIN_NOT_FOUND');
      session = {token: auth.access_token, storeId, chainId};
      sessionAt = Date.now();
      campaignsCache = null;
      return session;
    })();
    try { return await signingIn; } finally { signingIn = null; }
  }

  async function table(name, query, token) {
    const url = new URL(`${supabaseUrl}/rest/v1/${name}`);
    for (const [key, value] of Object.entries(query)) url.searchParams.set(key, value);
    return json(url, {token});
  }

  async function campaignItems(auth) {
    if (campaignsCache && Date.now() - campaignsAt < 60_000) return campaignsCache;
    const campaignQuery = {
      select: 'id,name,code,start_date,end_date,active,deleted_at,updated_at,store_id,chain_id',
      active: 'eq.true',
      deleted_at: 'is.null',
      order: 'updated_at.desc'
    };
    campaignQuery[auth.chainId ? 'or' : 'store_id'] = auth.chainId
      ? `(store_id.eq.${auth.storeId},and(store_id.is.null,chain_id.eq.${auth.chainId}))`
      : `eq.${auth.storeId}`;
    const campaigns = await table('campaigns', campaignQuery, auth.token);
    const ids = (Array.isArray(campaigns) ? campaigns : []).map(c => c.id).filter(Boolean);
    if (!ids.length) return [];
    const items = await table('campaign_items', {
      select: '*',
      campaign_id: `in.${encodeIn(ids)}`,
      order: 'display_order.asc'
    }, auth.token);
    const campaignById = new Map(campaigns.map(c => [String(c.id), c]));
    campaignsCache = (Array.isArray(items) ? items : []).map(item => ({
      ...item,
      id: item.id ?? `${item.campaign_id}:${item.code ?? item.barcode ?? item.description}`,
      campaignId: item.campaign_id,
      campaignName: campaignById.get(String(item.campaign_id))?.name ?? null,
      barCode: item.barcode ?? null,
      imagem: item.image_url ?? null,
      imageUrl: item.image_url ?? null,
      linkloja: null,
      productUrl: null
    }));
    campaignsAt = Date.now();
    return campaignsCache;
  }

  async function products(auth, parameters) {
    const query = {select: '*', chain_id: auth.chainId ? `eq.${auth.chainId}` : undefined, limit: '250'};
    if (!query.chain_id) delete query.chain_id;
    for (const [key, value] of parameters) {
      if (key === 'pageSize') query.limit = String(Math.min(Number(value) || 250, 250));
      if (key === 'code') query.code = `eq.${value}`;
      if (key === 'barCode') query.barcode = `eq.${value}`;
      if (key === 'description') query.description = `ilike.*${String(value).replace(/[*%]/g, '')}*`;
    }
    const rows = await table('chain_products', query, auth.token);
    return (Array.isArray(rows) ? rows : []).map(item => ({
      ...item, barCode: item.barcode ?? null, imagem: item.image_url ?? null,
      imageUrl: item.image_url ?? null, linkloja: null, productUrl: null
    }));
  }

  return {
    async consult(input) {
      for (let attempt = 0; attempt < 2; attempt++) {
        try {
          const auth = await signIn();
          const rows = input.path === 'Promotion/all'
            ? await campaignItems(auth)
            : input.path === 'Product/all' ? await products(auth, input.parameters) : [];
          const page = Number(input.parameters.find(([key]) => key === 'pageIndex')?.[1] ?? 0);
          const size = Math.min(Number(input.parameters.find(([key]) => key === 'pageSize')?.[1] ?? 250), 250);
          return {items: rows.slice(page * size, (page + 1) * size), pageIndex: page,
            totalCount: rows.length, totalPages: rows.length ? Math.ceil(rows.length / size) : 0};
        } catch (error) {
          if (attempt === 0 && /LOGIN|SESSION|UPSTREAM|STORE_NOT_FOUND|CHAIN_NOT_FOUND/.test(String(error?.message))) {
            session = null; campaignsCache = null; continue;
          }
          throw error;
        }
      }
      throw Error('ACPTECH_UPSTREAM_FAILURE');
    }
  };
}
