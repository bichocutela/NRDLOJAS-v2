import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ControlDocuments } from './documents.mjs';

test('only public provider/store configuration is cached; auth and validity stay live', async () => {
  let now=100000, reads=0;
  const api=async (url,opts={})=>{
    if(opts.method==='POST') return {ok:true,json:async()=>[{version:2}]};
    reads++;
    return {ok:true,json:async()=>[{path:new URL(url).searchParams.get('path').slice(3),fields:{reads},version:reads,deleted:false}]};
  };
  const docs=new ControlDocuments({url:'https://test.supabase.co',key:'local',fetcher:api,now:()=>now,configCacheMs:15000});
  const one=await docs.get('config/acpProvider');
  const two=await docs.get('config/acpProvider');
  assert.deepEqual(one,two);
  assert.equal(reads,1);
  await docs.get('config/promotion_stores');
  await docs.get('config/promotion_stores');
  assert.equal(reads,2);
  await docs.get('restricted_access/employee1');
  await docs.get('restricted_access/employee1');
  assert.equal(reads,4,'security must not be cached');
  await docs.get('access_sessions/employee1');
  await docs.get('access_sessions/employee1');
  assert.equal(reads,6,'sessions must not be cached');
  await docs.get('config/acpOfferValidity');
  await docs.get('config/acpOfferValidity');
  assert.equal(reads,8,'validity must not be cached');
  now+=16000;
  await docs.get('config/acpProvider');
  assert.equal(reads,9,'cache expires after 15s');
  await docs.write('config/acpProvider',{enabled:true});
  await docs.get('config/acpProvider');
  assert.equal(reads,10,'successful writes invalidate cached settings');
});