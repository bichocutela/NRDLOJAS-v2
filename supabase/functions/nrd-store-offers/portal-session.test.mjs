import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPortalSession } from './portal-session.mjs';

function fixture({ readStatuses = [200], loginStatus = 200, loginFlags = {} } = {}) {
  let time = Date.parse('2026-10-24T10:00:00Z'), logins = 0;
  const reads = [];
  const session = createPortalSession({ now: () => time,
    getCredentials: async () => ({ user: 'test-user', password: 'test-password' }),
    getTenantToken: async () => 'test-tenant-token',
    fetchImpl: async (url, options) => {
      if (String(url).endsWith('/auth/person')) {
        logins++;
        assert.equal(options.method, 'POST');
        assert.equal(options.body.get('user'), 'test-user');
        assert.equal(options.body.get('password'), 'test-password');
        return Response.json({ access_token: `test-session-${logins}`,
          expiration: new Date(time + 3600000).toISOString(), ...loginFlags }, { status: loginStatus });
      }
      reads.push({ url: String(url), ...options });
      return Response.json({ items: [] }, { status: readStatuses.shift() ?? 200 });
    }
  });
  return { session, reads, get logins() { return logins; }, advance(ms) { time += ms; } };
}

test('shares one internal login and sends both required headers without URL credentials', async () => {
  const f = fixture();
  await Promise.all(Array.from({ length: 10 }, () => f.session.get('/v2.0/Promotion/customer', { page: 1 })));
  assert.equal(f.logins, 1);
  assert.ok(f.reads.every(read => read.method === 'GET' && read.headers.tokenAuth === 'test-tenant-token' &&
    read.headers.Authorization === 'Bearer test-session-1' && !read.url.includes('test-user')));
});

test('automatically reconnects before expiration', async () => {
  const f = fixture();
  await f.session.get('/v2.0/Promotion/customer');
  f.advance(3600000);
  await f.session.get('/v2.0/Promotion/customer');
  assert.equal(f.logins, 2);
});

test('a rejected session reconnects once, never in a loop', async () => {
  const f = fixture({ readStatuses: [401, 401] });
  await assert.rejects(f.session.get('/v2.0/Promotion/customer'), /^Error: PORTAL_SOURCE_UNAVAILABLE$/);
  await assert.rejects(f.session.get('/v2.0/Promotion/customer'), /^Error: PORTAL_RECONNECT_DEFERRED$/);
  assert.equal(f.logins, 2); assert.equal(f.reads.length, 2);
});

test('an authentication failure is sanitized and pauses subsequent logins', async () => {
  const f = fixture({ loginStatus: 403 });
  await assert.rejects(f.session.get('/v2.0/Promotion/customer'), /^Error: PORTAL_AUTH_UNAVAILABLE$/);
  await assert.rejects(f.session.get('/v2.0/Promotion/customer'), /^Error: PORTAL_RECONNECT_DEFERRED$/);
  assert.equal(f.logins, 1);
});

test('does not accept terms, choose a store, activate offers or touch profile routes', async () => {
  const f = fixture({ loginFlags: { need_update_terms: true } });
  await assert.rejects(f.session.get('/v2.0/Promotion/customer'), /^Error: PORTAL_AUTH_UNAVAILABLE$/);
  assert.equal(f.reads.length, 0);
  for (const path of ['/promotion/activate/all', '/person', '/store/preferential/12',
    '/v2.0/Promotion/customer?redirect=https://other.test', '//other.test']) {
    await assert.rejects(f.session.get(path), /^Error: PORTAL_ROUTE_NOT_ALLOWED$/);
  }
});
