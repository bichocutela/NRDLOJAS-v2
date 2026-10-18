const DEFAULT_URL = 'https://lolnlmlnvspipzxatgvj.supabase.co';
const DEFAULT_STORE = 'nordestao12';
// Public browser key exposed by the ACP Tech portal; ACP credentials remain server-only.
const DEFAULT_ANON_KEY = 'sb_publishable_kslyfs4uh1omFnKSjf_tyg_qfjh-N3Q';

function encodeIn(values) {
  return `(${values.map(value => JSON.stringify(String(value))).join(',')})`;
}

function categoryObjects(item) {
  const existing = Array.isArray(item.productCategories) ? item.productCategories : [];
  const price = Number(item.price ?? item.value);
  const previous = Number(item.previous_price ?? item.previousValue);
  const club = Number(item.club_price ?? item.clubValue);
  const take = Number(item.leve_quantity ?? item.quantityTake);
  const pay = Number(item.pague_quantity ?? item.quantityPay);
  const categories = [...existing];
  const labels = new Set(categories.map(category => String(category?.description ?? '').toLowerCase().replace(/[^a-z0-9]/g, '')));
  const add = description => {
    const key = description.toLowerCase().replace(/[^a-z0-9]/g, '');
    if (!labels.has(key)) { categories.push({description}); labels.add(key); }
  };
  if (Number.isFinite(price) && price > 0 && Number.isFinite(previous) && previous > price) add('De-Por');
  if (Number.isFinite(club) && club > 0) add('Clube de Vantagens');
  if (Number.isFinite(take) && Number.isFinite(pay) && take > pay && pay > 0) add('Leve/Pague');
  if (Number(item.wholesale_price ?? item.wholesaleValue) > 0) add('Atacado');
  const type = String(item.promotion_type ?? item.promotionType ?? '').toLowerCase().replace(/[^a-z0-9]/g, '');
  if (['clube', 'club', 'clubedevantagens', 'clubvantagens'].includes(type)) add('Clube de Vantagens');
  if (['leveepague', 'levepague', 'takepay', 'buyxpayy'].includes(type)) add('Leve/Pague');
  return categories;
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
    campaignsCache = (Array.isArray(items) ? items : []).map(item => {
      const campaign = campaignById.get(String(item.campaign_id));
      return ({
      ...item,
      id: item.id ?? `${item.campaign_id}:${item.code ?? item.barcode ?? item.description}`,
      campaignId: item.campaign_id,
      campaignName: campaign?.name ?? null,
      barCode: item.barcode ?? null,
      value: item.value ?? item.price ?? null,
      previousValue: item.previousValue ?? item.previous_price ?? null,
      clubValue: item.clubValue ?? item.club_price ?? null,
      wholesaleValue: item.wholesaleValue ?? item.wholesale_price ?? null,
      wholesaleQuantity: item.wholesaleQuantity ?? item.wholesale_quantity ?? null,
      quantityTake: item.quantityTake ?? item.leve_quantity ?? null,
      quantityPay: item.quantityPay ?? item.pague_quantity ?? null,
      productCategories: categoryObjects(item),
      secondUnitDiscount: item.secondUnitDiscount ?? item.second_unit_discount ?? null,
      unitLimitPerCPF: item.unitLimitPerCPF ?? item.unit_limit ?? null,
      startDate: item.startDate ?? campaign?.start_date ?? null,
      endDate: item.endDate ?? item.valid_until ?? campaign?.end_date ?? null,
      imagem: item.image_url ?? null,
      imageUrl: item.image_url ?? null,
      linkloja: null,
      productUrl: null
      });
    });
    campaignsAt = Date.now();
    return campaignsCache;
  }

  async function products(auth, parameters) {
    const query = {select: '*', chain_id: auth.chainId ? `eq.${auth.chainId}` : undefined, deleted_at: 'is.null', limit: '250'};
    if (!query.chain_id) delete query.chain_id;
    for (const [key, value] of parameters) {
      if (key === 'pageSize') query.limit = String(Math.min(Number(value) || 250, 250));
      if (key === 'code') query.code = `eq.${value}`;
      if (key === 'barCode') query.barcode = `eq.${value}`;
      if (key === 'description') query.description = `ilike.*${String(value).replace(/[*%]/g, '')}*`;
    }
    const rows = await table('chain_products', query, auth.token);
    const campaignRows = await campaignItems(auth);
    const campaignFor = item => (Array.isArray(campaignRows) ? campaignRows : []).find(candidate =>
      (item.code != null && candidate.code != null && String(item.code) === String(candidate.code)) ||
      (item.barcode && candidate.barcode && String(item.barcode) === String(candidate.barcode)) ||
      (item.description && candidate.description && String(item.description).trim().toLowerCase() === String(candidate.description).trim().toLowerCase())
    );
    return (Array.isArray(rows) ? rows : []).map(item => {
      const campaign = campaignFor(item);
      const merged = {
        ...item,
        value: Number(item.value ?? item.price) > 0 ? (item.value ?? item.price) : (campaign?.value ?? campaign?.price ?? item.value ?? item.price),
        price: Number(item.price) > 0 ? item.price : (campaign?.price ?? campaign?.value ?? item.price),
        previous_price: Number(item.previous_price) > 0 ? item.previous_price : (campaign?.previous_price ?? campaign?.previousValue ?? item.previous_price),
        club_price: Number(item.club_price) > 0 ? item.club_price : (campaign?.club_price ?? campaign?.clubValue ?? item.club_price),
        leve_quantity: Number(item.leve_quantity) > 0 ? item.leve_quantity : (campaign?.leve_quantity ?? campaign?.quantityTake ?? item.leve_quantity),
        pague_quantity: Number(item.pague_quantity) > 0 ? item.pague_quantity : (campaign?.pague_quantity ?? campaign?.quantityPay ?? item.pague_quantity),
        promotion_type: item.promotion_type || campaign?.promotion_type || campaign?.promotionType || ''
      };
      return {
        ...merged,
        barCode: merged.barcode ?? null,
        value: merged.value ?? null,
        previousValue: merged.previousValue ?? merged.previous_price ?? null,
        clubValue: merged.clubValue ?? merged.club_price ?? null,
        wholesaleValue: merged.wholesaleValue ?? merged.wholesale_price ?? null,
        wholesaleQuantity: merged.wholesaleQuantity ?? merged.wholesale_quantity ?? null,
        quantityTake: merged.quantityTake ?? merged.leve_quantity ?? null,
        quantityPay: merged.quantityPay ?? merged.pague_quantity ?? null,
        productCategories: categoryObjects(merged),
        secondUnitDiscount: merged.secondUnitDiscount ?? merged.second_unit_discount ?? null,
        unitLimitPerCPF: merged.unitLimitPerCPF ?? merged.unit_limit ?? null,
        endDate: merged.endDate ?? merged.valid_until ?? null,
        imagem: merged.image_url ?? null,
        imageUrl: merged.image_url ?? null, linkloja: null, productUrl: null
      };
    });
  }

  return {
    async consult(input) {
      for (let attempt = 0; attempt < 2; attempt++) {
        try {
          const auth = await signIn();
          const rows = input.path === 'Promotion/all'
            ? await campaignItems(auth)
            : input.path === 'Product/all' ? await products(auth, input.parameters)
            : input.path === 'ProductCategory/all' ? await table('product_categories', {
              select: '*', order: 'name.asc', limit: '250'
            }, auth.token)
            : [];
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
