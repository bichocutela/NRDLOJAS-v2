#!/usr/bin/env node
/**
 * Trusted administrative publisher for public NRD metadata.
 * Requires Application Default Credentials with Firestore write permission.
 * Usage: node scripts/firebase/publish-snapshots.mjs stores stores.json
 *        node scripts/firebase/publish-snapshots.mjs encartes encartes.json
 *
 * This tool does NOT fetch supplier credentials, modify Supabase, or deploy rules.
 * Never run from an Android client or embed service-account credentials in the APK.
 */
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { initializeApp, applicationDefault, getApps } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');

const [kind, file] = process.argv.slice(2);
const targets = { stores: 'nordestao_stores', encartes: 'encartes_public' };
if (!Object.hasOwn(targets, kind) || !file) {
  console.error('Usage: node publish-snapshots.mjs stores|encartes <json-file>');
  process.exit(2);
}
const raw = await readFile(file, 'utf8');
const bytes = Buffer.byteLength(raw, 'utf8');
if (bytes > 250000) throw Error('Snapshot too large');
const data = JSON.parse(raw);
if (kind === 'stores') {
  if (!Array.isArray(data.stores) || data.stores.length > 100 ||
      typeof data.fetchedAt !== 'string') throw Error('Invalid stores snapshot');
  const ids = new Set();
  for (const store of data.stores) {
    if (!Number.isSafeInteger(store.id) || store.id <= 0 || ids.has(store.id) ||
        typeof store.name !== 'string' || !store.name.trim() ||
        !store.address || typeof store.address !== 'object' ||
        !Array.isArray(store.phones)) throw Error('Invalid store');
    ids.add(store.id);
  }
} else {
  if (!Array.isArray(data.tabloids) || data.tabloids.length > 200) throw Error('Invalid tabloids');
  for (const item of data.tabloids) {
    if (!Number.isSafeInteger(item.id) || item.id <= 0 ||
        typeof item.name !== 'string' ||
        typeof item.startDate !== 'string' || typeof item.endDate !== 'string') throw Error('Invalid tabloid');
    for (const [key, ext] of [['uri', '.pdf'], ['image', '.png']]) {
      const url = new URL(item[key]);
      if (url.protocol !== 'https:' || url.hostname !== 'datalakeidever.blob.core.windows.net' ||
          url.username || url.password || url.port || !url.pathname.toLowerCase().endsWith(ext)) {
        throw Error('Untrusted tabloid URL');
      }
    }
  }
}
if (!getApps().length) initializeApp({ credential: applicationDefault() });
const db = getFirestore();
const ref = db.collection('config').doc(targets[kind]);
await db.runTransaction(async tx => {
  const previous = await tx.get(ref);
  const revision = (previous.get('revision') || 0) + 1;
  tx.set(ref, { payload: raw, revision });
});
console.log('Published validated', kind, 'metadata:', bytes, 'bytes');
