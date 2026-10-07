/** Bounded, isolate-local read cache. Distributed leases always use the backing store. */
export class ReadCache {
  constructor(store, {now = () => Date.now(), maxBytes = 32 * 1024 * 1024, maxEntries = 12000} = {}) {
    this.store = store; this.now = now; this.maxBytes = maxBytes; this.maxEntries = maxEntries;
    this.entries = new Map(); this.pending = new Map(); this.bytes = 0;
  }
  ttl(key, value) {
    if (key.startsWith('snapshot_') && value) return 30 * 60_000; // Immutable generation.
    if (key.startsWith('category_') && value?.payload.category) return 24 * 60 * 60_000;
    if (key.startsWith('category_')) return 60_000; // Missing/retry results must eventually be rechecked.
    if (key === 'promotion_snapshot' && value?.payload.revision) return 10_000;
    return 0; // Never retain budget/lease documents.
  }
  forget(key) {
    const old = this.entries.get(key);
    if (old) this.bytes -= old.bytes;
    this.entries.delete(key);
  }
  remember(key, value) {
    this.forget(key);
    const ttl = this.ttl(key, value);
    if (!ttl) return;
    const bytes = new TextEncoder().encode(JSON.stringify(value ?? null)).length;
    if (bytes > this.maxBytes) return;
    for (const [id, entry] of this.entries) if (entry.until <= this.now()) this.forget(id);
    while (this.bytes + bytes > this.maxBytes || this.entries.size >= this.maxEntries) this.forget(this.entries.keys().next().value);
    this.entries.set(key, {value:structuredClone(value ?? null), bytes, until:this.now() + ttl});
    this.bytes += bytes;
  }
  async many(keys) {
    const unique = [...new Set(keys)];
    const missing = unique.filter(key => {
      const entry = this.entries.get(key);
      if (entry && entry.until > this.now()) return false;
      this.forget(key); return !this.pending.has(key);
    });
    if (missing.length) {
      const batch = Promise.resolve().then(() => this.store.many(missing));
      for (const key of missing) {
        const job = batch.then(values => {
          const value = values.get(key) ?? null;
          this.remember(key, value); return value;
        }).finally(() => { if (this.pending.get(key) === job) this.pending.delete(key); });
        this.pending.set(key, job);
      }
    }
    const values = await Promise.all(unique.map(async key => {
      const job = this.pending.get(key);
      const value = job ? await job : this.entries.get(key)?.value;
      return [key, structuredClone(value ?? null)];
    }));
    return new Map(values.filter(([, value]) => value !== null));
  }
  async get(key) { return (await this.many([key])).get(key) ?? null; }
  async write(key, ...args) {
    // A preceding batch may still be reading an older version.
    await this.pending.get(key)?.catch(() => {});
    this.forget(key);
    const value = await this.store.write(key, ...args);
    if (value) this.remember(key, value);
    return value;
  }
  async claim(key, seconds) {
    await this.pending.get(key)?.catch(() => {});
    this.forget(key);
    return this.store.claim(key, seconds); // Fresh read + CAS; never grant a cached lease.
  }
  async finish(key, claim, payload) {
    await this.pending.get(key)?.catch(() => {});
    this.forget(key);
    const value = await this.store.finish(key, claim, payload);
    if (value) this.remember(key, value);
    return value;
  }
  async remove(key) {
    await this.pending.get(key)?.catch(() => {});
    this.forget(key); return this.store.remove(key);
  }
}
