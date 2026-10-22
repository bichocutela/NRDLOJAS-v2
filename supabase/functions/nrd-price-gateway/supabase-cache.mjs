/** Server-only Supabase cache; SQL versions fence shared leases and stale writes. */
export class SupabaseCache {
  constructor({url,key,fetcher=fetch}) { this.url=url?.replace(/\/$/,'');this.key=key;this.fetcher=fetcher; }
  valid(key) { if(!/^[a-zA-Z0-9_-]{1,160}$/.test(key))throw Error('INVALID_CACHE_KEY');return key; }
  async request(path,options={}) {
    if(!this.url || !this.key)throw Error('CACHE_UNAVAILABLE');
    const response=await this.fetcher(`${this.url}/rest/v1/${path}`,{...options,signal:AbortSignal.timeout(10000),
      headers:{apikey:this.key,Authorization:'Bearer '+this.key,'Content-Type':'application/json',...options.headers}});
    if(!response.ok)throw Error('CACHE_UNAVAILABLE');return response;
  }
  decode(row) { return row ? {payload:row.payload,lease:Number(row.lease),version:String(row.version)} : null; }
  async many(keys) {
    const result=new Map();
    for(let offset=0;offset<keys.length;offset+=100){
      const batch=keys.slice(offset,offset+100).map(key=>this.valid(key));
      const query=new URLSearchParams({select:'key,payload,lease,version',key:`in.(${batch.join(',')})`});
      const response=await this.request('nrd_promotion_cache?'+query);
      for(const row of await response.json())result.set(row.key,this.decode(row));
    }
    return result;
  }
  async get(key) { return (await this.many([key])).get(key) ?? null; }
  async write(key,payload,lease=0,version=undefined) {
    const response=await this.request('rpc/nrd_cache_write',{method:'POST',body:JSON.stringify({p_key:this.valid(key),
      p_payload:payload,p_lease:lease,p_mode:version===null?'insert':version===undefined?'upsert':'cas',p_version:version ?? null})});
    return this.decode((await response.json())[0]);
  }
  async remove(key) { await this.request('nrd_promotion_cache?'+new URLSearchParams({key:'eq.'+this.valid(key)}),{method:'DELETE'}); }
  async claim(key,seconds=90) {
    const current=await this.get(key);if((current?.lease ?? 0)>Date.now())return null;
    return this.write(key,current?.payload ?? {},Date.now()+seconds*1000,current?.version ?? null);
  }
  async finish(key,claim,payload) { return this.write(key,payload,0,claim.version); }
}
