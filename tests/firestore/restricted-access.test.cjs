const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, updateDoc, collection, getDocs } = require('firebase/firestore');

(async () => {
  const env = await initializeTestEnvironment({ projectId: 'demo-nrd-access', firestore: { rules: fs.readFileSync(require('node:path').resolve(__dirname, '../../firestore.rules'), 'utf8'), host: '127.0.0.1', port: 8088 } });
  const grant = { login: 'ana', enabled: true, profile: true, promotions: false, prices: false };
  try {
    await env.withSecurityRulesDisabled(async ctx => {
      await setDoc(doc(ctx.firestore(), 'restricted_access/ana'), grant);
      await setDoc(doc(ctx.firestore(), 'restricted_access/bia'), { ...grant, login: 'bia', enabled: false });
      await setDoc(doc(ctx.firestore(), 'restricted_access/carla'), { ...grant, login: 'carla', profile: false, prices: true });
      await setDoc(doc(ctx.firestore(), 'work_schedules/2026-10'), { year: 2026, month: 10, employees: [] });
    });
    const master = env.authenticatedContext('master', { email: 'mestre@nrdlojas.com' }).firestore();
    const admin = env.authenticatedContext('admin', { email: 'admin@nrdlojas.com' }).firestore();
    const ana = env.authenticatedContext('ana', { email: 'ana@usuarios.nrdlojas.com' }).firestore();
    const bia = env.authenticatedContext('bia').firestore();
    const carla = env.authenticatedContext('carla').firestore();
    const publicDb = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(publicDb, 'restricted_access/ana')));
    await assertFails(getDoc(doc(publicDb, 'work_schedules/2026-10')));
    await assertSucceeds(getDoc(doc(ana, 'restricted_access/ana')));
    await assertFails(getDoc(doc(ana, 'restricted_access/bia')));
    await assertFails(getDocs(collection(ana, 'restricted_access')));
    await assertFails(updateDoc(doc(ana, 'restricted_access/ana'), { prices: true }));
    await assertFails(setDoc(doc(publicDb, 'restricted_access/new'), grant));
    await assertFails(setDoc(doc(admin, 'restricted_access/new'), grant));
    await assertSucceeds(setDoc(doc(master, 'restricted_access/new'), grant));
    await assertFails(setDoc(doc(master, 'restricted_access/bad'), { ...grant, password: 'never-store-passwords' }));
    await assertSucceeds(getDocs(collection(master, 'restricted_access')));
    await assertSucceeds(getDoc(doc(master, 'work_schedules/2026-10')));
    await assertSucceeds(getDoc(doc(ana, 'work_schedules/2026-10')));
    await assertFails(getDoc(doc(bia, 'work_schedules/2026-10')));
    await assertFails(getDoc(doc(carla, 'work_schedules/2026-10')));
    await assertSucceeds(updateDoc(doc(master, 'restricted_access/ana'), { enabled: false }));
    await assertFails(getDoc(doc(ana, 'work_schedules/2026-10')));
    await assertFails(setDoc(doc(publicDb, 'config/restricted_access'), { publicAccess: true }));
    await assertFails(setDoc(doc(admin, 'config/restricted_access'), { publicAccess: true }));
    await assertFails(setDoc(doc(master, 'config/restricted_access'), { publicAccess: 'true' }));
    await assertSucceeds(setDoc(doc(master, 'config/restricted_access'), { publicAccess: true }));
    await assertSucceeds(getDoc(doc(publicDb, 'work_schedules/2026-10')));
    await assertSucceeds(getDoc(doc(bia, 'work_schedules/2026-10')));
    await assertSucceeds(setDoc(doc(master, 'config/restricted_access'), { publicAccess: false }));
    await assertFails(getDoc(doc(publicDb, 'work_schedules/2026-10')));
    await assertFails(getDoc(doc(ana, 'work_schedules/2026-10')));
    console.log('PASS: 26 authorization checks (individual access, public toggle, revocation and schema).');
  } finally { await env.cleanup(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
