import {test} from 'node:test';
import assert from 'node:assert/strict';
import {ServerCache} from './server-cache.mjs';
test('optimistic leases allow one winner and reject stale writes',async()=>{
  let document=null, revision=0;
  const cache=new ServerCache({project:'test',token:async()=> 'synthetic',fetcher:async(url,options)=>{
    if (options.method!=='PATCH') return document ? Response.json(document) : new Response('',{status:404});
    const query=new URL(url).searchParams;
    if (query.get('currentDocument.exists')==='false' && document) return new Response('',{status:409});
    if (query.has('currentDocument.updateTime') && query.get('currentDocument.updateTime')!==document?.updateTime) return new Response('',{status:412});
    document={...JSON.parse(options.body),updateTime:String(++revision)}; return Response.json(document);
  }});
  const claims=await Promise.all([cache.claim('product'),cache.claim('product')]);
  assert.equal(claims.filter(Boolean).length,1);
  const winner=claims.find(Boolean);
  assert.equal(await cache.claim('product'),null);
  assert.ok(await cache.finish('product',winner,{category:'Bebidas'}));
  assert.equal(await cache.finish('product',winner,{category:'Limpeza'}),null);
  assert.equal((await cache.get('product')).payload.category,'Bebidas');
});
