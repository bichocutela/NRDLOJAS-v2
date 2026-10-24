// Providers return a complete normalized catalog for exactly one store.
// The gateway never combines catalogs from two sources or two stores.
export function createOfferFailover({ primary, backup, now = Date.now,
  readCache = async () => null, writeCache = async () => {},
  cacheMs = 300000, primaryRetryMs = 60000 }) {
  const cache = new Map(), pending = new Map(), failed = new Set(), retryAt = new Map();
  let primaryRetryAt = 0;

  async function load(storeId) {
    try {
      const value = await readCache(storeId);
      if (value?.storeId === storeId && Array.isArray(value.offers) &&
          ['primary','authenticated'].includes(value.source) &&
          Number.isFinite(Date.parse(value.fetchedAt)) && Date.parse(value.fetchedAt) <= now()) {
        const previous = cache.get(storeId);
        if (!previous || Date.parse(value.fetchedAt) > Date.parse(previous.fetchedAt)) {
          cache.set(storeId, { storeId, offers: value.offers, fetchedAt: value.fetchedAt, source: value.source });
          failed.delete(storeId);
          retryAt.delete(storeId);
        }
        if (value.source === 'authenticated') primaryRetryAt = Math.max(primaryRetryAt, Date.parse(value.fetchedAt) + primaryRetryMs);
      }
    } catch { /* Cache outages must not disable live sources. */ }
  }

  async function save(storeId, value) {
    cache.set(storeId, value);
    try { await writeCache(value); } catch { /* Keep the successful live result. */ }
  }

  function validate(value, storeId, source) {
    if (!value || value.storeId !== storeId || !Array.isArray(value.offers)) {
      throw new Error('SOURCE_STORE_MISMATCH');
    }
    // Only the public catalog crosses this boundary. Never forward upstream
    // headers, session tokens, account data or unknown response properties.
    return { storeId, offers: value.offers, fetchedAt: new Date(now()).toISOString(), source };
  }

  async function refresh(storeId) {
    if (now() >= primaryRetryAt) {
      try {
        const value = validate(await primary(storeId), storeId, 'primary');
        primaryRetryAt = 0;
        await save(storeId, value);
        failed.delete(storeId);
        retryAt.delete(storeId);
        return { ...value, stale: false };
      } catch {
        primaryRetryAt = now() + primaryRetryMs;
      }
    }
    const cachedBackup = cache.get(storeId);
    if (cachedBackup?.source === 'authenticated' && !failed.has(storeId) &&
        Date.parse(cachedBackup.fetchedAt) + cacheMs > now()) return { ...cachedBackup, stale: false };
    try {
      const value = validate(await backup(storeId), storeId, 'authenticated');
      await save(storeId, value);
      failed.delete(storeId);
      retryAt.delete(storeId);
      return { ...value, stale: false };
    } catch {
      failed.add(storeId);
      retryAt.set(storeId, now() + primaryRetryMs);
      await load(storeId);
      const previous = cache.get(storeId);
      if (previous) return { ...previous, stale: failed.has(storeId) || Date.parse(previous.fetchedAt) + cacheMs <= now() };
      throw new Error('OFFERS_UNAVAILABLE');
    }
  }

  return {
    async get(storeId) {
      if (!Number.isSafeInteger(storeId) || storeId <= 0) throw new Error('INVALID_STORE');
      if (!cache.has(storeId) || failed.has(storeId)) await load(storeId);
      const previous = cache.get(storeId);
      if ((retryAt.get(storeId) ?? 0) > now()) {
        if (previous) return { ...previous, stale: true };
        throw new Error('OFFERS_UNAVAILABLE');
      }
      // A healthy backup must not postpone probing the recovered primary for
      // the full cache lifetime. Failed refreshes never advance fetchedAt.
      const probePrimary = previous?.source === 'authenticated' && now() >= primaryRetryAt;
      if (previous && !probePrimary && Date.parse(previous.fetchedAt) + cacheMs > now()) {
        return { ...previous, stale: failed.has(storeId) };
      }
      let task = pending.get(storeId);
      if (!task) {
        task = refresh(storeId).finally(() => pending.delete(storeId));
        pending.set(storeId, task);
      }
      return task;
    }
  };
}
