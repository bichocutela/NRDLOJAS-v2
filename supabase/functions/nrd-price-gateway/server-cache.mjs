/** Server-only Firestore collection. Optimistic preconditions fence leases across Edge instances. */
export class ServerCache {
  constructor({project, token, fetcher = fetch}) {
    this.root = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents`;
    this.token = token; this.fetcher = fetcher;
  }
  async request(url, options = {}) {
    return this.fetcher(url, {...options, signal:AbortSignal.timeout(10000), headers:{
      'Content-Type':'application/json', Authorization:'Bearer '+await this.token(), ...options.headers}});
  }
  url(key) { if (!/^[a-zA-Z0-9_-]{1,160}$/.test(key)) throw Error('INVALID_CACHE_KEY'); return `${this.root}/server_promotion_cache/${key}`; }
  decode(doc) {
    if (!doc?.fields?.payload?.stringValue) return null;
    return {payload:JSON.parse(doc.fields.payload.stringValue), lease:Number(doc.fields.lease?.integerValue ?? 0), version:doc.updateTime};
  }
  async get(key) {
    const response = await this.request(this.url(key));
    if (response.status === 404) return null;
    if (!response.ok) throw Error('CACHE_UNAVAILABLE');
    return this.decode(await response.json());
  }
  async many(keys) {
    if (!keys.length) return new Map();
    const results = new Map();
    for (let offset = 0; offset < keys.length; offset += 100) {
      const batch = keys.slice(offset, offset + 100);
      const response = await this.request(this.root+':batchGet', {method:'POST', body:JSON.stringify({documents:batch.map(key => this.url(key).split('/v1/')[1])})});
      if (!response.ok) throw Error('CACHE_UNAVAILABLE');
      const docs = await response.json();
      for (const result of Array.isArray(docs) ? docs : [docs]) {
        if (result.found) results.set(result.found.name.split('/').at(-1), this.decode(result.found));
      }
    }
    return results;
  }
  async write(key, payload, lease = 0, version = undefined) {
    const url = new URL(this.url(key));
    if (version === null) url.searchParams.set('currentDocument.exists','false');
    else if (version) url.searchParams.set('currentDocument.updateTime',version);
    const response = await this.request(url.toString(), {method:'PATCH', body:JSON.stringify({fields:{
      payload:{stringValue:JSON.stringify(payload)}, lease:{integerValue:String(lease)}}})});
    if ([409,412,404].includes(response.status)) return null;
    if (!response.ok) throw Error('CACHE_UNAVAILABLE');
    return this.decode(await response.json());
  }
  async remove(key) {
    const response = await this.request(this.url(key), {method:'DELETE'});
    if (!response.ok && response.status !== 404) throw Error('CACHE_UNAVAILABLE');
  }
  async claim(key, seconds = 90) {
    const current = await this.get(key);
    if ((current?.lease ?? 0) > Date.now()) return null;
    return this.write(key, current?.payload ?? {}, Date.now()+seconds*1000, current?.version ?? null);
  }
  async finish(key, claim, payload) { return this.write(key, payload, 0, claim.version); }
}
