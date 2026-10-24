export const LOGIN_PROVIDERS = ['offers','legacy','acptech'];

export function createIntegrationLogins({ url, key, fallback = () => ({}), fetcher = fetch,
  now = Date.now, ttlMs = 15000 }) {
  const cache = new Map(), pending = new Map();
  async function rpc(name, body) {
    const response = await fetcher(url + '/rest/v1/rpc/' + name, { method: 'POST', redirect: 'error',
      headers: { apikey: key, Authorization: 'Bearer ' + key, 'Content-Type': 'application/json' },
      body: JSON.stringify(body), signal: AbortSignal.timeout(10000) });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(['CREDENTIAL_CONFLICT','LOGIN_IN_USE','LOGIN_RECOVERY_PENDING'].includes(error?.message) ? error.message : 'LOGIN_STORAGE_UNAVAILABLE');
    }
    return response.json();
  }
  async function get(provider, force = false) {
    if (!LOGIN_PROVIDERS.includes(provider)) throw new Error('INVALID_PROVIDER');
    if (!force && cache.get(provider)?.until > now()) return cache.get(provider).value;
    if (pending.has(provider)) return pending.get(provider);
    const work = (async () => {
      const stored = await rpc('nrd_integration_login', { p_provider: provider });
      const fromVault = Boolean(stored?.login && stored?.password);
      const original = fromVault ? stored : fallback(provider);
      const value = { login: original?.login, password: original?.password,
        version: Number(stored?.revision) || 0, revision: (fromVault ? 'vault:' : 'environment:') + (Number(stored?.revision) || 0),
        updatedAt: stored?.updatedAt ?? null, source: fromVault ? 'vault' : 'environment' };
      cache.set(provider, { value, until: now() + ttlMs });
      return value;
    })().finally(() => pending.delete(provider));
    pending.set(provider, work); return work;
  }
  return { get, async save(provider, login, password, actor, revision) {
    if (!LOGIN_PROVIDERS.includes(provider)) throw new Error('INVALID_PROVIDER');
    const result = await rpc('nrd_set_integration_login', { p_provider: provider,
      p_login: login, p_password: password, p_actor: actor, p_revision: revision });
    cache.delete(provider); return result;
  } };
}
