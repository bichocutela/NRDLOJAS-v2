import {test} from 'node:test';
import assert from 'node:assert/strict';
import {snapshot,diff,validSync,createPromotionSync} from './promotion-sync.mjs';
test('stable hashes ignore key order but detect price and same-count replacements',async()=>{
  const before=await snapshot([{id:1,description:'A',value:2},{id:2,description:'B',value:3}]);
  const reordered=await snapshot([{value:3,description:'B',id:2},{value:2,id:1,description:'A'}]);
  assert.equal(before.revision,reordered.revision);
  const after=await snapshot([{id:1,description:'A',value:1},{id:3,description:'C',value:3}]);
  const delta=diff(after.rows,before.rows);
  assert.deepEqual(delta.removedIds,['2']);
  assert.deepEqual(delta.items.map(r=>r.id),['1','3']);
  assert.equal(diff(before.rows,before.rows).items.length,0);
});
test('manifest validation rejects duplicate IDs, arbitrary bodies and malformed hashes',()=>{
  assert.ok(validSync({operation:'promotion_sync',revision:'',manifest:[]}));
  assert.ok(validSync({operation:'promotion_refresh',revision:'',manifest:[]}));
  assert.ok(validSync({operation:'promotion_status'}));
  const row={id:'x',hash:'a'.repeat(64)};
  assert.ok(!validSync({operation:'promotion_sync',revision:'',manifest:[row,row]}));
  assert.ok(!validSync({operation:'promotion_status',path:'Product/all'}));
  assert.ok(!validSync({operation:'promotion_sync',revision:'',manifest:[{...row,hash:'x'}]}));
});
test('shared snapshot avoids duplicate reads, survives failure and forced refresh bypasses TTL',async()=>{
  const docs=new Map(); let version=0, time=1000, calls=0; let current={id:1,value:2};
  const cache={get:async k=>docs.get(k),many:async keys=>new Map(keys.map(k=>[k,docs.get(k)])),
    write:async(k,payload)=>{const value={payload,version:++version};docs.set(k,value);return value;},
    claim:async k=>{const value={payload:docs.get(k)?.payload ?? {},version:++version};docs.set(k,value);return value;},
    finish:async(k,c,payload)=>cache.write(k,payload)};
  const sync=createPromotionSync({cache,now:()=>time,background:()=>{}});
  const readPage=async page=>{calls++;return {items:[current],pageIndex:page,totalPages:1,totalCount:1};};
  const first=await sync({input:{operation:'promotion_sync',manifest:[]},readPage});
  assert.equal(first.items.length,1); assert.equal(calls,1);
  await sync({input:{operation:'promotion_status'},readPage}); assert.equal(calls,1);
  time+=61000;
  const stale=await sync({input:{operation:'promotion_status'},readPage:async()=>{throw Error('offline');}});
  assert.equal(stale.revision,first.revision);
  current={id:1,value:3};
  const forced=await sync({input:{operation:'promotion_refresh',revision:first.revision,manifest:first.items.map(({id,hash})=>({id,hash}))},readPage});
  assert.equal(calls,2);
  assert.equal(forced.items.length,1);
  assert.notEqual(forced.revision,first.revision);
});
test('manual refresh waits for another instance to publish new products instead of returning old offers',async()=>{
 const before=await snapshot([{id:1,value:2}]);
 const after=await snapshot([{id:1,value:2},{id:2,value:3}]);
 let pointer={revision:before.revision,checkedAt:1000,count:1,pages:1,generation:'old'};
 let waits=0,reads=0;
 const cache={get:async()=>({payload:pointer}),claim:async()=>null,
  getFresh:async()=>({payload:pointer}),many:async keys=>new Map(keys.map(key=>[key,{payload:{rows:after.rows}}]))};
 const sync=createPromotionSync({cache,now:()=>2000,sleep:async()=>{
  if(++waits===2) pointer={revision:after.revision,checkedAt:2500,count:2,pages:1,generation:'new'};
 }});
 const result=await sync({input:{operation:'promotion_refresh',revision:before.revision,manifest:before.rows.map(({id,hash})=>({id,hash}))},
  readPage:async()=>{reads++;throw Error('duplicate scan');}});
 assert.equal(reads,0);assert.equal(waits,2);assert.equal(result.revision,after.revision);
 assert.deepEqual(result.items.map(row=>row.id),['2']);
});
test('manual refresh with a stalled remote lease fails rather than claiming a fresh no-change result',async()=>{
 const before=await snapshot([{id:1,value:2}]);let waits=0;
 const cache={get:async()=>({payload:{revision:before.revision,checkedAt:1000,count:1,pages:1,generation:'old'}}),claim:async()=>null};
 const sync=createPromotionSync({cache,now:()=>2000,sleep:async()=>{waits++;}});
 await assert.rejects(()=>sync({input:{operation:'promotion_refresh',revision:before.revision,manifest:[]},readPage:async()=>{throw Error('duplicate');}}),/REFRESH_BUSY/);
 assert.equal(waits,7);
});
