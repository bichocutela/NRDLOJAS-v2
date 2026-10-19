import {mergeCommercial, normalizeCommercial, notExpired} from './commercial.mjs';

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

export function createAcpTechClient({fetcher = fetch, boundedJson, credentials, config = {}, now = () => Date.now()}) {
  const supabaseUrl = String(config.url || DEFAULT_URL).replace(/\/$/, '');
  const storeSlug = String(config.storeSlug || DEFAULT_STORE);
  const anonKey = String(config.anonKey || DEFAULT_ANON_KEY);
  let session = null;
  let sessionAt = 0;
  let signingIn = null;
  let campaignsCache = null;
  let campaignsAt = 0;
  let campaignsLoading = null;
  const scopedCampaignCache = new Map();
  const scopedCampaignLoads = new Map();
  let promotionsCache = null;
  let promotionsAt = 0;

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
      campaignsLoading = null;
      scopedCampaignCache.clear();
      scopedCampaignLoads.clear();
      promotionsCache = null;
      return session;
    })();
    try { return await signingIn; } finally { signingIn = null; }
  }

  async function table(name, query, token) {
    const url = new URL(`${supabaseUrl}/rest/v1/${name}`);
    for (const [key, value] of Object.entries(query)) url.searchParams.set(key, value);
    return json(url, {token});
  }

  async function tablePage(name, query, token, count = 'planned') {
    const url = new URL(`${supabaseUrl}/rest/v1/${name}`);
    for (const [key, value] of Object.entries(query)) url.searchParams.set(key, value);
    const response = await fetcher(url, {
      redirect: 'error',
      signal: AbortSignal.timeout(20000),
      headers: {...headers(token), Prefer: `count=${count}`}
    });
    const body = await boundedJson(response);
    if (!response.ok && response.status === 416) {
      const available = typeof body?.details === 'string' ? body.details.match(/only (\d+) rows/) : null;
      return {rows: [], total: available ? Number(available[1]) : 0};
    }
    if (!response.ok) {
      console.warn('ACPTECH_TABLE_FAILURE', JSON.stringify({table: name, status: response.status,
        code: /^[A-Z0-9_]+$/.test(String(body?.code ?? '')) ? body.code : null}));
      throw Error('ACPTECH_UPSTREAM_FAILURE');
    }
    const range = response.headers?.get?.('content-range') || '';
    const match = range.match(/\/([0-9]+|\*)$/);
    return {rows: Array.isArray(body) ? body : [], hasRange: Boolean(range), total: match && match[1] !== '*' ? Number(match[1]) : null};
  }

  async function tableAll(name, query, token, count = 'exact') {
    const rows = [];
    for (let page = 0; page < 100; page++) {
      const result = await tablePage(name, {...query, limit: '250', offset: String(rows.length)}, token, count);
      rows.push(...result.rows);
      if (rows.length > 10000) throw Error('ACPTECH_RESULT_TOO_LARGE');
      if (!result.rows.length || (count === 'exact' && result.total != null && rows.length >= result.total) ||
          (!result.hasRange && result.rows.length < 250)) return rows;
    }
    throw Error('ACPTECH_INCOMPLETE_RESULT');
  }

  async function rpcPage(name, body, token) {
    const response = await fetcher(`${supabaseUrl}/rest/v1/rpc/${name}`, {
      method: 'POST', body: JSON.stringify(body),
      redirect: 'error', signal: AbortSignal.timeout(20000),
      headers: {...headers(token), 'Content-Type': 'application/json'}
    });
    const payload = await boundedJson(response);
    if (!response.ok) {
      console.warn('ACPTECH_RPC_FAILURE', JSON.stringify({rpc: name, status: response.status,
        code: /^[A-Z0-9_]+$/.test(String(payload?.code ?? '')) ? payload.code : null}));
      throw Error('ACPTECH_UPSTREAM_FAILURE');
    }
    return {rows: Array.isArray(payload) ? payload : [], total: null};
  }

  async function rpcValue(name, body, token) {
    const response = await fetcher(`${supabaseUrl}/rest/v1/rpc/${name}`, {
      method: 'POST', body: JSON.stringify(body),
      redirect: 'error', signal: AbortSignal.timeout(20000),
      headers: {...headers(token), 'Content-Type': 'application/json'}
    });
    const payload = await boundedJson(response);
    if (!response.ok) throw Error('ACPTECH_UPSTREAM_FAILURE');
    return payload;
  }

  async function storeOverrides(auth, productIds) {
    const ids = [...new Set(productIds.map(value => String(value)).filter(Boolean))];
    if (!ids.length || !auth.storeId) return new Map();
    const rows = await tableAll('store_product_price_overrides', {
      select: '*', store_id: `eq.${auth.storeId}`, chain_product_id: `in.${encodeIn(ids)}`, order: 'chain_product_id.asc'
    }, auth.token);
    return new Map((Array.isArray(rows) ? rows : []).map(row => [String(row.chain_product_id), row]));
  }

  async function campaignItems(auth, productCodes = null) {
    const scoped = Array.isArray(productCodes);
    if (!scoped && campaignsCache && Date.now() - campaignsAt < 60_000) return campaignsCache;
    const productKey = scoped ? [...new Set(productCodes.flatMap(value => typeof value === 'object'
      ? [value.code, value.barcode].filter(Boolean).map(String) : value == null ? [] : [String(value)]))].sort().join('|') : null;
    if (scoped && productKey && scopedCampaignCache.has(productKey)) {
      const cached = scopedCampaignCache.get(productKey);
      if (Date.now() - cached.at < 30_000) return cached.items;
      scopedCampaignCache.delete(productKey);
    }
    if (!scoped && campaignsLoading) return campaignsLoading;
    if (scoped && productKey && scopedCampaignLoads.has(productKey)) return scopedCampaignLoads.get(productKey);
    const loading = loadCampaignItems(auth, productCodes, scoped, productKey);
    if (!scoped) campaignsLoading = loading;
    else if (productKey) scopedCampaignLoads.set(productKey, loading);
    try { return await loading; }
    finally {
      if (!scoped && campaignsLoading === loading) campaignsLoading = null;
      if (scoped && productKey && scopedCampaignLoads.get(productKey) === loading) scopedCampaignLoads.delete(productKey);
    }
  }

  async function loadCampaignItems(auth, productCodes, scoped, productKey) {
    const campaignQuery = {
      select: 'id,name,code,start_date,end_date,active,deleted_at,updated_at,store_id,chain_id',
      active: 'eq.true',
      deleted_at: 'is.null',
      order: 'updated_at.desc,id.asc'
    };
    campaignQuery[auth.chainId ? 'or' : 'store_id'] = auth.chainId
      ? `(store_id.eq.${auth.storeId},and(store_id.is.null,chain_id.eq.${auth.chainId}))`
      : `eq.${auth.storeId}`;
    const campaigns = await tableAll('campaigns', campaignQuery, auth.token);
    const ids = (Array.isArray(campaigns) ? campaigns : []).map(c => c.id).filter(Boolean);
    if (!ids.length) {
      if (!scoped) { campaignsCache = []; campaignsAt = Date.now(); }
      else if (productKey) scopedCampaignCache.set(productKey, {items: [], at: Date.now()});
      return [];
    }
    const items = [];
    const codes = scoped ? [...new Set(productCodes.map(value => typeof value === 'object' ? value.code : value).filter(value => value != null && String(value) !== '').map(String))] : [];
    const barcodes = scoped ? [...new Set(productCodes.map(value => typeof value === 'object' ? value.barcode : null).filter(Boolean).map(String))] : [];
    if (scoped && !codes.length && !barcodes.length) return [];
    // Bound URL size and page through the upstream, rather than paginating an
    // already truncated in-memory array.
    for (let offset = 0; offset < ids.length; offset += 50) {
      const itemQuery = {select: '*', campaign_id: `in.${encodeIn(ids.slice(offset, offset + 50))}`, order: 'display_order.asc,id.asc'};
      if (scoped) {
        const filters = [];
        if (codes.length) filters.push(`code.in.${encodeIn(codes)}`);
        if (barcodes.length) filters.push(`barcode.in.${encodeIn(barcodes)}`);
        itemQuery.or = `(${filters.join(',')})`;
      }
      items.push(...await tableAll('campaign_items', itemQuery, auth.token));
      if (items.length > 10000) throw Error('ACPTECH_RESULT_TOO_LARGE');
    }
    const campaignById = new Map(campaigns.map(c => [String(c.id), c]));
    const mapped = (Array.isArray(items) ? items : []).map(item => {
      const campaign = campaignById.get(String(item.campaign_id));
      return ({
      ...normalizeCommercial(item),
      id: item.id ?? `${item.campaign_id}:${item.code ?? item.barcode ?? item.description}`,
      campaignId: item.campaign_id,
      campaignName: campaign?.name ?? null,
      campaignCode: campaign?.code ?? null,
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
    const productCandidates = scoped ? productCodes.filter(value => value && typeof value === 'object' && value.id != null) : [];
    const enriched = await enrichCampaignItems(auth, mapped, productCandidates);
    if (!scoped) { campaignsCache = enriched; campaignsAt = Date.now(); }
    else if (productKey) {
      scopedCampaignCache.set(productKey, {items: enriched, at: Date.now()});
      if (scopedCampaignCache.size > 200) scopedCampaignCache.delete(scopedCampaignCache.keys().next().value);
    }
    return enriched;
  }

  async function enrichCampaignItems(auth, items, productCandidates = []) {
    if (!items.length) return items;
    const catalog = [];
    const byId = new Set();
    for (const product of productCandidates) {
      const id = String(product.id ?? '');
      if (id && !byId.has(id)) { byId.add(id); catalog.push(product); }
    }
    const alternativeProductIdByBarcode = new Map();
    const codes = [...new Set(items.map(item => item.code).filter(value => value != null && String(value) !== '').map(String))];
    const barcodes = [...new Set(items.map(item => item.barcode ?? item.barCode).filter(Boolean).map(String))];
    const ids = [...new Set(items.map(item => item.chain_product_id ?? item.product_id).filter(Boolean).map(String))];
    const candidateCodes = new Set(catalog.map(product => String(product.code ?? '')).filter(Boolean));
    const candidateBarcodes = new Set(catalog.map(product => String(product.barcode ?? product.barCode ?? '')).filter(Boolean));
    const candidateIds = new Set(catalog.map(product => String(product.id ?? '')).filter(Boolean));
    const candidateCodeCounts = new Map();
    for (const product of catalog) {
      const code = String(product.code ?? '');
      if (code) candidateCodeCounts.set(code, (candidateCodeCounts.get(code) ?? 0) + 1);
    }
    const lookupCodes = codes.filter(code => !candidateCodes.has(code));
    const lookupBarcodes = barcodes.filter(barcode => !candidateBarcodes.has(barcode) &&
      !items.some(item => String(item.barcode ?? item.barCode ?? '') === barcode &&
        candidateCodeCounts.get(String(item.code ?? '')) === 1));
    const lookupIds = ids.filter(id => !candidateIds.has(id));
    // Campaigns are small compared with the full catalogue. Resolve only their
    // product identities, then apply the store-specific prices/conditions.
    // Search results already contain the matching catalogue rows, so reuse
    // those identities instead of querying the same RPC a second time.
    for (let offset = 0; offset < Math.max(lookupCodes.length, lookupBarcodes.length, lookupIds.length); offset += 50) {
      const pCodes = lookupCodes.slice(offset, offset + 50);
      const pBarcodes = lookupBarcodes.slice(offset, offset + 50);
      const pIds = lookupIds.slice(offset, offset + 50);
      const result = await rpcPage('search_chain_products', {
        p_chain_id: auth.chainId,
        p_category_id: null,
        p_code: null,
        p_barcode_prefix: null,
        p_description: null,
        p_ids: pIds.length ? pIds : null,
        p_codes: pCodes.length ? pCodes : null,
        p_barcodes: pBarcodes.length ? pBarcodes : null,
        p_include_deleted: false,
        p_limit: 250,
        p_offset: 0
      }, auth.token);
      for (const product of result.rows) {
        const id = String(product.id ?? '');
        if (id && !byId.has(id)) { byId.add(id); catalog.push(product); }
      }
    }
    // ACP campaign rows can carry a legacy EAN that is not the product's
    // primary barcode. Resolve only those campaign EANs through ACP's alias
    // table, then fetch the corresponding products by ID via its search RPC.
    if (lookupBarcodes.length) {
      const aliases = await tableAll('chain_product_barcodes', {
        select: 'chain_product_id,barcode', barcode: `in.${encodeIn(lookupBarcodes)}`,
        order: 'chain_product_id.asc,barcode.asc'
      }, auth.token);
      for (const alias of aliases) {
        if (alias?.barcode && alias?.chain_product_id) {
          alternativeProductIdByBarcode.set(String(alias.barcode), String(alias.chain_product_id));
        }
      }
      const alternativeIds = [...new Set(aliases.map(alias => alias?.chain_product_id).filter(Boolean).map(String))];
      for (let offset = 0; offset < alternativeIds.length; offset += 50) {
        const result = await rpcPage('search_chain_products', {
          p_chain_id: auth.chainId, p_category_id: null, p_code: null,
          p_barcode_prefix: null, p_description: null,
          p_ids: alternativeIds.slice(offset, offset + 50), p_codes: null, p_barcodes: null,
          p_include_deleted: false, p_limit: 250, p_offset: 0
        }, auth.token);
        for (const product of result.rows) {
          const id = String(product.id ?? '');
          if (id && !byId.has(id)) { byId.add(id); catalog.push(product); }
        }
      }
    }
    // ACP's multi-code campaign search may omit products that its single-code
    // lookup can resolve. Retry only unresolved numeric campaign codes; this
    // stays bounded to products in the campaigns being read.
    const catalogCodes = new Set(catalog.map(product => String(product.code ?? '')));
    const unresolvedCodes = codes.filter(code => /^\d+$/.test(code) && !catalogCodes.has(code)).slice(0, 50);
    for (const code of unresolvedCodes) {
      const result = await rpcPage('search_chain_products', {
        p_chain_id: auth.chainId, p_category_id: null, p_code: Number(code),
        p_barcode_prefix: null, p_description: null, p_ids: null, p_codes: null,
        p_barcodes: null, p_include_deleted: false, p_limit: 250, p_offset: 0
      }, auth.token);
      for (const product of result.rows) {
        const id = String(product.id ?? '');
        if (id && !byId.has(id)) { byId.add(id); catalog.push(product); }
      }
    }
    if (!catalog.length) return items;
    const storeProducts = await hydrateProducts(auth, catalog, false);
    const productFor = item => {
      const barcode = String(item.barcode ?? item.barCode ?? '');
      const productId = item.chain_product_id ?? item.product_id ?? alternativeProductIdByBarcode.get(barcode);
      if (productId != null) {
        const byId = storeProducts.find(product => String(product.id) === String(productId));
        if (byId) return byId;
      }
      const code = item.code == null ? '' : String(item.code);
      if (code) {
        const codeMatches = storeProducts.filter(product => String(product.code ?? '') === code);
        if (codeMatches.length === 1) return codeMatches[0];
        if (barcode) {
          const exact = codeMatches.find(product => String(product.barcode ?? product.barCode ?? '') === barcode);
          if (exact) return exact;
        }
        if (codeMatches.length > 1) return undefined;
      }
      if (barcode) return storeProducts.find(product => String(product.barcode ?? product.barCode ?? '') === barcode);
      return undefined;
    };
    return items.map(item => {
      const product = productFor(item);
      if (!product) return item;
      const merged = {...product, ...item};
      // ACP campaign rows can carry zero as a placeholder when the actual value
      // lives in store_product_price_overrides. Keep each positive campaign
      // value; otherwise fall back to the matched store product's value.
      for (const [raw, alias] of [['price', 'value'], ['previous_price', 'previousValue'],
        ['club_price', 'clubValue'], ['wholesale_price', 'wholesaleValue'],
        ['wholesale_quantity', 'wholesaleQuantity'], ['leve_quantity', 'quantityTake'],
        ['pague_quantity', 'quantityPay'], ['second_unit_discount', 'secondUnitDiscount'],
        ['cashback_percent', 'cashback'], ['cashback_value', 'cashbackValue'], ['unit_limit', 'unitLimitPerCPF']]) {
        const campaignValue = item[raw] ?? item[alias];
        const productValue = product[raw] ?? product[alias];
        const selected = Number(campaignValue) > 0 ? campaignValue : productValue ?? campaignValue ?? null;
        merged[raw] = selected;
        merged[alias] = selected;
      }
      // Campaign validity belongs to the campaign item, even when its price
      // and eligibility fields come from the store override.
      merged.startDate = item.startDate ?? item.start_date ?? null;
      merged.endDate = item.endDate ?? item.end_date ?? item.valid_until ?? null;
      merged.start_date = merged.startDate;
      merged.valid_until = merged.endDate;
      merged.productCategories = categoryObjects(merged);
      return merged;
    });
  }

  async function legacyCampaigns(auth) {
    // Search/detail need upcoming campaign rules too, so the app can show the
    // price and its start/end dates before the campaign begins. Promotions UI
    // still filters to offers valid today on the client.
    const items = (await promotions(auth)).filter(item => notExpired(normalizeCommercial(item), now()));
    const grouped = new Map();
    for (const item of items) {
      const id = item.campaignId ?? 'catalog-products';
      if (!grouped.has(id)) grouped.set(id, {
        id, code: item.campaignCode ?? null, name: item.campaignName ?? 'Condições do cadastro de produtos',
        startDate: item.campaignId ? item.startDate : null, endDate: item.campaignId ? item.endDate : null,
        active: true, products: []
      });
      grouped.get(id).products.push(item);
    }
    return [...grouped.values()];
  }

  async function products(auth, parameters, enrichCampaigns = true) {
    const page = Math.max(0, Number(parameters.find(([key]) => key === 'pageIndex')?.[1] ?? 0) || 0);
    const size = Math.min(250, Math.max(1, Number(parameters.find(([key]) => key === 'pageSize')?.[1] ?? 50) || 50));
    const query = {select: '*', chain_id: auth.chainId ? `eq.${auth.chainId}` : undefined, deleted_at: 'is.null', limit: String(size), offset: String(page * size), order: 'description.asc,code.asc'};
    if (!query.chain_id) delete query.chain_id;
    let code = null, barcode = null, description = null, categoryId = null;
    for (const [key, value] of parameters) {
      if (key === 'code') { query.code = `eq.${value}`; code = /^\d+$/.test(String(value)) ? Number(value) : null; }
      if (key === 'barCode') { query.barcode = `eq.${value}`; barcode = String(value); }
      if (key === 'description') { query.description = `ilike.*${String(value).replace(/[*%]/g, '')}*`; description = String(value).trim(); }
      if (key === 'productCategoryIds' && value) { query.category_id = `eq.${value}`; categoryId = String(value); }
    }
    const searched = code != null || barcode != null || Boolean(description);
    let result;
    if (searched || categoryId) {
      try {
        result = await rpcPage('search_chain_products', {
        p_chain_id: auth.chainId,
        p_category_id: categoryId,
        p_code: code,
        p_barcode_prefix: barcode,
        p_description: description,
        p_ids: null, p_codes: null, p_barcodes: null,
        p_include_deleted: false, p_limit: size, p_offset: page * size
        }, auth.token);
      } catch (error) {
        // Keep the catalogue searchable if the ACP Tech search RPC is briefly
        // unavailable. PostgREST supports the same exact and text filters.
        console.warn('ACPTECH_SEARCH_RPC_FALLBACK', JSON.stringify({code: error?.message === 'ACPTECH_UPSTREAM_FAILURE' ? 'UPSTREAM' : 'ERROR'}));
        result = await tablePage('chain_products', query, auth.token, 'exact');
      }
    } else {
      result = await tablePage('chain_products', query, auth.token);
    }
    if (!searched) {
      try {
        const counted = await rpcValue('count_chain_products', {
          p_chain_id: auth.chainId, p_category_id: categoryId,
          p_include_deleted: false
        }, auth.token);
        const total = Number(counted?.count ?? counted);
        if (Number.isFinite(total) && total >= 0) result = {...result, total};
      } catch { /* planned count remains a safe fallback */ }
    }
    const items = await hydrateProducts(auth, result.rows, enrichCampaigns);
    const totalCount = result.total ?? (page * size + items.length + (items.length === size ? size : 0));
    return {items, totalCount, pageIndex: page, pageSize: size};
  }

  async function hydrateProducts(auth, rows, enrichCampaigns = false) {
    const overrides = await storeOverrides(auth, rows.map(item => item.id).filter(Boolean));
    let campaignRows = [];
    if (enrichCampaigns) {
      try { campaignRows = await campaignItems(auth, rows); }
      catch (error) {
        // Campaign lookup supplements the product price. It must not hide the
        // ACP catalogue price when the campaigns endpoint is temporarily down.
        console.warn('ACPTECH_CAMPAIGN_ENRICHMENT_FAILURE', JSON.stringify({
          error: error?.message === 'ACPTECH_UPSTREAM_FAILURE' ? 'UPSTREAM' : 'ERROR', count: rows.length
        }));
      }
    }
    const campaignFor = item => (Array.isArray(campaignRows) ? campaignRows : []).filter(candidate =>
      (item.code != null && candidate.code != null && String(item.code) === String(candidate.code)) ||
      (item.barcode && candidate.barcode && String(item.barcode) === String(candidate.barcode))
    );
    return (Array.isArray(rows) ? rows : []).map(item => {
      const campaigns = campaignFor(item);
      const override = overrides.get(String(item.id));
      const base = {...item};
      for (const key of ['price', 'previous_price', 'wholesale_price', 'wholesale_quantity', 'club_price', 'valid_until', 'installments', 'promotion_type', 'leve_quantity', 'pague_quantity', 'cashback_percent', 'cashback_value', 'stock', 'unit_limit']) {
        if (override?.[key] !== undefined && override?.[key] !== null) base[key] = override[key];
      }
      const merged = mergeCommercial(base, campaigns, now());
      return {
        ...merged,
        barCode: merged.barcode ?? merged.barCode ?? null,
        stockQuantity: merged.stock ?? merged.stockQuantity ?? null,
        unit: typeof merged.unit === 'string' ? {description: merged.unit} : merged.unit,
        packageType: typeof merged.package_type === 'string' ? {description: merged.package_type} : merged.packageType,
        packageQuantity: merged.package_quantity ?? merged.packageQuantity ?? null,
        contentQuantity: merged.content_quantity ?? merged.contentQuantity ?? null,
        contentUnit: merged.content_unit ?? merged.contentUnit ?? null,
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
        startDate: merged.startDate ?? merged.start_date ?? null,
        endDate: merged.endDate ?? merged.valid_until ?? null,
        imagem: merged.image_url ?? null,
        imageUrl: merged.image_url ?? null, linkloja: null, productUrl: null
      };
    });
  }

  async function categories(auth) {
    const items = await tableAll('product_categories', {select: '*', order: 'name.asc,id.asc'}, auth.token);
    return items.map(item => ({...item, description: item.description ?? item.name}));
  }

  async function promotions(auth) {
    if (promotionsCache && now() - promotionsAt < 60_000) return promotionsCache;
    const all = new Map((await campaignItems(auth)).map(item => [String(item.id), item]));
    // The legacy feed used these product categories. Campaign items alone do not
    // include catalogue-only Clube/Leve-Pague offers in ACP Tech.
    const promotionalCategories = (await categories(auth)).filter(category =>
      ['depor', 'clubedevantagens', 'clubvantagens', 'levepague', 'leveepague'].includes(
        String(category.description ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '')));
    for (const category of promotionalCategories) {
      for (let page = 0; page < 100; page++) {
        const result = await products(auth, [['productCategoryIds', String(category.id)], ['pageIndex', String(page)], ['pageSize', '250']], false);
        for (const item of result.items) {
          if (item.id == null) throw Error('ACPTECH_INVALID_PRODUCT');
          all.set(String(item.id), {...item, productCategories: categoryObjects(item)});
        }
        if (all.size > 10000) throw Error('ACPTECH_RESULT_TOO_LARGE');
        if (!result.items.length || (page + 1) * 250 >= result.totalCount) break;
        if (page === 99) throw Error('ACPTECH_INCOMPLETE_RESULT');
      }
    }
    promotionsCache = [...all.values()]; promotionsAt = now();
    return promotionsCache;
  }

  return {
    async consult(input) {
      for (let attempt = 0; attempt < 2; attempt++) {
        try {
          const auth = await signIn();
          const rows = input.path === 'Promotion/all'
            ? await promotions(auth)
            : input.path === 'Product/all' ? await products(auth, input.parameters)
            : input.path === 'ProductCategory/all' ? await categories(auth)
            : input.path === 'Campaign/all' ? await legacyCampaigns(auth)
            : [];
          if (input.path === 'Product/all' && rows && !Array.isArray(rows)) {
            const totalPages = rows.totalCount ? Math.ceil(rows.totalCount / rows.pageSize) : 0;
            return {items: rows.items, pageIndex: rows.pageIndex, totalCount: rows.totalCount, totalPages};
          }
          const page = Number(input.parameters.find(([key]) => key === 'pageIndex')?.[1] ?? 0);
          const size = Math.min(Number(input.parameters.find(([key]) => key === 'pageSize')?.[1] ?? 250), 250);
          return {items: rows.slice(page * size, (page + 1) * size), pageIndex: page,
            totalCount: rows.length, totalPages: rows.length ? Math.ceil(rows.length / size) : 0};
        } catch (error) {
          console.warn('ACPTECH_CONSULT_FAILURE', JSON.stringify({path: input.path,
            error: /^ACPTECH_[A-Z_]+$/.test(String(error?.message)) ? error.message : String(error?.name ?? 'Error')}));
          if (attempt === 0 && /LOGIN|SESSION|UPSTREAM|STORE_NOT_FOUND|CHAIN_NOT_FOUND/.test(String(error?.message))) {
            session = null; campaignsCache = null; promotionsCache = null; continue;
          }
          throw error;
        }
      }
      throw Error('ACPTECH_UPSTREAM_FAILURE');
    }
  };
}
