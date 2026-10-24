import { offerCatalog } from './catalog.mjs';

export function portalOfferCatalog(campaigns) {
  if (!Array.isArray(campaigns)) throw new Error('INVALID_CATALOG');
  return offerCatalog({ promotions: campaigns.map(campaign => ({ ...campaign,
    items: Array.isArray(campaign.items) ? campaign.items.map(item => ({ ...item,
      codeErp: item.codeErp || item.skuNumber || item.productId,
      indexibirPrecoBase: item.showBasePrice ?? item.indexibirPrecoBase,
      indexibirPercDesconto: item.showDiscountPercentage ?? item.indexibirPercDesconto
    })) : campaign.items
  })) }).map(offer => ({ ...offer, conditions: [offer.conditions,
    'Confira sua elegibilidade no Nordestão Pra Você. Ofertas que exigem ativação devem ser ativadas na sua própria conta antes da compra.'
  ].filter(Boolean).join('\n\n') }));
}

export function createPortalOffers({ session, lock, beforeQuery = async () => {}, now = Date.now, ownerId = () => crypto.randomUUID() }) {
  return async storeId => {
    const owner = ownerId(), acquired = await lock(owner, 'acquire');
    if (!acquired?.acquired) throw new Error('PORTAL_BUSY');
    const started = now(), deadline = started + 105000, workDeadline = started + 60000;
    let context = acquired.originalStoreId && acquired.selectedStoreId ?
      { original: acquired.originalStoreId, selected: acquired.selectedStoreId } : null;
    async function check(restoring = false) {
      if (now() >= (restoring ? deadline : workDeadline) || !(await lock(owner, 'check'))?.acquired) throw new Error('PORTAL_LEASE_LOST');
    }
    async function preferred(restoring = false) {
      await check(restoring);
      const value = await session.get('/store/preferential');
      if (!Number.isSafeInteger(value?.id) || value.id <= 0) throw new Error('PORTAL_STORE_INVALID');
      return value.id;
    }
    async function clear() {
      if (!(await lock(owner, 'clear'))?.acquired) throw new Error('PORTAL_LEASE_LOST');
      context = null;
    }
    async function restore() {
      if (!context) return;
      const current = await preferred(true);
      // Preserve a newer preference changed outside this integration.
      if (current === context.selected && current !== context.original) {
        await check(true);
        await session.selectStore(context.original);
        if (await preferred(true) !== context.original) throw new Error('PORTAL_RESTORE_FAILED');
      }
      await clear();
    }
    try {
      await beforeQuery();
      // Recover an interrupted previous worker before selecting a new store.
      await restore();
      const original = await preferred();
      if (original !== storeId) {
        if (!(await lock(owner, 'mark', original, storeId))?.acquired) throw new Error('PORTAL_LEASE_LOST');
        context = { original, selected: storeId };
        await check();
        await session.selectStore(storeId);
      }
      if (await preferred() !== storeId) throw new Error('PORTAL_STORE_MISMATCH');
      await check();
      const campaigns = await session.get('/promotion/customer');
      if (await preferred() !== storeId) throw new Error('PORTAL_STORE_MISMATCH');
      return { storeId, offers: portalOfferCatalog(campaigns) };
    } finally {
      try { await restore(); }
      finally { await lock(owner, 'release'); }
    }
  };
}
