import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createOfferFailover } from './failover.mjs';
import { activeOffers } from './catalog.mjs';

const catalog = storeId => ({ storeId, offers: [{ id: String(storeId),
  startsAt: '2026-10-24T00:00:00Z', endsAt: '2026-10-24T12:00:00Z' }],
  account: { secret: 'must-not-cross-boundary' } });
const down = async () => { throw new Error('upstream body containing private data'); };

test('uses primary without logging in and strips account metadata', async () => {
  let backupCalls = 0;
  const gateway = createOfferFailover({ primary: async id => catalog(id),
    backup: async id => { backupCalls++; return catalog(id); } });
  const value = await gateway.get(12);
  assert.equal(value.source, 'primary');
  assert.equal(value.account, undefined);
  assert.equal(backupCalls, 0);
});

test('falls back, coalesces concurrent requests and returns to the recovered primary', async () => {
  let time = Date.parse('2026-10-24T10:00:00Z'), healthy = false, primaryCalls = 0, backupCalls = 0;
  const gateway = createOfferFailover({ now: () => time,
    primary: async id => { primaryCalls++; if (!healthy) return down(); return catalog(id); },
    backup: async id => { backupCalls++; return catalog(id); } });
  const values = await Promise.all(Array.from({ length: 20 }, () => gateway.get(12)));
  assert.ok(values.every(value => value.source === 'authenticated'));
  assert.equal(primaryCalls, 1); assert.equal(backupCalls, 1);
  time += 59000;
  await gateway.get(12);
  assert.equal(backupCalls, 1);
  healthy = true; time += 1001;
  assert.equal((await gateway.get(12)).source, 'primary');
  assert.equal(primaryCalls, 2); assert.equal(backupCalls, 1);
});

test('keeps each store isolated and rejects a provider returning another store', async () => {
  const gateway = createOfferFailover({ primary: down,
    backup: async id => catalog(id === 9 ? 12 : id) });
  assert.equal((await gateway.get(12)).storeId, 12);
  await assert.rejects(gateway.get(9), /^Error: OFFERS_UNAVAILABLE$/);
  await assert.rejects(gateway.get('12'), /^Error: INVALID_STORE$/);
});

test('both sources failing preserve the previous timestamp, mark stale and back off', async () => {
  let time = Date.parse('2026-10-24T11:59:00Z'), healthy = true, calls = 0;
  const gateway = createOfferFailover({ now: () => time, cacheMs: 1000,
    primary: async id => { calls++; return healthy ? catalog(id) : down(); }, backup: down });
  const original = await gateway.get(12);
  healthy = false; time += 120000;
  const stale = await gateway.get(12);
  assert.equal(stale.stale, true);
  assert.equal(stale.fetchedAt, original.fetchedAt);
  assert.equal(activeOffers(stale.offers, time).length, 0);
  assert.equal((await gateway.get(12)).stale, true);
  assert.equal(calls, 2);
});

test('an empty valid catalog succeeds without resurrecting an old offer', async () => {
  const gateway = createOfferFailover({ primary: async storeId => ({ storeId, offers: [] }), backup: down });
  assert.deepEqual((await gateway.get(12)).offers, []);
});
