import {test} from 'node:test';
import assert from 'node:assert/strict';
import {snapshot,diff,validSync,createPromotionSync,retainCatalogMetadata} from './promotion-sync.mjs';
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
  const jobs=[];const sync=createPromotionSync({cache,now:()=>time,background:job=>jobs.push(job)});
  const readPage=async page=>{calls++;return {items:[current],pageIndex:page,totalPages:1,totalCount:1};};
  const first=await sync({input:{operation:'promotion_sync',manifest:[]},readPage});
  assert.equal(first.items.length,1); assert.equal(calls,1);
  await sync({input:{operation:'promotion_status'},readPage}); assert.equal(calls,1);
  time+=61000;
  await assert.rejects(()=>sync({input:{operation:'promotion_status'},readPage:async()=>{throw Error('offline');}}),/offline/);
  await Promise.all(jobs);
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


test('refresh preserves verified catalog metadata while updating price and rejects changed EAN',()=>{
 const previous={code:'2021000',barCode:'7891149840878',value:5,imageUrl:'https://official.test/image.jpg',catalog_match_type:'internal_code',nrdCategory:'Bebidas alcoólicas',catalog_category_source:'official'};
 const fresh={code:previous.code,barCode:previous.barCode,value:4,nrdCategory:'Bebidas'};
 const result=retainCatalogMetadata(fresh,previous);
 assert.equal(result.value,4);assert.equal(result.imageUrl,previous.imageUrl);assert.equal(result.nrdCategory,'Bebidas alcoólicas');
 assert.equal(retainCatalogMetadata({...fresh,barCode:'other'},previous).imageUrl,undefined);
});

test('snapshot enriches once after all ACP pages have been read',async()=>{
 const docs=new Map();let version=0,pages=0,enrichments=0;
 const cache={get:async k=>docs.get(k),many:async keys=>new Map(keys.map(k=>[k,docs.get(k)])),write:async(k,payload)=>{const row={payload,version:++version};docs.set(k,row);return row;},claim:async k=>cache.write(k,{}),finish:async(k,c,p)=>cache.write(k,p)};
 const sync=createPromotionSync({cache});
 const result=await sync({input:{operation:'promotion_sync',manifest:[]},readPage:async page=>{pages++;return {items:[{id:page}],pageIndex:page,totalPages:3,totalCount:3};},enrichItems:async items=>{assert.equal(pages,3);enrichments++;return items.map(item=>({...item,nrdCategory:'Alimentos'}));}});
 assert.equal(enrichments,1);assert.equal(result.items.length,3);assert.ok(result.items.every(row=>row.item.nrdCategory==='Alimentos'));
});

test('an expired status waits for ACP removal even if validity is still in the future',async()=>{
 const docs=new Map();let time=1000,version=0;
 const cache={get:async k=>docs.get(k),many:async keys=>new Map(keys.map(k=>[k,docs.get(k)])),
 write:async(k,payload)=>{const row={payload,version:++version};docs.set(k,row);return row;},claim:async k=>cache.write(k,docs.get(k)?.payload??{}),finish:async(k,c,p)=>cache.write(k,p)};
 const sync=createPromotionSync({cache,now:()=>time});
 const before=await sync({input:{operation:'promotion_sync',manifest:[]},readPage:async()=>({items:[{id:'adria',endDate:'2099-12-31',quantityTake:3,quantityPay:2}],pageIndex:0,totalCount:1,totalPages:1})});
 time+=60000;
 const after=await sync({input:{operation:'promotion_sync',manifest:before.items},readPage:async()=>({items:[],pageIndex:0,totalCount:0,totalPages:0}),enrichItems:async()=>{throw Error('image service offline');}});
 assert.equal(after.count,0);assert.deepEqual(after.removedIds,['adria']);assert.equal(after.checkedAt,time);
});

test('optional enrichment failure cannot hold a withdrawn promotion in the published snapshot',async()=>{
 const docs=new Map();let v=0;
 const cache={get:async k=>docs.get(k),many:async keys=>new Map(keys.map(k=>[k,docs.get(k)])),write:async(k,payload)=>{const row={payload,version:++v};docs.set(k,row);return row;},claim:async k=>cache.write(k,docs.get(k)?.payload??{}),finish:async(k,c,p)=>cache.write(k,p)};
 const sync=createPromotionSync({cache});
 const read=items=>async()=>({items,pageIndex:0,totalCount:items.length,totalPages:1});
 const before=await sync({input:{operation:'promotion_sync',manifest:[]},readPage:read([{id:1,value:2},{id:2,value:3}])});
 const after=await sync({input:{operation:'promotion_refresh',manifest:before.items},readPage:read([{id:1,value:2}]),enrichItems:async()=>{throw Error('offline');}});
 assert.deepEqual(after.removedIds,['2']);assert.equal(after.count,1);
});

test('large snapshots keep all ACP products and rotate bounded enrichment batches',async()=>{
 const docs=new Map();let version=0;const seen=new Set();const products=Array.from({length:503},(_,id)=>({id,value:2}));
 const cache={get:async k=>docs.get(k),many:async keys=>new Map(keys.map(k=>[k,docs.get(k)])),write:async(k,payload)=>{const row={payload,version:++version};docs.set(k,row);return row;},claim:async k=>cache.write(k,docs.get(k)?.payload ?? {}),finish:async(k,c,p)=>cache.write(k,p),remove:async k=>docs.delete(k)};
 const sync=createPromotionSync({cache});
 const args={readPage:async page=>({items:products.slice(page*250,(page+1)*250),pageIndex:page,totalPages:3,totalCount:503}),enrichItems:async batch=>{assert.equal(batch.length,80);batch.forEach(item=>seen.add(item.id));return batch;}};
 for(let i=0;i<7;i++){const result=await sync({...args,input:{operation:'promotion_refresh',manifest:[]}});assert.equal(result.count,503);assert.equal(result.items.length,503);}
 assert.equal(seen.size,503);
});

test('official images survive refresh for De/Por, Clube and Leve e Pague products',()=>{
 for(const family of ['depor','club','takepay']){
  const old={code:family,barCode:'7891149840878',imageUrl:'https://produto-assets-vipcommerce-com-br.br-se1.magaluobjects.com/250x250/photo.jpg',catalog_match_type:'internal_code'};
  const next=retainCatalogMetadata({code:family,barCode:old.barCode,value:9},old);
  assert.equal(next.imageUrl,old.imageUrl);
 }
});

test('repeated upstream failures back off, preserve the snapshot, and manual recovery resets the delay',async()=>{
 const before=await snapshot([{id:1,value:2}]);let time=100000,calls=0;
 const docs=new Map([['promotion_snapshot_v3',{payload:{revision:before.revision,checkedAt:1,count:1,pages:1,generation:'old'}}],['snapshot_old_0',{payload:{rows:before.rows}}]]);
 const cache={get:async k=>docs.get(k),many:async keys=>new Map(keys.map(k=>[k,docs.get(k)])),
  claim:async k=>docs.get(k),finish:async(k,c,payload)=>{const row={payload};docs.set(k,row);return row;}};
 const sync=createPromotionSync({cache,now:()=>time});
 const offline=async()=>{calls++;throw Error('offline');};
 await assert.rejects(()=>sync({input:{operation:'promotion_status'},readPage:offline}),/offline/);
 assert.equal(calls,1);assert.equal(docs.get('promotion_snapshot_v3').payload.retryAt,time+60000);
 time+=60000;
 await assert.rejects(()=>sync({input:{operation:'promotion_status'},readPage:offline}),/offline/);
 assert.equal(calls,2);assert.equal(docs.get('promotion_snapshot_v3').payload.retryAt,time+120000);
 time+=60000;
 await assert.rejects(()=>sync({input:{operation:'promotion_status'},readPage:offline}),/SYNC_NOT_CONFIRMED/);
 assert.equal(calls,2);
 await sync({input:{operation:'promotion_refresh',manifest:[]},readPage:async()=>({items:[{id:1,value:2}],pageIndex:0,totalCount:1,totalPages:1})});
 assert.equal(docs.get('promotion_snapshot_v3').payload.retryAt,0);
 assert.equal(docs.get('promotion_snapshot_v3').payload.failureCount,0);
});
