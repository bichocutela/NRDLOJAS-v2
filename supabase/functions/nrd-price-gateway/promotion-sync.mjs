function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(k=>[k,canonical(value[k])]));
  return value;
}
export async function digest(value) {
  const bytes = new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify(canonical(value)))));
  return Array.from(bytes).map(x=>x.toString(16).padStart(2,'0')).join('');
}
export async function snapshot(items) {
  const rows = await Promise.all(items.map(async item=>({id:String(item.id ?? `${item.code}|${item.barCode}|${item.description}`),hash:await digest(item),item})));
  if (new Set(rows.map(r=>r.id)).size !== rows.length) throw Error('DUPLICATE_PRODUCTS');
  rows.sort((a,b)=>a.id.localeCompare(b.id));
  return {rows,revision:await digest(rows.map(({id,hash})=>({id,hash})))};
}
export function diff(rows, manifest) {
  const previous=new Map(manifest.map(({id,hash})=>[id,hash]));
  const current=new Set(rows.map(r=>r.id));
  return {items:rows.filter(row=>previous.get(row.id)!==row.hash),removedIds:[...previous.keys()].filter(id=>!current.has(id))};
}
export function validSync(input) {
  if (!input || Array.isArray(input)) return false;
  if (input.operation === 'promotion_status') return Object.keys(input).length === 1;
  if (!['promotion_sync','promotion_refresh'].includes(input.operation) || Object.keys(input).some(k=>!['operation','revision','manifest'].includes(k))) return false;
  if (typeof input.revision !== 'string' || !/^(?:[a-f0-9]{64})?$/.test(input.revision) || !Array.isArray(input.manifest) || input.manifest.length>10000) return false;
  const seen=new Set();
  return input.manifest.every(row=>{
    if (!row || Object.keys(row).sort().join(',')!=='hash,id' || typeof row.id!=='string' || row.id.length>512 || !row.id || typeof row.hash!=='string' || !/^[a-f0-9]{64}$/.test(row.hash) || seen.has(row.id)) return false;
    seen.add(row.id); return true;
  });
}
export function retainCatalogMetadata(item,previous) {
  if(!previous || item.catalog_match_type || String(item.code ?? '')!==String(previous.code ?? '') ||
    String(item.barCode ?? '')!==String(previous.barCode ?? ''))return item;
  const result={...item};
  for(const key of ['imagem','imageUrl','linkloja','productUrl','catalog_match_type','catalog_match_score','catalog_product_id'])
    if(previous.catalog_match_type && previous[key]!=null)result[key]=previous[key];
  if(previous.catalog_category_source==='official')
    for(const key of ['nrdCategory','catalog_department','catalog_category_source','catalog_section_id'])
      if(previous[key]!=null)result[key]=previous[key];
  return result;
}
/** Shared ACP snapshot, immutable pages and one atomic pointer; stale data survives failed refresh. */
export function createPromotionSync({cache,background,now=()=>Date.now(),sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms))}) {
  let inFlight=null;
  async function refresh(readPage,enrichItems) {
    if (inFlight) return inFlight;
    inFlight=(async()=>{
      const claim=await cache.claim('promotion_snapshot_v3',180);
      if (!claim) return null;
      try {
        const products=[]; let expected=null;
        for(let page=0;page<100;page++) {
          const result=await readPage(page);
          if (!Array.isArray(result.items) || result.pageIndex!==page || !Number.isInteger(result.totalCount) || result.totalCount<0 || result.totalCount>10000 || !Number.isInteger(result.totalPages) || result.totalPages>100 || result.totalPages<0) throw Error('INVALID_SNAPSHOT');
          if (expected===null) expected=result.totalCount;
          if (expected!==result.totalCount) throw Error('UNSTABLE_SNAPSHOT');
          products.push(...result.items);
          if(page+1>=result.totalPages) break;
          if(!result.items.length) throw Error('INCOMPLETE_SNAPSHOT');
        }
        if(products.length!==expected) throw Error('INCOMPLETE_SNAPSHOT');
        console.info('PROMOTION_SNAPSHOT_STAGE',JSON.stringify({stage:'read',count:products.length}));
        const enriched=enrichItems ? await enrichItems(products) : products;
        if(!Array.isArray(enriched) || enriched.length!==products.length)throw Error('INVALID_ENRICHMENT');
        const previousKeys=Array.from({length:claim.payload.pages ?? 0},(_,i)=>`snapshot_${claim.payload.generation}_${i}`);
        const previousPages=await cache.many(previousKeys);
        const previousItems=new Map(previousKeys.flatMap(key=>previousPages.get(key)?.payload.rows ?? []).map(row=>[row.id,row.item]));
        const stable=enriched.map(item=>retainCatalogMetadata(item,previousItems.get(String(item.id ?? `${item.code}|${item.barCode}|${item.description}`))));
        const next=await snapshot(stable);
        if(next.revision===claim.payload.revision) {
          return (await cache.finish('promotion_snapshot_v3',claim,{...claim.payload,checkedAt:now()}))?.payload;
        }
        const generation=crypto.randomUUID().replaceAll('-',''); const pages=[]; let chunk=[]; let bytes=0;
        for(const row of next.rows) {
          const size=new TextEncoder().encode(JSON.stringify(row)).length;
          if(size>700000) throw Error('PRODUCT_TOO_LARGE');
          if(bytes+size>700000 && chunk.length) { pages.push(chunk); chunk=[]; bytes=0; }
          chunk.push(row); bytes+=size;
        }
        if(chunk.length) pages.push(chunk);
        for(let index=0;index<pages.length;index++) {
          if(!await cache.write(`snapshot_${generation}_${index}`,{rows:pages[index]})) throw Error('CACHE_UNAVAILABLE');
        }
        const retired=[];
        for(const old of claim.payload.retired ?? []) {
          if(old.deleteAfter>now()) retired.push(old);
          else {
            try { for(let i=0;i<old.pages;i++) await cache.remove(`snapshot_${old.generation}_${i}`); }
            catch { retired.push(old); }
          }
        }
        if(claim.payload.generation) retired.push({generation:claim.payload.generation,pages:claim.payload.pages,deleteAfter:now()+30*60_000});
        const pointer={retired,revision:next.revision,generation,pages:pages.length,count:next.rows.length,checkedAt:now()};
        return (await cache.finish('promotion_snapshot_v3',claim,pointer))?.payload;
      } catch(error) {
        await cache.finish('promotion_snapshot_v3',claim,{...claim.payload,retryAt:now()+60000}).catch(()=>{});
        throw error;
      }
    })();
    try {return await inFlight;} finally {inFlight=null;}
  }
  return async ({input,readPage,enrichItems})=>{
    let pointer=(await cache.get('promotion_snapshot_v3'))?.payload;
    if(!pointer?.revision) {
      pointer=await refresh(readPage,enrichItems);
      if(!pointer?.revision) {
        pointer=(await cache.get('promotion_snapshot_v3'))?.payload;
        if(!pointer?.revision) throw Error('INITIAL_SYNC_BUSY');
      }
    } else if(input.operation==='promotion_refresh') {
      // Another Edge instance may own the lease. Never report the previous page as a successful fresh scan.
      const startedAt=now();
      const previousCheck=pointer.checkedAt;
      const fresh=await refresh(readPage,enrichItems);
      if(fresh) pointer=fresh;
      else {
        let published=false;
        for(const delay of [500,1000,2000,4000,8000,8000,6500]) {
          await sleep(delay);
          const candidate=(await (cache.getFresh ? cache.getFresh('promotion_snapshot_v3') : cache.get('promotion_snapshot_v3')))?.payload;
          if(candidate?.revision && candidate.checkedAt>=startedAt && candidate.checkedAt>previousCheck) {
            pointer=candidate; published=true; break;
          }
        }
        if(!published) throw Error('REFRESH_BUSY');
      }
    } else if(now()-pointer.checkedAt>=60000 && (pointer.retryAt ?? 0)<=now()) {
      // Return the saved status immediately; the next poll observes the shared refresh.
      // A closed/slow client does not own the server refresh lifetime.
      const job=refresh(readPage,enrichItems).catch(()=>null);
      if(background) background(job);
      else pointer=await job ?? pointer;
    }
    const status={revision:pointer.revision,checkedAt:pointer.checkedAt,count:pointer.count};
    if(input.operation==='promotion_status') return status;
    // Compare IDs/hashes even if a caller incorrectly claims the same revision.
    const keys=Array.from({length:pointer.pages},(_,i)=>`snapshot_${pointer.generation}_${i}`);
    const pages=await cache.many(keys); const rows=keys.flatMap(key=>pages.get(key)?.payload.rows ?? []);
    if(rows.length!==pointer.count) throw Error('INCOMPLETE_CACHE');
    return {...status,...diff(rows,input.manifest)};
  };
}

