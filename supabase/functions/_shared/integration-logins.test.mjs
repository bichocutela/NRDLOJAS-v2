import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createIntegrationLogins } from './integration-logins.mjs';

test('verified absence uses the existing environment without persisting or exposing it', async () => {
  const source = createIntegrationLogins({ url: 'https://project.invalid', key: 'fake-key',
    fetcher: async () => Response.json({ revision: 0 }), fallback: () => ({ login: 'old', password: 'old-password' }) });
  const value = await source.get('legacy');
  assert.equal(value.login, 'old'); assert.equal(value.revision, 'environment:0');
});
test('coalesces reads and discovers a newer vault revision within the cache period', async () => {
  let time = 0, revision = 1, reads = 0;
  const source = createIntegrationLogins({ url: 'https://project.invalid', key: 'fake-key', now: () => time,
    fetcher: async () => { reads++; return Response.json({ login: 'user', password: 'password', revision }); } });
  await Promise.all([source.get('offers'), source.get('offers')]); assert.equal(reads, 1);
  revision = 2; time += 15001;
  assert.equal((await source.get('offers')).revision, 'vault:2'); assert.equal(reads, 2);
});
test('a storage outage never silently reverts to the old environment credentials', async () => {
  const source = createIntegrationLogins({ url: 'https://project.invalid', key: 'fake-key',
    fetcher: async () => Response.json({ message: 'private provider secret' }, { status: 500 }),
    fallback: () => ({ login: 'old', password: 'old-password' }) });
  await assert.rejects(source.get('legacy'), /^Error: LOGIN_STORAGE_UNAVAILABLE$/);
});
