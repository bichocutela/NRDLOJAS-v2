import {test} from 'node:test';
import assert from 'node:assert/strict';
import {SupabaseCache} from './supabase-cache.mjs';
test('Supabase cache batches reads and fences leases and stale writes',async()=>{
 const rows=new Map();let reads=0;
 const cache=new SupabaseCache({url:'https://example.test',key:'server-test-key',fetcher:async(url,options)=>{
  assert.equal(options.headers.Authorization,'Bearer server-test-key');
  if(options.method==='POST'){
   const p=JSON.parse(options.body),old=rows.get(p.p_key);
   if(p.p_mode==='insert'&&old || p.p_mode==='cas'&&String(old?.version)!==String(p.p_version))return Response.json([]);
   const row={key:p.p_key,payload:p.p_payload,lease:p.p_lease,version:(old?.version??0)+1};rows.set(p.p_key,row);return Response.json([row]);
  }
  reads++;const keys=new URL(url).searchParams.get('key').slice(4,-1).split(',');return Response.json(keys.flatMap(k=>rows.has(k)?[rows.get(k)]:[]));
 }});
 const claim=await cache.claim('shared');assert.ok(claim);assert.equal(await cache.claim('shared'),null);
 assert.ok(await cache.finish('shared',claim,{ready:true}));assert.equal(await cache.finish('shared',claim,{ready:false}),null);
 assert.equal((await cache.get('shared')).payload.ready,true);
 const before=reads;await cache.many(Array.from({length:201},(_,i)=>'key_'+i));assert.equal(reads-before,3);
 await assert.rejects(()=>cache.get('invalid/key'),/INVALID_CACHE_KEY/);
});
