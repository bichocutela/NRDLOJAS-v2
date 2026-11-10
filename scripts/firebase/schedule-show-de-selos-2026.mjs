#!/usr/bin/env node
/**
 * Schedule the official (unchanged) NRD Show de Selos immersive Drive artworks.
 *
 * Only config/appSettings.appearanceThemeBackgrounds and appearanceRevision
 * may be modified. Existing theme backgrounds and all other app settings remain
 * untouched. Supports DRY_RUN=true for review before publishing.
 *
 * Auth: GitHub Actions ephemeral Google Application Default Credentials.
 */
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { initializeApp, applicationDefault, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

const PROJECT_ID = 'appcodigo-7f245';
const START_DATE = '2026-11-12';
const END_DATE = '2026-11-22';
const LABEL = 'Show de Selos — Imersivo — 12 a 22/11/2026';
const DRY_RUN = process.env.DRY_RUN !== 'false';

const assets = Object.freeze({
  multicolor: '1csV4W-AwJYMVSp8hN40rkGO9TKyi_qY8',
  red: '1YYz97EHBWL-RNA0KGb8FBiMVLNbXNcIh',
  gold: '1Mwz_9dwevzJxxZR01aT_6mjA2E3ASD5m',
  green: '151pTgH-9nHRJ1NdE7-APIO7jumGhkKsy',
  blue: '1i1sklZIWPhjTVbh9tl5b_haNh_VPPTl6',
  orange: '1It9-M8udUBMd7RBcxwW2nd6T5QZ5ngXs',
  glass: '1x02Xn7mhhcFAHGMwNiFzqVNIh_1PqamC',
  expressive: '14cIicCodIkTMBKbugE6hhpJgKsjrBG6d',
});

if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  throw Error('Set GOOGLE_APPLICATION_CREDENTIALS to an ephemeral service account file.');
}
const serviceAccount = JSON.parse(await readFile(process.env.GOOGLE_APPLICATION_CREDENTIALS, 'utf8'));
if (serviceAccount.project_id !== PROJECT_ID) {
  throw Error('Safety check: service account is not for the target Firebase project.');
}
if (!getApps().length) initializeApp({
  credential: applicationDefault(),
  projectId: PROJECT_ID,
});
const db = getFirestore();
const ref = db.collection('config').doc('appSettings');

function backgroundFor(theme, fileId) {
  return {
    id: 'nrd-show-de-selos-20261112-' + theme,
    label: LABEL,
    url: 'https://drive.google.com/uc?export=download&id=' + fileId,
    isActive: true,
    startDate: START_DATE,
    endDate: END_DATE,
    imageScale: 1,
    imageOffsetX: 0,
    imageOffsetY: 0,
    imageStretchX: 1,
    imageStretchY: 1,
  };
}
function plan(previous) {
  if (previous === null || previous === undefined) previous = {};
  if (typeof previous !== 'object' || Array.isArray(previous)) {
    throw Error('Existing theme backgrounds are not a Firestore map; refusing to overwrite.');
  }
  const proposed = { ...previous };
  let changes = 0;
  for (const [key, fileId] of Object.entries(assets)) {
    const existing = previous[key] ?? [];
    if (!Array.isArray(existing) || existing.some(x => !x || typeof x !== 'object')) {
      throw Error('Unexpected existing theme background entries for ' + key);
    }
    const item = backgroundFor(key, fileId);
    const already = existing.find(x => x.id === item.id);
    const hasChanged = !already || JSON.stringify(already) !== JSON.stringify(item);
    if (hasChanged) changes++;
    proposed[key] = [...existing.filter(x => x.id !== item.id), item];
  }
  return { proposed, changes };
}
const verify = async () => {
  const doc = await ref.get();
  if (!doc.exists) throw Error('config/appSettings missing after update.');
  const backgrounds = doc.get('appearanceThemeBackgrounds') ?? {};
  for (const [key, fileId] of Object.entries(assets)) {
    const item = backgrounds[key]?.find(x => x?.id === 'nrd-show-de-selos-20261112-' + key);
    if (!item || item.url !== backgroundFor(key, fileId).url ||
      item.startDate !== START_DATE || item.endDate !== END_DATE || item.isActive !== true) {
      throw Error('Readback failed for ' + key);
    }
  }
  console.log('VERIFIED 8 themes, November 12 to 22 inclusive.');
};

if (DRY_RUN) {
  const snapshot = await ref.get();
  if (!snapshot.exists) throw Error('config/appSettings missing.');
  const { proposed, changes } = plan(snapshot.get('appearanceThemeBackgrounds'));
  for (const key of Object.keys(assets)) {
    console.log('DRY RUN', key,
      'existing:', (snapshot.get('appearanceThemeBackgrounds')?.[key] ?? []).length,
      'after:', proposed[key].length);
  }
  console.log('DRY_RUN OK — changes:', changes, '(zero writes)');
} else {
  let changed = 0;
  await db.runTransaction(async tx => {
    const snapshot = await tx.get(ref);
    if (!snapshot.exists) throw Error('config/appSettings missing.');
    const { proposed, changes } = plan(snapshot.get('appearanceThemeBackgrounds'));
    changed = changes;
    if (!changes) return;
    const currentRevision = Number(snapshot.get('appearanceRevision') ?? 0);
    const revision = Math.max(Date.now(), currentRevision + 1);
    // Update only these two fields, not the rest of appSettings.
    tx.update(ref, {
      appearanceThemeBackgrounds: proposed,
      appearanceRevision: revision,
    });
  });
  await verify();
  console.log('PUBLISHED — updated themes:', changed);
}
