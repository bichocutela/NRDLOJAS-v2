// Providers return a complete normalized catalog for exactly one store.
// The gateway never combines catalogs from two sources or two stores.
export function createOfferFailover({ primary, backup, now = Date.now,
  cacheMs = 300000, primaryRetryMs = 60000 }) {
  const cache = new Map(), pending = new Map(), failed = new Set(), retryAt = new Map();
  let primaryRetryAt = 0;

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
        cache.set(storeId, value);
        failed.delete(storeId);
        retryAt.delete(storeId);
        return { ...value, stale: false };
      } catch {
        primaryRetryAt = now() + primaryRetryMs;
      }
    }
    try {
      const value = validate(await backup(storeId), storeId, 'authenticated');
      cache.set(storeId, value);
      failed.delete(storeId);
      retryAt.delete(storeId);
      return { ...value, stale: false };
    } catch {
      failed.add(storeId);
      retryAt.set(storeId, now() + primaryRetryMs);
      const previous = cache.get(storeId);
      if (previous) return { ...previous, stale: true };
      throw new Error('OFFERS_UNAVAILABLE');
    }
  }

  return {
    async get(storeId) {
      if (!Number.isSafeInteger(storeId) || storeId <= 0) throw new Error('INVALID_STORE');
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
