const PORTAL_ORIGIN = 'https://pravoce.nordestao.com.br';
const API = 'https://idevercustomerportalapiprod.azurewebsites.net/api';
const readable = /^\/(?:v2\.0\/Promotion\/(?:customer|categories)|promotion\/customer(?:\/[1-9][0-9]*\/item\/[1-9][0-9]*)?|store\/preferential)$/;

export function portalExpiration(value) {
  if (typeof value === 'number' || (typeof value === 'string' && /^\d+$/.test(value))) {
    const numeric = Number(value);
    return numeric > 100000000000 ? numeric : numeric * 1000;
  }
  return typeof value === 'string' ? Date.parse(value) : NaN;
}

// Server-only client. getCredentials must read a separately provisioned secret;
// no credentials, token values or upstream error bodies may be logged/returned.
export function createPortalSession({ getCredentials, getTenantToken = async () => '',
  fetchImpl = fetch, now = Date.now, loginRetryMs = 300000, onHttpFailure = () => {} }) {
  let session, loginPending, blockedUntil = 0;

  async function login() {
    if (now() < blockedUntil) throw new Error('PORTAL_RECONNECT_DEFERRED');
    try {
      const credentials = await getCredentials();
      if (!credentials?.user || !credentials?.password) throw new Error('PORTAL_NOT_CONFIGURED');
      const body = new FormData();
      body.append('user', credentials.user);
      body.append('password', credentials.password);
      const tenantToken = await getTenantToken();
      const response = await fetchImpl(API + '/auth/person', {
        method: 'POST', body, headers: { ...(tenantToken ? { tokenAuth: tenantToken } : {}), Accept: 'application/json',
          Origin: PORTAL_ORIGIN, Referer: PORTAL_ORIGIN + '/' },
        redirect: 'error', signal: AbortSignal.timeout(12000)
      });
      if (!response.ok) {
        onHttpFailure('/auth/person', response.status);
        throw new Error('PORTAL_AUTH_UNAVAILABLE');
      }
      const envelope = await response.json();
      if (envelope?.isSuccess !== true) throw new Error('PORTAL_AUTH_UNAVAILABLE');
      const result = envelope.data;
      const expiresAt = portalExpiration(result?.expiration);
      if (typeof result?.access_token !== 'string' || !result.access_token ||
          !Number.isFinite(expiresAt) || expiresAt <= now() + 60000 ||
          result.need_update_terms === true || result.need_update === true || result.need_update_favorite_store === true) {
        throw new Error('PORTAL_AUTH_REQUIRES_ADMIN');
      }
      session = { token: result.access_token, expiresAt, tenantToken };
      return session;
    } catch {
      session = undefined;
      blockedUntil = now() + loginRetryMs;
      throw new Error('PORTAL_AUTH_UNAVAILABLE');
    }
  }

  async function currentSession() {
    if (session && session.expiresAt > now() + 60000) return session;
    if (!loginPending) loginPending = login().finally(() => { loginPending = undefined; });
    return loginPending;
  }

  async function request(path, params, method = 'GET') {
    if (method === 'GET' ? !readable.test(path) : !/^\/store\/preferential\/[1-9][0-9]*$/.test(path)) {
      throw new Error('PORTAL_ROUTE_NOT_ALLOWED');
    }
    const url = new URL(API + path);
    for (const [key, value] of Object.entries(params ?? {})) {
      if (value !== undefined) url.searchParams.set(key, String(value));
    }
    async function send(auth) {
      return fetchImpl(url, { method, redirect: 'error',
        headers: { ...(auth.tenantToken ? { tokenAuth: auth.tenantToken } : {}), Authorization: `Bearer ${auth.token}`, Accept: 'application/json',
          Origin: PORTAL_ORIGIN, Referer: PORTAL_ORIGIN + '/' },
        signal: AbortSignal.timeout(15000) });
    }
    let auth = await currentSession();
    let response = await send(auth);
    if (response.status === 401) {
      // Do not discard a session another concurrent request already renewed.
      if (session === auth) session = undefined;
      auth = await currentSession();
      response = await send(auth);
      if (response.status === 401) {
        if (session === auth) session = undefined;
        blockedUntil = now() + loginRetryMs;
      }
    }
    if (!response.ok) {
      onHttpFailure(path, response.status);
      throw new Error('PORTAL_SOURCE_UNAVAILABLE');
    }
    return method === 'PUT' || response.status === 204 ? null : response.json();
  }

  return { get: (path, params) => request(path, params), selectStore: storeId => {
    if (!Number.isSafeInteger(storeId) || storeId <= 0) throw new Error('INVALID_STORE');
    return request('/store/preferential/' + storeId, undefined, 'PUT');
  } };
}
