const API = 'https://idevercustomerportalapiprod.azurewebsites.net/api';
const readable = /^\/(?:v2\.0\/Promotion\/(?:customer|categories)|promotion\/customer(?:\/[1-9][0-9]*\/item\/[1-9][0-9]*)?|store\/preferential)$/;

// Server-only client. getCredentials must read a separately provisioned secret;
// no credentials, token values or upstream error bodies may be logged/returned.
export function createPortalSession({ getCredentials, getTenantToken,
  fetchImpl = fetch, now = Date.now, loginRetryMs = 300000 }) {
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
        method: 'POST', body, headers: { tokenAuth: tenantToken, Accept: 'application/json' },
        redirect: 'error', signal: AbortSignal.timeout(12000)
      });
      if (!response.ok) throw new Error('PORTAL_AUTH_UNAVAILABLE');
      const result = await response.json();
      const expiresAt = Date.parse(result?.expiration);
      if (typeof result?.access_token !== 'string' || !result.access_token ||
          !Number.isFinite(expiresAt) || expiresAt <= now() + 60000 ||
          result.need_update_terms === true || result.need_update_favorite_store === true) {
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

  async function request(path, params) {
    if (!readable.test(path)) throw new Error('PORTAL_ROUTE_NOT_ALLOWED');
    const url = new URL(API + path);
    for (const [key, value] of Object.entries(params ?? {})) {
      if (value !== undefined) url.searchParams.set(key, String(value));
    }
    async function send(auth) {
      return fetchImpl(url, { method: 'GET', redirect: 'error',
        headers: { tokenAuth: auth.tenantToken, Authorization: `Bearer ${auth.token}`, Accept: 'application/json' },
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
    if (!response.ok) throw new Error('PORTAL_SOURCE_UNAVAILABLE');
    return response.json();
  }

  return { get: request };
}
