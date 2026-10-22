import {test} from 'node:test';
import assert from 'node:assert/strict';
import {ReadCache} from './read-cache.mjs';
import {ServerCache} from './server-cache.mjs';

test('20 repeated category scans read each completed classification only once', async () => {
  let reads = 0, time = 0;
  const keys = Array.from({length:250}, (_, i) => `category_${i}`);
  const store = {many:async keys => { reads += keys.length; return new Map(keys.map(k => [k, {payload:{category:'Mercearia'}}])); }};
  const cache = new ReadCache(store, {now:() => time});
  for (let cycle = 0; cycle < 20; cycle++) { time += 60_000; assert.equal((await cache.many(keys)).size, 250); }
  assert.equal(reads, 250); // Previous pipeline: 5,000 document reads.
});

test('overlapping calls share pending reads, callers cannot mutate cached values', async () => {
  let reads = 0;
  const cache = new ReadCache({many:async keys => { reads += keys.length; return new Map(keys.map(k => [k, {payload:{rows:[{id:'1'}]}}])); }});
  const [a, b] = await Promise.all([cache.many(['snapshot_a_0']), cache.many(['snapshot_a_0'])]);
  a.get('snapshot_a_0').payload.rows.pop();
  assert.equal(b.get('snapshot_a_0').payload.rows.length, 1);
  assert.equal((await cache.get('snapshot_a_0')).payload.rows.length, 1);
  assert.equal(reads, 1);
});

test('missing classifications and mutable pointer expire; generation pages are reused', async () => {
  let time = 0, reads = 0, category = null;
  const cache = new ReadCache({many:async keys => {
    reads += keys.length;
    return new Map(keys.map(k => [k, k.startsWith('category_') ? category : {payload:{revision:'r',rows:[]}}]).filter(([,v]) => v));
  }}, {now:() => time});
  await cache.many(['category_a','promotion_snapshot','snapshot_a_0']);
  assert.equal(reads, 3);
  time = 10_001; await cache.many(['category_a','promotion_snapshot','snapshot_a_0']); assert.equal(reads, 4);
  category = {payload:{category:'Bebidas'}};
  time = 60_001; assert.equal((await cache.get('category_a')).payload.category, 'Bebidas'); assert.equal(reads, 5);
});

test('failed requests are not retained and bounded memory evicts old entries', async () => {
  let fails = true;
  const cache = new ReadCache({many:async keys => {
    if (fails) throw Error('offline');
    return new Map(keys.map(k => [k,{payload:{category:'Bebidas'}}]));
  }}, {maxEntries:2});
  await assert.rejects(cache.get('category_a'));
  fails = false;
  await cache.many(['category_a','category_b','category_c']);
  assert.equal(cache.entries.size, 2);
  assert.equal(cache.pending.size, 0);
});

test('distributed lease and stale-write fencing are preserved through read cache', async () => {
  let document = null, revision = 0;
  const backing = new ServerCache({project:'test', token:async () => 'synthetic', fetcher:async (url, options) => {
    if (options.method === 'POST') return Response.json(document ? [{found:document}] : []);
    if (options.method !== 'PATCH') return document ? Response.json(document) : new Response('',{status:404});
    const query = new URL(url).searchParams;
    if (query.get('currentDocument.exists') === 'false' && document) return new Response('',{status:409});
    if (query.has('currentDocument.updateTime') && query.get('currentDocument.updateTime') !== document?.updateTime) return new Response('',{status:412});
    document = {...JSON.parse(options.body), name:'test/category_a', updateTime:String(++revision)};
    return Response.json(document);
  }});
  const cache = new ReadCache(backing);
  const claims = await Promise.all([cache.claim('category_a'), cache.claim('category_a')]);
  assert.equal(claims.filter(Boolean).length, 1);
  const winner = claims.find(Boolean);
  assert.equal(await cache.claim('category_a'), null);
  await cache.finish('category_a', winner, {category:'Bebidas'});
  assert.equal((await cache.get('category_a')).payload.category, 'Bebidas');
  assert.equal(await cache.finish('category_a', winner, {category:'Limpeza'}), null);
  assert.equal((await cache.get('category_a')).payload.category, 'Bebidas');
});

test('quota exhaustion pauses remote requests rather than retrying every product', async () => {
  let calls = 0;
  const cache = new ServerCache({project:'test', token:async () => 'synthetic', fetcher:async () => { calls++; return new Response('', {status:429}); }});
  await assert.rejects(cache.get('a'), /CACHE_RATE_LIMITED/);
  await assert.rejects(cache.get('b'), /CACHE_RATE_LIMITED/);
  assert.equal(calls, 1);
});
