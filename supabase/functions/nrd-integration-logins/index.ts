import { createRemoteJWKSet, jwtVerify } from 'npm:jose@5.9.6';
import { createIntegrationLogins } from '../_shared/integration-logins.mjs';
import { createLoginManager } from './handler.mjs';
import { createLoginTester } from './tester.mjs';

const project = 'appcodigo-7f245';
const jwks = createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
const store = createIntegrationLogins({ url: Deno.env.get('SUPABASE_URL'), key: Deno.env.get('SUPABASE_SERVICE_ROLE_KEY'),
  fallback: provider => provider === 'legacy' ? { login: Deno.env.get('NRD_PRICE_LOGIN'), password: Deno.env.get('NRD_PRICE_PASSWORD') } :
    provider === 'acptech' ? { login: Deno.env.get('ACPTECH_LOGIN'), password: Deno.env.get('ACPTECH_PASSWORD') } : {} });
Deno.serve(createLoginManager({ store,
  verify: async (token: string) => (await jwtVerify(token, jwks, { algorithms: ['RS256'],
    issuer: `https://securetoken.google.com/${project}`, audience: project })).payload,
  testConnection: createLoginTester({ acpTechConfig: { url: Deno.env.get('ACPTECH_SUPABASE_URL'),
    anonKey: Deno.env.get('ACPTECH_SUPABASE_ANON_KEY'), storeSlug: Deno.env.get('ACPTECH_STORE_SLUG') ?? 'nordestao12' } })
}));
