import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHandler} from './handler.mjs';
function fixture(overrides={}) {
  const events=[];
  const master={uid:'master-uid',email:'mestre@nrdlojas.com',authTime:100};
  const deps={verify:async()=>master,lookup:async uid=>uid===master.uid?{email:master.email,validSince:'99'}:{email:'teste@usuarios.nrdlojas.com'},
    grant:async()=>({login:'teste'}),disable:async()=>events.push('disable'),removeUser:async()=>events.push('user'),removeGrant:async()=>events.push('grant'),...overrides};
  const handle=createHandler(deps);
  const send=(body={uid:'user-uid'},token='valid')=>handle(new Request('https://example.test',{method:'POST',headers:token?{'x-firebase-token':token}:{},body:JSON.stringify(body)}));
  return {events,send};
}
test('exclusion revokes access before deleting authentication and document',async()=>{const f=fixture();const r=await f.send();assert.equal(r.status,200);assert.deepEqual(await r.json(),{deleted:true});assert.deepEqual(f.events,['disable','user','grant']);});
test('unauthenticated calls cannot mutate',async()=>{const f=fixture();assert.equal((await f.send(undefined,'')).status,401);assert.deepEqual(f.events,[]);});
test('forged or expired identity is denied',async()=>{const f=fixture({verify:async()=>{throw Error();}});assert.equal((await f.send()).status,401);assert.deepEqual(f.events,[]);});
test('ordinary account cannot mutate',async()=>{const f=fixture({verify:async()=>({uid:'other',email:'user@usuarios.nrdlojas.com'})});assert.equal((await f.send()).status,403);assert.deepEqual(f.events,[]);});
test('revoked Master session cannot mutate',async()=>{const f=fixture({lookup:async()=>({email:'mestre@nrdlojas.com',validSince:'101'})});assert.equal((await f.send()).status,403);assert.deepEqual(f.events,[]);});
test('disabled Master cannot mutate',async()=>{const f=fixture({lookup:async()=>({email:'mestre@nrdlojas.com',disabled:true})});assert.equal((await f.send()).status,403);});
test('Master cannot delete own identity',async()=>{const f=fixture();assert.equal((await f.send({uid:'master-uid'})).status,400);assert.deepEqual(f.events,[]);});
test('path traversal and unexpected fields are denied',async()=>{for(const body of [{uid:'../admin'},{uid:'user-uid',email:'other'}]){const f=fixture();assert.equal((await f.send(body)).status,400);assert.deepEqual(f.events,[]);}});
test('management logins and unrelated emails are protected',async()=>{for(const overrides of [{grant:async()=>({login:'mestre'})},{lookup:async uid=>({email:uid==='master-uid'?'mestre@nrdlojas.com':'admin@nrdlojas.com'})}]){const f=fixture(overrides);assert.equal((await f.send()).status,403);assert.deepEqual(f.events,[]);}});
test('missing grant is idempotent without deleting an arbitrary identity',async()=>{const f=fixture({grant:async()=>null});assert.equal((await f.send()).status,200);assert.deepEqual(f.events,[]);});
test('failed authentication deletion keeps revoked grant for retry',async()=>{const f=fixture({removeUser:async()=>{throw Error();}});const r=await f.send();assert.equal(r.status,503);assert.equal((await r.json()).error,'DELETE_INCOMPLETE');assert.deepEqual(f.events,['disable']);});
test('retry cleans up grant when authentication was already deleted',async()=>{const f=fixture({lookup:async uid=>uid==='master-uid'?{email:'mestre@nrdlojas.com'}:null});assert.equal((await f.send()).status,200);assert.deepEqual(f.events,['disable','grant']);});
test('failed grant revocation never deletes authentication',async()=>{const f=fixture({disable:async()=>{throw Error();}});assert.equal((await f.send()).status,503);assert.deepEqual(f.events,[]);});
