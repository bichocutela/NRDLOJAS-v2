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
  assert.ok(validSync({operation:'promotion_status'}));
  const row={id:'x',hash:'a'.repeat(64)};
  assert.ok(!validSync({operation:'promotion_sync',revision:'',manifest:[row,row]}));
  assert.ok(!validSync({operation:'promotion_status',path:'Product/all'}));
  assert.ok(!validSync({operation:'promotion_sync',revision:'',manifest:[{...row,hash:'x'}]}));
});
test('shared snapshot serves unchanged checks without another ACP download and keeps complete old data on failure',async()=>{
  const docs=new Map(); let version=0, time=1000, calls=0; const pending=[];
  const cache={get:async k=>docs.get(k),many:async keys=>new Map(keys.map(k=>[k,docs.get(k)])),
    write:async(k,payload)=>{const value={payload,version:++version};docs.set(k,value);return value;},
    claim:async k=>{const value={payload:docs.get(k)?.payload ?? {},version:++version};docs.set(k,value);return value;},
    finish:async(k,c,payload)=>cache.write(k,payload)};
  const sync=createPromotionSync({cache,now:()=>time,background:p=>pending.push(p)});
  const readPage=async page=>{calls++;return {items:[{id:1,value:2}],pageIndex:page,totalPages:1,totalCount:1};};
  const first=await sync({input:{operation:'promotion_sync',manifest:[]},readPage});
  assert.equal(first.items.length,1); assert.equal(calls,1);
  await sync({input:{operation:'promotion_status'},readPage}); assert.equal(calls,1);
  time+=61000;
  const stale=await sync({input:{operation:'promotion_status'},readPage:async()=>{throw Error('offline');}});
  await Promise.all(pending);
  assert.equal(stale.revision,first.revision);
  assert.equal((await sync({input:{operation:'promotion_sync',manifest:[]},readPage})).items.length,1);
});
