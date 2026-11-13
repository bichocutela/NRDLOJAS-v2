import {test} from 'node:test';
import assert from 'node:assert/strict';
import {SupabaseCache} from './supabase-cache.mjs';
test('Supabase cache batches reads and fences leases and stale writes',async()=>{
 const rows=new Map();let reads=0;
 const cache=new SupabaseCache({url:'https://example.test',key:'server-test-key',fetcher:async(url,options)=>{
  assert.equal(options.headers.Authorization,'Bearer server-test-key');
  if(options.method==='POST'){
   assert.equal(new URL(url).searchParams.get('select'),'key,lease,version');
   const p=JSON.parse(options.body),old=rows.get(p.p_key);
   if(p.p_mode==='insert'&&old || p.p_mode==='cas'&&String(old?.version)!==String(p.p_version))return Response.json([]);
   const row={key:p.p_key,payload:p.p_payload,lease:p.p_lease,version:(old?.version??0)+1};rows.set(p.p_key,row);return Response.json([{key:row.key,lease:row.lease,version:row.version}]);
  }
  reads++;const keys=new URL(url).searchParams.get('key').slice(4,-1).split(',');return Response.json(keys.flatMap(k=>rows.has(k)?[rows.get(k)]:[]));
 }});
 const claim=await cache.claim('shared');assert.ok(claim);assert.equal(await cache.claim('shared'),null);
 assert.deepEqual((await cache.finish('shared',claim,{ready:true})).payload,{ready:true});assert.equal(await cache.finish('shared',claim,{ready:false}),null);
 assert.equal((await cache.get('shared')).payload.ready,true);
 const before=reads;await cache.many(Array.from({length:201},(_,i)=>'key_'+i));assert.equal(reads-before,3);
 await assert.rejects(()=>cache.get('invalid/key'),/INVALID_CACHE_KEY/);
});

test('concurrent reads of the same key share one request without retaining stale values',async()=>{
 let calls=0,value=1;
 const cache=new SupabaseCache({url:'https://example.test',key:'server-test-key',fetcher:async()=>{
  calls++;await new Promise(resolve=>setTimeout(resolve,5));
  return Response.json([{key:'shared',payload:{value},lease:0,version:value}]);
 }});
 const [a,b,c]=await Promise.all([cache.get('shared'),cache.get('shared'),cache.get('shared')]);
 assert.equal(calls,1);assert.equal(a.payload.value,1);assert.deepEqual(a,b);assert.deepEqual(b,c);
 value=2;assert.equal((await cache.get('shared')).payload.value,2);assert.equal(calls,2);
});
