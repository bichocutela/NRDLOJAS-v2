import {test} from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
if(!globalThis.crypto)globalThis.crypto=webcrypto;
import {createHandler,documentStore,digest} from './handler.mjs';
const key='a'.repeat(64);
const pdf=btoa('%PDF-1.4\nfixture');
const request=(body,headers={})=>new Request('https://test/',{method:'POST',headers,body:JSON.stringify(body)});
function fixture(){
 const items=new Map(); let hash; const db={settings:async()=>hash?{key_hash:hash}:null,
 setup:async(email,value)=>{hash=value;},list:async()=>[...items.values()].map(({pdf_base64,...meta})=>meta),
 get:async id=>items.get(id),enqueue:async value=>{if(items.has(value.id))return false;items.set(value.id,{...value,status:'pending'});return true;},
 finish:async(id,status)=>items.set(id,{...items.get(id),status,pdf_base64:null})};
 const handler=createHandler({db,random:()=>key,verify:async token=>{if(token!=='master')throw Error();return {email:'mestre@nrdlojas.com'};}});
 return {handler,items};
}
test('rejects unauthenticated callers and public API keys',async()=>{
 const {handler}=fixture();assert.equal((await handler(request({operation:'list'}))).status,403);
 assert.equal((await handler(request({operation:'setup',email:'x@gmail.com'},{apikey:'public'}))).status,403);
});
test('only Mestre provisions connector; capability only ingests and duplicates are idempotent',async()=>{
 const {handler,items}=fixture();
 assert.equal((await handler(request({operation:'setup',email:'box@gmail.com'},{'x-firebase-token':'master'}))).status,200);
 const headers={'x-nrd-mail-key':key};
 assert.equal((await handler(request({operation:'list'},headers))).status,403);
 const body={operation:'ingest',filename:'CENTRO_0005.pdf',pdf};
 assert.equal((await (await handler(request(body,headers))).json()).duplicate,false);
 assert.equal((await (await handler(request(body,headers))).json()).duplicate,true);
 assert.equal(items.size,1);
 const city=await (await handler(request({...body,filename:'CLUBE_0012.pdf'},headers))).json();
 assert.equal(city.ignored,true);assert.equal(items.size,1);
 const id=[...items.keys()][0];
 assert.equal((await handler(request({operation:'published',id},headers))).status,403);
 await handler(request({operation:'published',id},{'x-firebase-token':'master'}));
 assert.equal(items.get(id).pdf_base64,null);
});
test('rejects invalid PDF, unknown units and deceptive filename',async()=>{
 assert.throws(()=>documentStore('../CENTRO_0005.pdf'));assert.throws(()=>documentStore('CENTRO_9999.pdf'));
 const {handler}=fixture();await handler(request({operation:'setup',email:'box@gmail.com'},{'x-firebase-token':'master'}));
 assert.equal((await handler(request({operation:'ingest',filename:'CENTRO_0005.pdf',pdf:btoa('not PDF')},{'x-nrd-mail-key':key}))).status,400);
});
