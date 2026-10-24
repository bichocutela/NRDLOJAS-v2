import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPortalOffers, portalOfferCatalog } from './portal-catalog.mjs';

const campaigns = [{ id: 1, status: 'A', type: 'A', startDate: '2026-10-24T00:00:00',
  endDate: '2026-10-25T23:59:59', items: [{ id: 2, description: 'Produto oficial',
    skuNumber: '2000001', price: 2, originalPrice: 4, images: ['https://official.test/product.jpg'],
    showBasePrice: false, showDiscountPercentage: false, isActivated: true }] }];

function fixture({ initial = 12, dirty = null, failCatalog = false, externalChange = false,
  failRestore = false, busy = false } = {}) {
  let current = initial, context = dirty, released = false;
  const selects = [], requested = [];
  const get = createPortalOffers({ ownerId: () => 'fake-owner',
    lock: async (_owner, operation, original, selected) => {
      if (operation === 'acquire') return { acquired: !busy,
        originalStoreId: context?.original, selectedStoreId: context?.selected };
      if (operation === 'mark') context = { original, selected };
      if (operation === 'clear') context = null;
      if (operation === 'release') released = true;
      return { acquired: true };
    },
    session: {
      selectStore: async id => {
        selects.push(id);
        if (failRestore && id === 12) throw new Error('network outage');
        current = id;
      },
      get: async path => {
        if (path === '/store/preferential') return { id: current };
        requested.push(current);
        if (externalChange) current = 99;
        if (failCatalog) throw new Error('catalog unavailable');
        return campaigns;
      }
    }
  });
  return { get, selects, requested, get current() { return current; },
    get context() { return context; }, get released() { return released; } };
}

test('normalizes official prices and images while dropping customer activation state', () => {
  const [offer] = portalOfferCatalog(campaigns);
  assert.equal(offer.code, '2000001');
  assert.equal(offer.image, 'https://official.test/product.jpg');
  assert.equal(offer.discount, 0);
  assert.equal(offer.isActivated, undefined);
  assert.match(offer.conditions, /sua própria conta/);
  assert.throws(() => portalOfferCatalog({ items: [] }));
});

test('selects the requested store, verifies it and restores the original preference', async () => {
  const f = fixture();
  assert.equal((await f.get(9)).storeId, 9);
  assert.deepEqual(f.requested, [9]);
  assert.deepEqual(f.selects, [9, 12]);
  assert.equal(f.current, 12); assert.equal(f.context, null); assert.equal(f.released, true);
});

test('does not change the account when the requested store is already selected', async () => {
  const f = fixture(); await f.get(12);
  assert.deepEqual(f.selects, []);
});

test('restores the preference even if the catalog fails', async () => {
  const f = fixture({ failCatalog: true });
  await assert.rejects(f.get(9));
  assert.equal(f.current, 12); assert.equal(f.context, null); assert.equal(f.released, true);
});

test('recovers a killed worker before starting another store query', async () => {
  const f = fixture({ initial: 9, dirty: { original: 12, selected: 9 } });
  await f.get(16);
  assert.deepEqual(f.selects, [12, 16, 12]);
  assert.deepEqual(f.requested, [16]);
  assert.equal(f.current, 12);
});

test('does not overwrite a new external preference or publish a mismatched catalog', async () => {
  const f = fixture({ externalChange: true });
  await assert.rejects(f.get(9), /^Error: PORTAL_STORE_MISMATCH$/);
  assert.equal(f.current, 99); assert.deepEqual(f.selects, [9]);
});

test('a failed restore preserves the recovery context and never publishes the catalog', async () => {
  const f = fixture({ failRestore: true });
  await assert.rejects(f.get(9));
  assert.deepEqual(f.context, { original: 12, selected: 9 });
  assert.equal(f.released, true);
});

test('a busy global lease does not access the account', async () => {
  const f = fixture({ busy: true });
  await assert.rejects(f.get(9), /^Error: PORTAL_BUSY$/);
  assert.deepEqual(f.selects, []); assert.deepEqual(f.requested, []);
});
