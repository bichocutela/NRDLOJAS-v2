import { LOGIN_PROVIDERS } from '../_shared/integration-logins.mjs';
import { boundedJson } from '../nrd-price-gateway/handler.mjs';

const headers = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
export function createLoginManager({ verify, store, testConnection }) {
  const reply = (status, body) => Response.json(body, { status, headers });
  return async req => {
    if (req.method !== 'POST') return reply(405, { error: 'METHOD_NOT_ALLOWED' });
    let identity;
    try { identity = await verify(req.headers.get('x-firebase-token') || ''); } catch {}
    if (identity?.email !== 'mestre@nrdlojas.com' || !/^[A-Za-z0-9_-]{1,128}$/.test(identity?.sub ?? '')) {
      return reply(403, { error: 'ACCESS_DENIED' });
    }
    let input;
    try { input = await boundedJson(req, 8192); } catch { return reply(400, { error: 'INVALID_REQUEST' }); }
    if (!input || Array.isArray(input)) return reply(400, { error: 'INVALID_REQUEST' });
    try {
      if (input.operation === 'list') {
        const providers = await Promise.all(LOGIN_PROVIDERS.map(async provider => {
          const value = await store.get(provider, true);
          return { provider, configured: Boolean(value.login && value.password),
            revision: value.version, updatedAt: value.updatedAt };
        }));
        return reply(200, { providers });
      }
      if (!['test','save'].includes(input.operation) || !LOGIN_PROVIDERS.includes(input.provider)) {
        return reply(400, { error: 'INVALID_REQUEST' });
      }
      const current = input.operation === 'test' && input.current === true;
      const candidate = current ? await store.get(input.provider, true) :
        { login: typeof input.login === 'string' ? input.login.trim() : '', password: input.password };
      if (!candidate.login || candidate.login.length > 256 || typeof candidate.password !== 'string' ||
          !candidate.password || candidate.password.length > 512 ||
          (input.operation === 'save' && (!Number.isSafeInteger(input.revision) || input.revision < 0))) {
        return reply(400, { error: 'INVALID_CREDENTIALS', preserved: true });
      }
      try { await testConnection(input.provider, candidate); }
      catch { return reply(502, { error: 'LOGIN_TEST_FAILED', preserved: true }); }
      if (input.operation === 'test') return reply(200, { ok: true });
      const saved = await store.save(input.provider, candidate.login, candidate.password, identity.sub, input.revision);
      return reply(200, { ok: true, revision: saved.revision, updatedAt: saved.updatedAt });
    } catch (error) {
      if (error?.message === 'CREDENTIAL_CONFLICT') return reply(409, { error: 'CREDENTIAL_CONFLICT', preserved: true });
      if (['LOGIN_IN_USE','LOGIN_RECOVERY_PENDING'].includes(error?.message)) return reply(409, { error: error.message, preserved: true });
      return reply(503, { error: 'LOGIN_MANAGER_UNAVAILABLE' });
    }
  };
}
