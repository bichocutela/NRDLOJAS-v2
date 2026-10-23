import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import { storeCatalog } from './catalog.mjs';
const source = stripTypeScriptTypes(readFileSync(new URL('./index.ts', import.meta.url), 'utf8').replace("import { storeCatalog } from './catalog.mjs';", ''));
const raw = JSON.parse(readFileSync(new URL('../../../app/src/test/resources/stores/nordestao-observed-2026-10-23.json', import.meta.url), 'utf8'));
function harness(saved, upstreamOk = false) {
  let handler, stored;
  const tasks = [], errors = [];
  const context = vm.createContext({ Response, URLSearchParams, AbortSignal, storeCatalog,
    console: { error: (...args) => errors.push(args) },
    Deno: { env: { get: () => 'test' }, serve: fn => { handler = fn; } },
    EdgeRuntime: { waitUntil: task => tasks.push(task) },
    fetch: async (url, opts) => {
      if(url.includes('nrd_stores_cache')) {
        if(opts.method === 'POST') { stored = JSON.parse(opts.body).payload; return new Response(null, { status: 204 }); }
        return Response.json(saved ? [{ payload: saved }] : []);
      }
      if(url.includes('credentials')) return Response.json({ clientId: 'test', clientSecret: 'test', tenantName: 'nordestao' });
      if(url.includes('connect/token')) return Response.json({ access_token: 'test', expires_in: 3600 });
      if(!upstreamOk) throw new Error('private upstream failure');
      return Response.json(raw);
    } });
  vm.runInContext(source, context);
  return { run: () => handler({ method: 'GET' }), tasks, errors, stored: () => stored };
}
test('cold worker serves persisted stores even when official source is unavailable', async () => {
  const saved = { stores: storeCatalog(raw), fetchedAt: '2026-01-01T00:00:00.000Z' };
  const h = harness(saved);
  const response = await h.run();
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), saved);
  await Promise.all(h.tasks);
  assert.equal(h.errors[0][1], 'TIMEOUT_OR_NETWORK');
  assert.equal((await (await h.run()).json()).stores.length, 12);
  assert.equal(h.tasks.length, 1, 'failed source is backed off');
});
test('successful source refresh replaces and persists the entire snapshot', async () => {
  const h = harness({ stores: [], fetchedAt: '2026-01-01T00:00:00.000Z' }, true);
  await h.run(); await Promise.all(h.tasks);
  const next = await (await h.run()).json();
  assert.equal(next.stores.length, 12);
  assert.deepEqual(h.stored(), next);
});
test('no persisted snapshot and unavailable source returns retryable error', async () => {
  const h = harness(null);
  const response = await h.run();
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: 'STORES_UNAVAILABLE' });
});
