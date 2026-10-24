import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createLoginManager } from './handler.mjs';

function fixture({ rejectTest = false, conflict = false } = {}) {
  const saved = [], tested = [], reads = [];
  const handler = createLoginManager({
    verify: async token => token === 'master' ? { sub: 'master-uid', email: 'mestre@nrdlojas.com' } :
      { sub: 'ordinary-uid', email: 'user@usuarios.nrdlojas.com' },
    store: { get: async provider => { reads.push(provider); return { login: 'saved-user', password: 'saved-secret', version: 7, updatedAt: null }; },
      save: async (...args) => { if (conflict) throw new Error('CREDENTIAL_CONFLICT'); saved.push(args); return { revision: 8 }; } },
    testConnection: async (provider, value) => {
      tested.push({ provider, ...value });
      if (rejectTest) throw new Error('upstream body with saved-secret');
    }
  });
  return { saved, tested, reads, request: (body, token = 'master') => handler(new Request('https://test.invalid', {
    method: 'POST', headers: { 'x-firebase-token': token }, body: JSON.stringify(body) })) };
}
const candidate = { operation: 'save', provider: 'offers', login: ' new-user ', password: 'new-secret', revision: 7 };

test('rejects ordinary identities before reading credentials or calling the provider', async () => {
  const f = fixture(); assert.equal((await f.request(candidate, 'ordinary')).status, 403);
  assert.deepEqual(f.reads, []); assert.deepEqual(f.tested, []); assert.deepEqual(f.saved, []);
});
test('status lists only configuration metadata, never usernames or passwords', async () => {
  const f = fixture(); const response = await f.request({ operation: 'list' }); const text = await response.text();
  assert.equal(response.status, 200); assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal(JSON.parse(text).providers.length, 3);
  assert.ok(!text.includes('saved-user') && !text.includes('saved-secret'));
});
test('a failed candidate test preserves all active credentials and sanitizes the error', async () => {
  const f = fixture({ rejectTest: true }); const response = await f.request(candidate);
  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), { error: 'LOGIN_TEST_FAILED', preserved: true });
  assert.deepEqual(f.saved, []);
});
test('saves exactly the tested credential with actor and expected revision', async () => {
  const f = fixture(); const response = await f.request(candidate);
  assert.equal(response.status, 200);
  assert.equal(f.tested[0].login, 'new-user');
  assert.deepEqual(f.saved, [['offers', 'new-user', 'new-secret', 'master-uid', 7]]);
  assert.ok(!(await response.text()).includes('new-secret'));
});
test('testing the current access never saves or returns its credentials', async () => {
  const f = fixture(); const response = await f.request({ operation: 'test', provider: 'legacy', current: true });
  assert.equal(f.tested[0].login, 'saved-user'); assert.deepEqual(f.saved, []);
  assert.deepEqual(await response.json(), { ok: true });
});
test('rejects unknown providers and missing passwords without attempting a login', async () => {
  const f = fixture();
  assert.equal((await f.request({ ...candidate, provider: 'https://other.invalid' })).status, 400);
  assert.equal((await f.request({ ...candidate, password: '' })).status, 400);
  assert.deepEqual(f.tested, []);
});
test('does not overwrite a credential changed concurrently on another device', async () => {
  const f = fixture({ conflict: true }); const response = await f.request(candidate);
  assert.equal(response.status, 409);
  assert.deepEqual(await response.json(), { error: 'CREDENTIAL_CONFLICT', preserved: true });
});
