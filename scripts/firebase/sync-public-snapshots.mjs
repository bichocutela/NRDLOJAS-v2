#!/usr/bin/env node
/**
 * Transitional read-only bridge: copy already-public store/encarte JSON
 * from legacy NRD endpoints to Firestore. No supplier passwords or Vault access.
 * Run only in a trusted environment with Firebase ADC and a public Supabase anon key.
 * Never delete the legacy endpoint or enable Android Firebase routing implicitly.
 */
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { initializeApp, applicationDefault, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

const base = process.env.NRD_LEGACY_PUBLIC_URL?.replace(/\/$/, '');
const key = process.env.NRD_LEGACY_ANON_KEY;
if (!base || !/^https:\/\//.test(base) || !key) throw Error('Missing secure bridge configuration');
if (!getApps().length) initializeApp({ credential: applicationDefault() });
const db = getFirestore();
const targets = [
  { path: 'nrd-stores', doc: 'nordestao_stores', array: 'stores', limit: 100 },
  { path: 'nrd-encartes', doc: 'encartes_public', array: 'tabloids', limit: 200 },
];
for (const target of targets) {
  const response = await fetch(base + '/functions/v1/' + target.path, {
    headers: { apikey: key, Authorization: 'Bearer ' + key, Accept: 'application/json' },
    signal: AbortSignal.timeout(45000),
  });
  if (!response.ok) throw Error(target.path + ': upstream HTTP ' + response.status);
  const raw = await response.text();
  if (Buffer.byteLength(raw, 'utf8') > 250000) throw Error(target.path + ': oversized snapshot');
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed[target.array]) || parsed[target.array].length > target.limit) {
    throw Error(target.path + ': invalid payload');
  }
  if (target.array === 'stores' && typeof parsed.fetchedAt !== 'string') throw Error('Invalid stores timestamp');
  if (target.array === 'tabloids') {
    for (const item of parsed.tabloids) {
      if (!Number.isSafeInteger(item.id) || item.id <= 0) throw Error('Invalid tabloid id');
      for (const [field, ext] of [['uri','.pdf'],['image','.png']]) {
        const url = new URL(item[field]);
        if (url.protocol !== 'https:' || url.hostname !== 'datalakeidever.blob.core.windows.net' ||
            url.username || url.password || url.port || !url.pathname.toLowerCase().endsWith(ext)) {
          throw Error('Untrusted tabloid asset');
        }
      }
    }
  }
  const ref = db.collection('config').doc(target.doc);
  const current = await ref.get();
  if (current.exists && current.get('payload') === raw) {
    console.log(target.doc + ': unchanged');
    continue;
  }
  await db.runTransaction(async tx => {
    const previous = await tx.get(ref);
    tx.set(ref, { payload: raw, revision: (previous.get('revision') || 0) + 1 });
  });
  console.log(target.doc + ': snapshot updated');
}
