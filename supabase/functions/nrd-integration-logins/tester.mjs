import { createPortalSession } from '../nrd-store-offers/portal-session.mjs';
import { createHandler, boundedJson } from '../nrd-price-gateway/handler.mjs';
import { createAcpTechClient } from '../nrd-price-gateway/acptech-client.mjs';

export function createLoginTester({ fetcher = fetch, acpTechConfig = {} } = {}) {
  return async (provider, candidate) => {
    if (provider === 'offers') {
      const session = createPortalSession({ fetchImpl: fetcher,
        getCredentials: async () => ({ user: candidate.login, password: candidate.password }) });
      const preferred = await session.get('/store/preferential');
      if (!Number.isSafeInteger(preferred?.id) || preferred.id <= 0) throw new Error('LOGIN_TEST_FAILED');
      return;
    }
    const tech = provider === 'acptech';
    const handler = createHandler({ authorize: async () => ({ allowed: true, probe: true }),
      credentials: () => candidate, fetcher, provider: tech ? 'acptech' : 'legacy',
      providerSettings: async () => ({ legacyEnabled: !tech, acpTechEnabled: tech, primary: tech ? 'acptech' : 'legacy' }),
      acpTechClient: tech ? createAcpTechClient({ fetcher, boundedJson, credentials: () => candidate, config: acpTechConfig }) : null });
    const response = await handler(new Request('https://nrd.internal/login-test', {
      method: 'POST', body: JSON.stringify({ operation: 'health' }) }));
    if (!response.ok || (await response.json())?.ok !== true) throw new Error('LOGIN_TEST_FAILED');
  };
}
