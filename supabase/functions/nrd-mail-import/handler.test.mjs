import {test} from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
if(!globalThis.crypto)globalThis.crypto=webcrypto;
import {createHandler,documentStore,digest} from './handler.mjs';
const key='a'.repeat(64);
const pdf=btoa('%PDF-1.4\nfixture');
const request=(body,headers={})=>new Request('https://test/',{method:'POST',headers,body:JSON.stringify(body)});
function fixture({notify=async()=>{}}={}){
 const items=new Map(); let hash; let notificationClaimed=false; let notifications=0; let requested=null,finished=null,failed=false,seen=0;
 const db={settings:async()=>hash?{key_hash:hash}:null,
 requestScan:async()=>{requested='11111111-1111-4111-8111-111111111111';return requested;},
 scanStatus:async()=>({requestId:requested,finishedId:finished,seenAt:seen?'2026-10-26T00:00:00Z':null,finishedAt:null,failed}),
 poll:async()=>{seen++;return {requestId:requested,finishedId:finished};},
 ack:async(id,error)=>{if(id===requested){finished=id;failed=error;}},
 setup:async(email,value)=>{hash=value;},list:async()=>[...items.values()].map(({pdf_base64,...meta})=>meta),
 get:async id=>items.get(id),enqueue:async value=>{if(items.has(value.id))return false;items.set(value.id,{...value,status:'pending'});return true;},
 finish:async(id,status)=>items.set(id,{...items.get(id),status,pdf_base64:null}),claimMailNotification:async()=>{if(notificationClaimed)return false;notificationClaimed=true;return true;}};
 const handler=createHandler({db,notifyMailDocuments:async()=>{notifications++;await notify();},random:()=>key,verify:async token=>{if(token!=='master')throw Error();return {email:'mestre@nrdlojas.com'};}});
 return {handler,items,get notifications(){return notifications;}};
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

test('only Mestre requests Gmail scan; script key may poll and acknowledge but never list or publish',async()=>{
 const {handler}=fixture();
 assert.equal((await handler(request({operation:'scan'}))).status,403);
 await handler(request({operation:'setup',email:'box@gmail.com'},{'x-firebase-token':'master'}));
 const initial=await (await handler(request({operation:'scan'},{'x-firebase-token':'master'}))).json();
 assert.match(initial.requestId,/^[0-9a-f-]{36}$/);
 assert.equal((await handler(request({operation:'poll'},{'x-firebase-token':'master'}))).status,400);
 const poll=await (await handler(request({operation:'poll'},{'x-nrd-mail-key':key}))).json();
 assert.equal(poll.requestId,initial.requestId);
 assert.equal(poll.finishedId,null);
 assert.equal((await handler(request({operation:'list'},{'x-nrd-mail-key':key}))).status,403);
 assert.equal((await handler(request({operation:'ack',requestId:'bad',failed:false},{'x-nrd-mail-key':key}))).status,400);
 assert.equal((await handler(request({operation:'ack',requestId:initial.requestId,failed:false},{'x-nrd-mail-key':key}))).status,200);
 const state=await (await handler(request({operation:'status'},{'x-firebase-token':'master'}))).json();
 assert.equal(state.finishedId,initial.requestId);
 assert.equal(state.failed,false);
 assert.equal((await handler(request({operation:'status'},{'x-nrd-mail-key':key}))).status,403);
});

test('notifies the Master once for new reports and ignores duplicate attachments',async()=>{
 const {handler,notifications}=fixture();
 await handler(request({operation:'setup',email:'box@gmail.com'},{'x-firebase-token':'master'}));
 const headers={'x-nrd-mail-key':key}, body={operation:'ingest',filename:'CENTRO_0005.pdf',pdf};
 await handler(request(body,headers));
 await handler(request({...body,filename:'CLUBE_0008.pdf'},headers));
 await handler(request(body,headers));
 assert.equal(notifications,1);
});
