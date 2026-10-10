const BASE = 'https://services.vipcommerce.com.br/api-admin/v1';
const DOMAIN = 'nordestaomaisvoce.com.br';
const PUBLIC_KEY = 'df072f85df9bf7dd71b6811c34bdbaa4f219d98775b56cff9dfa5f8ca1bf8469';
const IMAGE_BASE = 'https://produto-assets-vipcommerce-com-br.br-se1.magaluobjects.com';
const first = (...values) => values.map(value => String(value ?? '').trim()).find(Boolean) ?? '';
export function catalogIdentity(item) {
  return {code:first(item?.codproduto,item?.codigo,item?.code,item?.internalCode),
    description:first(item?.desc_prod,item?.descricao,item?.description,item?.name)};
}
export function vipRecord(product) {
  const id = first(product?.produto_id), description = first(product?.descricao), filename = first(product?.imagem);
  if (!id || !description || !filename) return null;
  const slug = first(product?.link).replace(/^\/+/, '');
  return {produto_id:id,codigo_interno:first(product?.codigo_erp,product?.sku) || null,
    descricao:description,imagem:`${IMAGE_BASE}/250x250/${filename}`,slug,
    productUrl:`https://www.lojaonline.nordestao.com.br/produto/${encodeURIComponent(id)}/${slug}`};
}
// Four workers, a shared deadline, and bounded per-term cache avoid serial page delays.
export function createLiveCatalogLookup({boundedJson, fetcher=fetch, now=Date.now, maxItems=80,
  concurrency=4, budgetMs=12000, cacheTtlMs=15*60_000}={}) {
  maxItems = Number.isFinite(maxItems) ? Math.min(250,Math.max(1,Math.floor(maxItems))) : 80;
  const cache = new Map(), pending = new Map();
  let token='', tokenAt=0, signingIn=null;
  async function getToken(signal) {
    if (token && now()-tokenAt < 20*60_000) return token;
    if (!signingIn) signingIn=(async()=>{
      const response=await fetcher(`${BASE}/org/52/auth/loja/login`,{method:'POST',signal,
        headers:{'Content-Type':'application/json',Accept:'application/json',DomainKey:DOMAIN,OrganizationId:'52'},
        body:JSON.stringify({domain:DOMAIN,username:'loja',key:PUBLIC_KEY})});
      const body=await boundedJson(response,100_000);
      const value=typeof body?.data === 'string' ? body.data : Array.isArray(body?.data) ? body.data.join('') : body?.data?.token;
      if(!response.ok || typeof value !== 'string' || value.length < 40)throw Error('VIP_AUTH_UNAVAILABLE');
      token=value;tokenAt=now();return token;
    })().finally(()=>{signingIn=null;});
    return signingIn;
  }
  async function search(term,signal) {
    const cached=cache.get(term);
    if(cached && cached.until>now())return cached.products;
    if(pending.has(term))return pending.get(term);
    const job=(async()=>{
      const access=await getToken(signal);
      const query=encodeURIComponent(term.trim().replace(/\s+/g,'+'));
      const response=await fetcher(`${BASE}/org/52/filial/1/centro_distribuicao/2/loja/buscas/produtos/termo/${query}/rapida?session=nrd-${crypto.randomUUID()}`,
        {signal,headers:{Accept:'application/json',DomainKey:DOMAIN,OrganizationId:'52',Authorization:'Bearer '+access}});
      if(response.status===401){token='';throw Error('VIP_AUTH_EXPIRED');}
      if(!response.ok)throw Error('VIP_SEARCH_UNAVAILABLE');
      const body=await boundedJson(response,1_000_000);
      const raw=body?.data?.produtos;
      if(!Array.isArray(raw))throw Error('VIP_INVALID_PAYLOAD');
      const products=raw.map(vipRecord).filter(Boolean);
      cache.delete(term);
      while(cache.size>=2000)cache.delete(cache.keys().next().value);
      cache.set(term,{products,until:now()+(products.length?cacheTtlMs:60000)});
      return products;
    })().finally(()=>pending.delete(term));
    pending.set(term,job);return job;
  }
  return async items => {
    const signal=AbortSignal.timeout(budgetMs), results=new Map();
    const unique=new Map();
    for(const item of items){const identity=catalogIdentity(item);if(identity.code||identity.description)unique.set(JSON.stringify(identity),identity);}
    const queue=[...unique.values()].slice(0,maxItems);let cursor=0;
    await Promise.all(Array.from({length:Math.min(concurrency,queue.length)},async()=>{
      while(cursor<queue.length && !signal.aborted){
        const item=queue[cursor++];
        try {
          let products=item.code ? await search(item.code,signal) : [];
          if(!products.length && item.description && !signal.aborted)products=await search(item.description,signal);
          for(const product of products)results.set(product.produto_id,product);
        } catch { /* One failed term must not discard the successful lookups. */ }
      }
    }));
    return [...results.values()];
  };
}
