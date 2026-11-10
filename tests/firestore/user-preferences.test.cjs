const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, getDocs, collection, serverTimestamp } = require('firebase/firestore');

(async () => {
 const env = await initializeTestEnvironment({
   projectId: 'demo-nrd-user-preferences',
   firestore: { rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8'), host: '127.0.0.1', port: 8088 }
 });
 try {
  const alice = env.authenticatedContext('alice').firestore();
  const bob = env.authenticatedContext('bob').firestore();
  const guest = env.unauthenticatedContext().firestore();
  const target = doc(alice, 'user_preferences/alice');
  const valid = { favoriteStoreCode: '0012', appTheme: 'multicolor', notificationsEnabled: true, updatedAt: serverTimestamp() };
  await assertSucceeds(setDoc(target, valid));
  await assertSucceeds(getDoc(target));
  await assertFails(getDoc(doc(bob, 'user_preferences/alice')));
  await assertFails(getDoc(doc(guest, 'user_preferences/alice')));
  await assertFails(getDocs(collection(alice, 'user_preferences')));
  await assertFails(setDoc(doc(bob, 'user_preferences/alice'), valid));
  await assertFails(setDoc(target, { ...valid, accessToken: 'secret' }));
  await assertFails(setDoc(target, { ...valid, updatedAt: 1 }));
 } finally { await env.cleanup(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
