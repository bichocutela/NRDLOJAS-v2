import { validSync } from './promotion-sync.mjs';
import { resolvePromotions } from '../nordestao-catalog-resolver/handler.mjs';
import { providerOrder } from './provider-routing.mjs';
// Fixed read-only routes; credentials, upstream tokens and cookies never leave the server.
export const TENANT = 'https://nordestao12.acp.app.br';
const API = 'https://api.acp.app.br';
const routes = new Set(['Promotion/all', 'Product/all', 'ProductCategory/all', 'Product/integrationInfo', 'Campaign/all', 'TemplatePrintLog/all', 'ProductGroup/all']);
const masterRoutes = new Set(['TemplatePrintLog/all', 'ProductGroup/all']);
const keys = new Set(['pageSize', 'pageIndex', 'code', 'barCode', 'description', 'productCategoryIds', 'orderByDescending', 'profileIdToBeDesconsidered']);
const headers = {'Content-Type':'application/json', 'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff'};
const reply = (status, body) => new Response(JSON.stringify(body), {status, headers});
async function enrichCatalog(payload, catalogLoader, minScore, catalogLookup, delivery = null) {
  if (delivery) {
    // Product prices are already available. Optional image resolution must never hold them
    // behind the storefront's multi-product network lookup (up to 12 seconds on a cold miss).
    const work = enrichCatalog(payload, catalogLoader, minScore, catalogLookup).catch(() => payload);
    delivery.background(work);
    let timer;
    try {
      return await Promise.race([work, new Promise(resolve => {
        timer = setTimeout(() => resolve(payload), delivery.budgetMs);
      })]);
    } finally { clearTimeout(timer); }
  }
  if ((!catalogLoader && !catalogLookup) || !Array.isArray(payload?.items) || payload.items.length === 0) return payload;
  let catalog;
  try { catalog = catalogLoader ? await catalogLoader() : await catalogLookup(payload.items); } catch { return payload; }
  if (!Array.isArray(catalog) || catalog.length === 0) return payload;
  const resolved = resolvePromotions(payload.items, catalog, {minScore});
  return {...payload, items: resolved.map(({promotion}) => ({
    ...promotion,
    productUrl: promotion.linkloja ?? promotion.productUrl ?? null,
    imageUrl: promotion.imagem ?? promotion.imageUrl ?? null
  }))};
}
export async function boundedJson(response, limit = 8_000_000) {
  const reader = response.body?.getReader();
  if (!reader) throw Error('EMPTY_BODY');
  const chunks = []; let length = 0;
  for (;;) {
    const {done, value} = await reader.read(); if (done) break;
    length += value.length;
    if (length > limit) { await reader.cancel(); throw Error('BODY_TOO_LARGE'); }
    chunks.push(value);
  }
  const result = new Uint8Array(length); let offset = 0;
  for (const chunk of chunks) { result.set(chunk, offset); offset += chunk.length; }
  return JSON.parse(new TextDecoder().decode(result));
}
// Preserve the existing commercial contract, but remove authentication and staff data recursively,
// including JSON nested in historical dataLog strings. Never return raw upstream error bodies.
export function clean(value, depth = 0) {
  if (depth > 30) throw Error('INVALID_DEPTH');
  if (Array.isArray(value)) return value.map(x => clean(x, depth + 1));
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value)
    .filter(([key]) => !/password|passwd|token|secret|cookie|authorization|credential|login|email|^user$|^userid$|^cpf$|^access$|^session$|^headers$/i.test(key))
    .map(([key, item]) => [key, clean(item, depth + 1)]));
  if (typeof value === 'string' && /^[\s]*[\[{]/.test(value)) {
    let parsed; try { parsed = JSON.parse(value); } catch { return value; }
    return JSON.stringify(clean(parsed, depth + 1));
  }
  return value;
}
export function validate(input) {
  if (!input || Array.isArray(input) || typeof input !== 'object') return false;
  if (validSync(input)) return true;
  if (['access','provider_status','health','health_promotions','health_sync','health_categories','health_catalog','health_search','promotion_tick'].includes(input.operation)) return Object.keys(input).length === 1;
  if (Object.keys(input).some(k => !['path','parameters'].includes(k)) || !routes.has(input.path) || !Array.isArray(input.parameters) || input.parameters.length > 24) return false;
  if (input.path === "Promotion/all" && input.parameters.some(pair => !["pageSize","pageIndex"].includes(pair?.[0]))) return false;
  const seen = new Set();
  return input.parameters.every(pair => {
    if (!Array.isArray(pair) || pair.length !== 2) return false;
    const [key,value] = pair;
    if (!keys.has(key) || typeof value !== 'string' || value.length > 240 || /[\x00-\x1f]/.test(value)) return false;
    if (seen.has(key) && key !== 'productCategoryIds') return false;
    seen.add(key);
    if (key === 'pageSize') return /^\d+$/.test(value) && +value >= 1 && +value <= 250;
    if (key === 'pageIndex') return /^\d+$/.test(value) && +value <= 3000;
    if (key === 'orderByDescending') return ['true','false'].includes(value);
    if (key === 'profileIdToBeDesconsidered') return input.path === 'TemplatePrintLog/all' && value === '1';
    return true;
  });
}
export function createHandler({authorize, credentials, fetcher = fetch, enrichPromotions = async items => items,
  promotionSync = null, catalogLoader = null, catalogLookup = null, catalogMinScore = 0.86, catalogCacheTtlMs = 15 * 60_000,
  background = work => { void work.catch(() => {}); }, catalogSearchBudgetMs = 75,
  provider = 'legacy', providerSettings = async () => ({}), acpTechClient = null}) {
  const catalogDelivery = {background, budgetMs:catalogSearchBudgetMs};
  // ACP session shared only inside this server instance. Login is serialized, not per product.
  let session = null, sessionAt = 0, signingIn = null, sessionRevision;
  let dePorCategory = null, dePorCategoryAt = 0;
  let catalogCache = null, catalogCachedAt = 0, catalogLoading = null;
  const loadCatalog = catalogLoader ? async () => {
    if (catalogCache && Date.now() - catalogCachedAt < catalogCacheTtlMs) return catalogCache;
    if (catalogLoading) return catalogLoading;
    catalogLoading = Promise.resolve(catalogLoader()).then(value => {
      if (!Array.isArray(value)) throw Error('INVALID_CATALOG');
      catalogCache = value; catalogCachedAt = Date.now(); return value;
    }).finally(() => { catalogLoading = null; });
    return catalogLoading;
  } : null;
  async function signIn() {
    const secret = await credentials('legacy');
    if (!secret?.login || !secret?.password) throw Error('NOT_READY');
    const revision = secret.revision ?? 0;
    if (session && sessionRevision === revision && Date.now() - sessionAt < 10 * 60_000) return session;
    if (signingIn) return signingIn;
    signingIn = (async () => {
      const jar = new Map();
      const json = async (path, options = {}) => {
        const response = await fetcher(TENANT + path, {...options, redirect:'error', signal:AbortSignal.timeout(15000),
          headers:{Accept:'application/json', ...options.headers, ...(jar.size ? {Cookie:[...jar].map(([k,v]) => k+'='+v).join('; ')} : {})}});
        for (const cookie of response.headers.getSetCookie()) {
          const pair = cookie.split(';')[0], at = pair.indexOf('=');
          if (at > 0) jar.set(pair.slice(0,at),pair.slice(at+1));
        }
        if (!response.ok) throw Error('UPSTREAM_FAILURE');
        return boundedJson(response);
      };
      const csrf = await json('/api/auth/csrf');
      if (typeof csrf.csrfToken !== 'string' || !csrf.csrfToken) throw Error('UPSTREAM_FAILURE');
      const callback = await json('/api/auth/callback/credentials', {method:'POST',
        headers:{'Content-Type':'application/x-www-form-urlencoded',Origin:TENANT,Referer:TENANT+'/login'},
        body:new URLSearchParams({login:secret.login,password:secret.password,csrfToken:csrf.csrfToken,callbackUrl:TENANT+'/print-template',json:'true'})});
      if (callback.error || (typeof callback.url === 'string' && new URL(callback.url,TENANT).searchParams.has('error'))) throw Error('UPSTREAM_FAILURE');
      const result = await json('/api/auth/session');
      if (typeof result.user?.accessToken !== 'string' || !result.user.accessToken) throw Error('UPSTREAM_FAILURE');
      session = {token:result.user.accessToken, proxy:result.user.proxyEnable === true, cookie:[...jar].map(([k,v]) => k+'='+v).join('; ')};
      sessionAt = Date.now(); sessionRevision = revision; return session;
    })();
    try { return await signingIn; } finally { signingIn = null; }
  }
  async function enrichItems(items) {
    items = (await enrichCatalog({items}, loadCatalog, catalogMinScore, catalogLookup)).items;
    try { return await enrichPromotions(items); } catch { return items; }
  }
  async function consultProvider(input, enrich, family, source) {
      if (source === 'acptech') {
        if (!acpTechClient) throw Error('NOT_READY');
        const payload = await acpTechClient.consult(input);
        const sanitized = clean(payload);
        if (!enrich) return sanitized;
        if (Array.isArray(sanitized.items)) {
          try { sanitized.items = await enrichPromotions(sanitized.items); } catch { /* enrichment is optional */ }
        }
        return ['Product/all','Promotion/all'].includes(input.path)
          ? enrichCatalog(sanitized, loadCatalog, catalogMinScore, catalogLookup, input.path === 'Product/all' ? catalogDelivery : null) : sanitized;
      }
      attempts: for (let attempt = 0; attempt < 2; attempt++) {
        const auth = await signIn();
        const base = auth.proxy ? TENANT+'/api/proxy/api/v1/' : API+'/api/v1/';
        let path = input.path;
        let parameters = input.parameters;
        if (path === 'Promotion/all') {
          if (!dePorCategory || Date.now() - dePorCategoryAt > 60_000) {
            const found = [];
            for (let page = 0; page < 20; page++) {
              const categoryUrl = new URL(base + 'ProductCategory/all');
              categoryUrl.searchParams.set('pageSize','250'); categoryUrl.searchParams.set('pageIndex',String(page));
              const categoryResponse = await fetcher(categoryUrl,{redirect:'error',signal:AbortSignal.timeout(25000),
                headers:{Accept:'application/json','Cache-Control':'no-cache',Authorization:'Bearer '+auth.token,...(auth.proxy ? {Cookie:auth.cookie} : {})}});
              if (categoryResponse.status === 401 && attempt === 0) { session = null; continue attempts; }
              if (!categoryResponse.ok) throw Error('UPSTREAM_FAILURE');
              const categoryPayload = await boundedJson(categoryResponse);
              const items = Array.isArray(categoryPayload) ? categoryPayload : categoryPayload.items;
              if (!Array.isArray(items)) throw Error('INVALID_PAYLOAD');
              for (const item of items) {
                const label=String(item.description).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]/g,'');
                if (['depor','clubedevantagens','clubvantagens','levepague','leveepague'].includes(label)) found.push({id:String(item.id),label});
              }
              if (page + 1 >= (categoryPayload.totalPages ?? 1)) break;
            }
            if (!found.length) throw Error('DEPOR_UNAVAILABLE');
            dePorCategory = [...new Map(found.map(item=>[item.id,item])).values()]; dePorCategoryAt = Date.now();
          }
          path = 'Product/all';
          const names=family==='club'?['clubedevantagens','clubvantagens']:family==='takepay'?['levepague','leveepague']:['depor'];
          const category=dePorCategory.find(item=>names.includes(item.label));
          if(!category) { if(family==='depor') throw Error('DEPOR_UNAVAILABLE'); return {items:[],pageIndex:Number(input.parameters.find(p=>p[0]==='pageIndex')?.[1] ?? 0),totalCount:0,totalPages:0}; }
          parameters = [...input.parameters,['productCategoryIds',category.id]];
        }
        const url = new URL(base + path);
        for (const [key,value] of parameters) url.searchParams.append(key,value);
        const response = await fetcher(url,{redirect:'error',signal:AbortSignal.timeout(25000),
          headers:{Accept:'application/json','Cache-Control':'no-cache',Authorization:'Bearer '+auth.token,...(auth.proxy ? {Cookie:auth.cookie} : {})}});
        if (response.status === 401 && attempt === 0) { session = null; continue; }
        if (response.status === 429) throw Error('RATE_LIMITED');
        if (!response.ok) throw Error('UPSTREAM_FAILURE');
        const raw = await boundedJson(response);
        const payload = Array.isArray(raw) ? {items:raw,pageIndex:0,totalPages:1,totalCount:raw.length} : raw;
        if (!payload || typeof payload !== 'object') throw Error('INVALID_PAYLOAD');
        const sanitized = clean(payload);
        if (!enrich) return sanitized;
        if (input.path === 'Promotion/all' && Array.isArray(sanitized.items)) {
          // Classification errors never prevent commercial consultations.
          try { sanitized.items = await enrichPromotions(sanitized.items); } catch { /* fallback on client */ }
        }
        return ['Product/all','Promotion/all'].includes(input.path)
          ? enrichCatalog(sanitized, loadCatalog, catalogMinScore, catalogLookup, input.path === 'Product/all' ? catalogDelivery : null) : sanitized;
      }
    throw Error('UPSTREAM_FAILURE');
  }
  async function enabledProviderOrder() {
    let settings = null;
    try { settings = await providerSettings(); } catch { /* Keep the environment default during a control-store outage. */ }
    const result = [];
    for (const source of providerOrder(settings, provider)) {
      try {
        const secret = await credentials(source);
        if (secret?.login && secret?.password && (source !== 'acptech' || acpTechClient)) result.push(source);
      } catch { /* Do not guess old credentials during a credential-store outage. */ }
    }
    return result;
  }
  async function consult(input, enrich = true, family = "depor") {
    const order = await enabledProviderOrder();
    if (!order.length) throw Error('NOT_READY');
    let lastError;
    for (let index = 0; index < order.length; index++) {
      const source = order[index];
      try {
        const result = await consultProvider(input, enrich, family, source);
        return ['Product/all','Campaign/all'].includes(input.path) ? {...result,nrdProvider:source} : result;
      }
      catch (error) {
        lastError = error;
        if (index + 1 >= order.length) break;
        const code = /^[A-Z0-9_]+$/.test(String(error?.message ?? '')) ? error.message : 'UPSTREAM_FAILURE';
        console.warn('ACP_PROVIDER_FALLBACK', JSON.stringify({from:source,to:order[index + 1],error:code}));
      }
    }
    throw lastError ?? Error('UPSTREAM_FAILURE');
  }
  function snapshotReader() {
    let loading;
    return async page => {
      if (!loading) loading = (async()=>{
        const order=await enabledProviderOrder();
        if(!order.length)throw Error('NOT_READY');
        let lastError;
        for(let sourceIndex=0;sourceIndex<order.length;sourceIndex++) {
          const source=order[sourceIndex];
          try {
            const products=new Map();
            for(const family of ['depor','takepay','club']) {
              let expected=null,count=0;
              for(let index=0;index<100;index++) {
                const result=await consultProvider({path:'Promotion/all',parameters:[['pageIndex',String(index)],['pageSize','250']]},false,family,source);
                if(!Array.isArray(result.items) || result.pageIndex!==index || !Number.isInteger(result.totalCount) || result.totalCount<0 || result.totalCount>10000 || !Number.isInteger(result.totalPages) || result.totalPages<0 || result.totalPages>100)throw Error('INVALID_SNAPSHOT');
                if(expected===null)expected=result.totalCount;
                if(expected!==result.totalCount)throw Error('UNSTABLE_SNAPSHOT');
                count+=result.items.length;
                for(const item of result.items) {
                  const id=String(item.id ?? `${item.code}|${item.barCode}|${item.description}`);
                  if(!products.has(id))products.set(id,item);
                }
                if(index+1>=result.totalPages)break;
                if(!result.items.length)throw Error('INCOMPLETE_SNAPSHOT');
              }
              if(count!==expected)throw Error('INCOMPLETE_SNAPSHOT');
              console.info('PROMOTION_FAMILY_COUNT',JSON.stringify({family,count,source}));
            }
            if(products.size>10000)throw Error('INVALID_SNAPSHOT');
            return [...products.values()];
          } catch(error) {
            lastError=error;
            if(sourceIndex+1>=order.length)break;
            const code=/^[A-Z0-9_]+$/.test(String(error?.message ?? ''))?error.message:'UPSTREAM_FAILURE';
            console.warn('ACP_PROVIDER_SNAPSHOT_FALLBACK',JSON.stringify({from:source,to:order[sourceIndex+1],error:code}));
          }
        }
        throw lastError ?? Error('UPSTREAM_FAILURE');
      })();
      const items=await loading;
      return {items:items.slice(page*250,(page+1)*250),pageIndex:page,totalCount:items.length,totalPages:Math.ceil(items.length/250)};
    };
  }
  return async req => {
    if (req.method !== 'POST') return reply(405,{error:'METHOD_NOT_ALLOWED'});
    let identity;
    try { identity = await authorize(req); } catch (error) {
      if (error?.message === 'PERMISSIONS_RATE_LIMITED') return reply(429,{error:'PERMISSIONS_RATE_LIMITED'});
      if (['INVALID_FIREBASE_PROJECT','FIREBASE_UNAVAILABLE','PERMISSIONS_UNAVAILABLE'].includes(error?.message)) return reply(503,{error:'PERMISSIONS_UNAVAILABLE'});
      return reply(401,{error:'AUTH_REQUIRED'});
    }
    if (!identity?.allowed) return reply(403,{error:'ACCESS_DENIED'});
    let input;
    try { input = await boundedJson(req,2_000_000); } catch { return reply(400,{error:'INVALID_REQUEST'}); }
    if (!validate(input)) return reply(400,{error:'INVALID_REQUEST'});
    if (identity.promotionsOnly && !['access','promotion_status','promotion_sync','promotion_refresh'].includes(input.operation) && input.path !== 'Promotion/all') return reply(403,{error:'ACCESS_DENIED'});
    if (identity.scheduler && input.operation !== 'promotion_tick') return reply(403,{error:'ACCESS_DENIED'});
    if (input.operation === 'promotion_tick' && !identity.scheduler) return reply(403,{error:'ACCESS_DENIED'});
    // CI may exercise exactly one fixed read to validate migration readiness, never arbitrary queries.
    if (identity.probe && !['health','health_promotions','health_sync','health_categories','health_catalog','health_search'].includes(input.operation)) return reply(403,{error:'ACCESS_DENIED'});
    if (['health','health_promotions','health_sync','health_categories','health_catalog','health_search'].includes(input.operation) && !identity.probe) return reply(403,{error:'ACCESS_DENIED'});
    if (masterRoutes.has(input.path) && !identity.master) return reply(403,{error:'ACCESS_DENIED'});
    if (input.operation === 'provider_status') {
      if (!identity.master) return reply(403,{error:'ACCESS_DENIED'});
      let settings = null;
      try { settings = await providerSettings(); } catch { /* Use the deployment default if the control store is unavailable. */ }
      const legacyEnabled = typeof settings?.legacyEnabled === 'boolean' ? settings.legacyEnabled : provider === 'legacy';
      const acpTechEnabled = typeof settings?.acpTechEnabled === 'boolean' ? settings.acpTechEnabled : provider === 'acptech';
      const selected = settings?.primary === 'legacy' || settings?.primary === 'acptech' ? settings.primary : provider;
      const primary = selected === 'legacy' && legacyEnabled || selected === 'acptech' && acpTechEnabled
        ? selected : acpTechEnabled ? 'acptech' : 'legacy';
      return reply(200,{legacyEnabled,acpTechEnabled,primary});
    }
    if (!(await enabledProviderOrder()).length) return reply(503,{error:'SERVICE_NOT_READY'});
    if (input.operation === 'access') return reply(200,{ok:true});
    if (input.operation === 'health_search') {
      // CI-only, fixed read-only fixture. No arbitrary queries or product details are exposed.
      try {
        const search={path:'Product/all',parameters:[['description','Cereja'],['pageSize','20'],['pageIndex','0']]};
        const start=performance.now();
        const raw=await consult(search,false);
        const upstreamMs=Math.round(performance.now()-start);
        const appStart=performance.now();
        const result=await consult(search);
        const gatewayMs=Math.round(performance.now()-appStart);
        if(!raw.items?.length || !result.items?.length)throw Error('EMPTY_SEARCH');
        return reply(200,{ok:true,upstreamMs,gatewayMs,count:result.items.length,provider:result.nrdProvider});
      } catch {return reply(503,{error:'SEARCH_NOT_READY'});}
    }
    if (input.operation === 'health_catalog') {
      // Fixed read-only CI fixture; never accepts caller-selected products or returns commercial data.
      try {
        const raw = await consult({path:'Product/all',parameters:[['code','2021000'],['pageSize','10'],['pageIndex','0']]}, false);
        const page = await enrichCatalog(raw, loadCatalog, catalogMinScore, catalogLookup);
        const product = page.items?.find(item => String(item.code ?? item.codproduto ?? item.internalCode) === '2021000');
        if (!product?.imageUrl || product.catalog_match_type !== 'internal_code' || product.catalog_department !== 'Bebidas alcoólicas') throw Error('CATALOG_NOT_READY');
        const image = await fetcher(product.imageUrl,{method:'HEAD',signal:AbortSignal.timeout(10000)});
        if (!image.ok || !image.headers.get('content-type')?.startsWith('image/')) throw Error('CATALOG_NOT_READY');
        return reply(200,{ok:true});
      } catch { return reply(503,{error:'CATALOG_NOT_READY'}); }
    }
    if (input.operation === 'health_categories') {
      try {
        const page = await consult({path:'Promotion/all',parameters:[['pageIndex','0'],['pageSize','1']]});
        if (!enrichPromotions.verify || !await enrichPromotions.verify(page.items)) throw Error('CLASSIFICATION_NOT_READY');
        return reply(200,{ok:true});
      } catch(error) {
        const code = error?.message;
        if (code === 'CACHE_RATE_LIMITED') return reply(429,{error:'SYNC_RATE_LIMITED'});
        return reply(503,{error:/^(GEMINI_NOT_CONFIGURED|GEMINI_HTTP_[0-9]{3}|CLASSIFICATION_NOT_READY|INVALID_CATEGORIES)$/.test(code) ? code : 'CLASSIFICATION_NOT_READY'});
      }
    }
    if (input.operation === 'health_sync') {
      try {
        const readPage = snapshotReader();
        const status = await promotionSync({input:{operation:'promotion_sync',revision:'',manifest:[]},readPage,enrichItems});
        if (!/^[a-f0-9]{64}$/.test(status.revision) || status.items.length !== status.count || status.removedIds.length) throw Error('INVALID_SNAPSHOT');
        const manifest = status.items.map(({id,hash})=>({id,hash}));
        const next = await promotionSync({input:{operation:'promotion_sync',revision:status.revision,manifest},readPage,enrichItems});
        if (next.revision === status.revision && (next.items.length || next.removedIds.length)) throw Error('INVALID_DELTA');
        return reply(200,{ok:true});
      } catch(error) {
        if (error?.message === 'CACHE_RATE_LIMITED') return reply(429,{error:'SYNC_RATE_LIMITED'});
        return reply(502,{error:'SYNC_UNAVAILABLE'});
      }
    }
    const health = ['health','health_promotions'].includes(input.operation);
    if (health) input = {path:input.operation === 'health_promotions' ? 'Promotion/all' : 'ProductCategory/all',parameters:[['pageSize','1'],['pageIndex','0']]};
    try {
      if (input.operation === 'promotion_tick') {
        await promotionSync({input:{operation:'promotion_status'},enrichItems,readPage:snapshotReader()});
        return reply(202,{ok:true});
      }
      if (['promotion_status','promotion_sync','promotion_refresh'].includes(input.operation)) {
        if (!promotionSync) return reply(503,{error:'SYNC_UNAVAILABLE'});
        const result = await promotionSync({input,enrichItems, readPage: snapshotReader()});
        return reply(200,result);
      }
      const payload = await consult(input);
      if (health) {
        if (!Array.isArray(payload.items) || payload.items.length === 0) throw Error('INVALID_PAYLOAD');
        return reply(200,{ok:true});
      }
      return reply(200,payload);
    } catch (error) {
      // Record only a controlled code; never tokens, credentials or upstream bodies.
      if (error?.message === 'CACHE_RATE_LIMITED') return reply(429,{error:'SYNC_RATE_LIMITED'});
      if (error?.message === 'REFRESH_BUSY') return reply(503,{error:'SYNC_BUSY'});
      if (error?.message === 'CACHE_UNAVAILABLE') console.warn('PROMOTION_SYNC_FAILURE','CACHE_UNAVAILABLE');
    }
    return reply(502,{error:'CONSULTATION_UNAVAILABLE'});
  };
}

