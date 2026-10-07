// Fixed read-only routes; credentials, upstream tokens and cookies never leave the server.
export const TENANT = 'https://nordestao12.acp.app.br';
const API = 'https://api.acp.app.br';
const routes = new Set(['Promotion/all', 'Product/all', 'ProductCategory/all', 'Product/integrationInfo', 'Campaign/all', 'TemplatePrintLog/all', 'ProductGroup/all']);
const masterRoutes = new Set(['TemplatePrintLog/all', 'ProductGroup/all']);
const keys = new Set(['pageSize', 'pageIndex', 'code', 'barCode', 'description', 'productCategoryIds', 'orderByDescending', 'profileIdToBeDesconsidered']);
const headers = {'Content-Type':'application/json', 'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff'};
const reply = (status, body) => new Response(JSON.stringify(body), {status, headers});
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
  if (['access','health'].includes(input.operation)) return Object.keys(input).length === 1;
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
export function createHandler({authorize, credentials, fetcher = fetch}) {
  // ACP session shared only inside this server instance. Login is serialized, not per product.
  let session = null, sessionAt = 0, signingIn = null;
  let dePorCategory = null, dePorCategoryAt = 0;
  async function signIn() {
    if (session && Date.now() - sessionAt < 10 * 60_000) return session;
    if (signingIn) return signingIn;
    signingIn = (async () => {
      const secret = credentials();
      if (!secret.login || !secret.password) throw Error('NOT_READY');
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
      sessionAt = Date.now(); return session;
    })();
    try { return await signingIn; } finally { signingIn = null; }
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
    try { input = await boundedJson(req,4096); } catch { return reply(400,{error:'INVALID_REQUEST'}); }
    if (!validate(input)) return reply(400,{error:'INVALID_REQUEST'});
    if (identity.promotionsOnly && input.operation !== 'access' && input.path !== 'Promotion/all') return reply(403,{error:'ACCESS_DENIED'});
    // CI may exercise exactly one fixed read to validate migration readiness, never arbitrary queries.
    if (identity.probe && input.operation !== 'health') return reply(403,{error:'ACCESS_DENIED'});
    if (input.operation === 'health' && !identity.probe) return reply(403,{error:'ACCESS_DENIED'});
    if (masterRoutes.has(input.path) && !identity.master) return reply(403,{error:'ACCESS_DENIED'});
    const secret = credentials();
    if (!secret.login || !secret.password) return reply(503,{error:'SERVICE_NOT_READY'});
    if (input.operation === 'access') return reply(200,{ok:true});
    const health = input.operation === 'health';
    if (health) input = {path:'ProductCategory/all',parameters:[['pageSize','1'],['pageIndex','0']]};
    try {
      for (let attempt = 0; attempt < 2; attempt++) {
        const auth = await signIn();
        const base = auth.proxy ? TENANT+'/api/proxy/api/v1/' : API+'/api/v1/';
        let path = input.path;
        let parameters = input.parameters;
        if (path === 'Promotion/all') {
          if (!dePorCategory || Date.now() - dePorCategoryAt > 60_000) {
            let found = null;
            for (let page = 0; page < 20; page++) {
              const categoryUrl = new URL(base + 'ProductCategory/all');
              categoryUrl.searchParams.set('pageSize','250'); categoryUrl.searchParams.set('pageIndex',String(page));
              const categoryResponse = await fetcher(categoryUrl,{redirect:'error',signal:AbortSignal.timeout(25000),
                headers:{Accept:'application/json','Cache-Control':'no-cache',Authorization:'Bearer '+auth.token,...(auth.proxy ? {Cookie:auth.cookie} : {})}});
              if (!categoryResponse.ok) throw Error('UPSTREAM_FAILURE');
              const categoryPayload = await boundedJson(categoryResponse);
              const items = Array.isArray(categoryPayload) ? categoryPayload : categoryPayload.items;
              if (!Array.isArray(items)) throw Error('INVALID_PAYLOAD');
              const category = items.find(item => String(item.description).toLowerCase().replace(/[^a-z0-9]/g,'') === 'depor');
              if (category) { found = String(category.id); break; }
              if (page + 1 >= (categoryPayload.totalPages ?? 1)) break;
            }
            if (!found) throw Error('DEPOR_UNAVAILABLE');
            dePorCategory = found; dePorCategoryAt = Date.now();
          }
          path = 'Product/all';
          parameters = [...input.parameters,['productCategoryIds',dePorCategory]];
        }
        const url = new URL(base + path);
        for (const [key,value] of parameters) url.searchParams.append(key,value);
        const response = await fetcher(url,{redirect:'error',signal:AbortSignal.timeout(25000),
          headers:{Accept:'application/json','Cache-Control':'no-cache',Authorization:'Bearer '+auth.token,...(auth.proxy ? {Cookie:auth.cookie} : {})}});
        if (response.status === 401 && attempt === 0) { session = null; continue; }
        if (response.status === 429) return reply(429,{error:'RATE_LIMITED'});
        if (!response.ok) throw Error('UPSTREAM_FAILURE');
        const raw = await boundedJson(response);
        const payload = Array.isArray(raw) ? {items:raw,pageIndex:0,totalPages:1,totalCount:raw.length} : raw;
        if (!payload || typeof payload !== 'object') throw Error('INVALID_PAYLOAD');
        if (health) {
          if (!Array.isArray(payload.items) || payload.items.length === 0) throw Error('INVALID_PAYLOAD');
          return reply(200,{ok:true});
        }
        return reply(200,clean(payload));
      }
    } catch { /* Deliberately do not log credentials, tokens, cookies or upstream bodies. */ }
    return reply(502,{error:'CONSULTATION_UNAVAILABLE'});
  };
}
