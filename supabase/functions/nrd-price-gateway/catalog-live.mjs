const BASE = 'https://services.vipcommerce.com.br/api-admin/v1';
const DOMAIN = 'nordestaomaisvoce.com.br';
const PUBLIC_KEY = 'df072f85df9bf7dd71b6811c34bdbaa4f219d98775b56cff9dfa5f8ca1bf8469';
const IMAGE_BASE = 'https://produto-assets-vipcommerce-com-br.br-se1.magaluobjects.com';
import {OFFICIAL_DEPARTMENTS,normalizeText,resolvePromotions} from '../nordestao-catalog-resolver/handler.mjs';
import {VERIFIED_DEPARTMENT_MAP} from './department-map.mjs';
const first = (...values) => values.map(value => String(value ?? '').trim()).find(Boolean) ?? '';
export function catalogIdentity(item) {
  return {code:first(item?.codproduto,item?.codigo,item?.code,item?.internalCode),
    ean:first(item?.barCode,item?.barcode,item?.codigo_barras,item?.ean),
    description:first(item?.desc_prod,item?.descricao,item?.description,item?.name)};
}
export function buildDepartmentMap(roots) {
  const labels=new Map(OFFICIAL_DEPARTMENTS.map(label=>[normalizeText(label),label]));
  labels.set('leites e lacticinios','Leites e laticínios');
  const result={};
  function walk(node,label) {
    if(node.classificacao_mercadologica_id != null)result[String(node.classificacao_mercadologica_id)]=label;
    for(const child of node.children ?? [])walk(child,label);
  }
  for(const root of roots ?? []){const label=labels.get(normalizeText(root.descricao));if(label)walk(root,label);}
  return result;
}
export function vipRecord(product,departments=VERIFIED_DEPARTMENT_MAP) {
  const id=first(product?.produto_id),description=first(product?.descricao),filename=first(product?.imagem);
  if(!id || !description)return null;
  const slug=first(product?.link).replace(/^\/+/, '');
  const sectionId=first(product?.classificacao_mercadologica_id,product?.secao_id);
  return {produto_id:id,codigo_interno:first(product?.codigo_erp) || null,codigo_barras:first(product?.codigo_barras) || null,
    descricao:description,imagem:filename?`${IMAGE_BASE}/250x250/${filename}`:null,slug,
    department:departments[sectionId] ?? null,sectionId,
    productUrl:`https://www.lojaonline.nordestao.com.br/produto/${encodeURIComponent(id)}/${slug}`};
}
async function identityKey(identity) {
  const bytes=new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify(identity))));
  return 'catalog_v2_'+Array.from(bytes).map(x=>x.toString(16).padStart(2,'0')).join('');
}
/** Durable associations cover every page; only unresolved/stale products consume the lookup budget. */
export function createLiveCatalogLookup({boundedJson,fetcher=fetch,now=Date.now,maxItems=80,store=null,
  concurrency=4,budgetMs=12000,cacheTtlMs=24*60*60_000}={}) {
  maxItems=Number.isFinite(maxItems)?Math.min(250,Math.max(1,Math.floor(maxItems))):80;
  const cache=new Map(),pending=new Map();
  let token='',tokenAt=0,signingIn=null,departments=VERIFIED_DEPARTMENT_MAP,departmentUntil=0,loadingDepartments=null;
  async function getToken(signal) {
    if(token && now()-tokenAt<20*60_000)return token;
    if(!signingIn)signingIn=(async()=>{
      const response=await fetcher(`${BASE}/org/52/auth/loja/login`,{method:'POST',signal,
        headers:{'Content-Type':'application/json',Accept:'application/json',DomainKey:DOMAIN,OrganizationId:'52'},
        body:JSON.stringify({domain:DOMAIN,username:'loja',key:PUBLIC_KEY})});
      const body=await boundedJson(response,100_000);
      const value=typeof body?.data==='string'?body.data:Array.isArray(body?.data)?body.data.join(''):body?.data?.token;
      if(!response.ok || typeof value!=='string' || value.length<40)throw Error('VIP_AUTH_UNAVAILABLE');
      token=value;tokenAt=now();return token;
    })().finally(()=>{signingIn=null;});
    return signingIn;
  }
  function headers(access) { return {Accept:'application/json',DomainKey:DOMAIN,OrganizationId:'52',Authorization:'Bearer '+access}; }
  async function loadDepartments(signal) {
    if(now()<departmentUntil)return departments;
    if(!loadingDepartments)loadingDepartments=(async()=>{
      try {
        const cached=await store?.get('catalog_departments');
        if(cached?.payload.map && cached.payload.until>now()){departments=cached.payload.map;departmentUntil=cached.payload.until;return;}
        const access=await getToken(signal);
        const response=await fetcher(`${BASE}/org/52/filial/1/centro_distribuicao/2/loja/classificacoes_mercadologicas/departamentos/arvore`,{signal,headers:headers(access)});
        if(!response.ok)throw Error('DEPARTMENTS_UNAVAILABLE');
        const body=await boundedJson(response,1_000_000),map=buildDepartmentMap(body?.data);
        if(Object.keys(map).length<16)throw Error('INVALID_DEPARTMENTS');
        departments=map;departmentUntil=now()+cacheTtlMs;
        await store?.write('catalog_departments',{map,until:departmentUntil}).catch(()=>{});
      } catch {departmentUntil=now()+60000;} // Last verified official mapping remains usable.
    })().finally(()=>{loadingDepartments=null;});
    await loadingDepartments;return departments;
  }
  async function search(term,signal) {
    const access=await getToken(signal),query=encodeURIComponent(term.trim().replace(/\s+/g,'+'));
    const response=await fetcher(`${BASE}/org/52/filial/1/centro_distribuicao/2/loja/buscas/produtos/termo/${query}/rapida?session=nrd-${crypto.randomUUID()}`,{signal,headers:headers(access)});
    if(response.status===401){token='';throw Error('VIP_AUTH_EXPIRED');}
    if(!response.ok)throw Error('VIP_SEARCH_UNAVAILABLE');
    const body=await boundedJson(response,1_000_000);
    if(!Array.isArray(body?.data?.produtos))throw Error('VIP_INVALID_PAYLOAD');
    return body.data.produtos.map(product=>vipRecord(product,departments)).filter(Boolean);
  }
  async function resolve(identity,signal) {
    const all=new Map();
    for(const term of [...new Set([identity.code,identity.ean,identity.description].filter(Boolean))]){
      for(const product of await search(term,signal))all.set(product.produto_id,product);
      const resolution=resolvePromotions([{code:identity.code,barCode:identity.ean,description:identity.description}],[...all.values()])[0]?.resolution;
      if(resolution?.matched)return resolution.product.raw;
      if(signal.aborted)break;
    }
    return null;
  }
  return async items=>{
    const signal=AbortSignal.timeout(budgetMs),results=new Map(),unique=new Map();
    for(const item of items){const identity=catalogIdentity(item);if(identity.code||identity.ean||identity.description)unique.set(JSON.stringify(identity),identity);}
    const entries=await Promise.all([...unique.values()].map(async identity=>({identity,key:await identityKey(identity)})));
    const saved=await store?.many(entries.map(e=>e.key)).catch(()=>new Map()) ?? new Map();
    const queue=[];
    for(const entry of entries){
      const association=cache.get(entry.key) ?? saved.get(entry.key)?.payload;
      if(association?.product)results.set(association.product.produto_id,association.product);
      if(!association || association.until<=now())queue.push({...entry,previous:association?.product});
    }
    // Missing products first; stale verified associations continue to render while revalidated.
    queue.sort((a,b)=>Number(Boolean(a.previous))-Number(Boolean(b.previous)));
    if(queue.length)await loadDepartments(signal);
    let cursor=0;
    await Promise.all(Array.from({length:Math.min(concurrency,queue.length,maxItems)},async()=>{
      while(cursor<Math.min(queue.length,maxItems) && !signal.aborted){
        const {identity,key,previous}=queue[cursor++];
        let job=pending.get(key);
        if(!job){
          job=(async()=>{
            let product=null,failed=false;
            try{product=await resolve(identity,signal);}catch{failed=true;}
            const value={product:product ?? previous ?? null,until:now()+(product?cacheTtlMs:failed?60000:60*60_000)};
            cache.delete(key);while(cache.size>=5000)cache.delete(cache.keys().next().value);cache.set(key,value);
            await store?.write(key,value).catch(()=>{});return value.product;
          })().finally(()=>pending.delete(key));
          pending.set(key,job);
        }
        const product=await job;if(product)results.set(product.produto_id,product);
      }
    }));
    return [...results.values()];
  };
}
