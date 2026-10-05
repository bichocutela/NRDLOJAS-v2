import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createSessionHandler} from './session.mjs';
const a='a'.repeat(64),b='b'.repeat(64);
function fixture(overrides={}) {
  let state=null,revision=0;
  const deps={verify:async()=>({uid:'uid',email:'teste@usuarios.nrdlojas.com',authTime:100}),lookup:async()=>({email:'teste@usuarios.nrdlojas.com',validSince:'99'}),grant:async()=>({login:'teste'}),
    hash:async value=>createHash('sha256').update(value).digest('hex'),
    readSession:async()=>state?{...state}:null,
    writeSession:async(uid,value,version)=>{if(version!==state?.version)return false;state={...value,version:String(++revision)};return true;},
    deleteSession:async(uid,version)=>{if(version!==state?.version)return false;state=null;return true;},...overrides};
  const handler=createSessionHandler(deps);
  return {send:(action='claim',deviceToken=a,token='valid')=>handler(new Request('https://example.test/session',{method:'POST',headers:token?{'x-firebase-token':token}:{},body:JSON.stringify({action,deviceToken})})),state:()=>state};
}
test('first device reserves session; only hashed capability is stored',async()=>{const f=fixture();assert.equal((await f.send()).status,200);assert.notEqual(f.state().deviceHash,a);assert.equal(f.state().authTime,100);});
test('same device can resume after app restart',async()=>{const f=fixture();await f.send();assert.equal((await f.send()).status,200);});
test('second device cannot claim or release first device',async()=>{const f=fixture();await f.send();for(const action of ['claim','release']){const r=await f.send(action,b);assert.equal(r.status,409);assert.equal((await r.json()).error,'DEVICE_IN_USE');}assert.ok(f.state());});
test('explicit logout releases for another device; release is idempotent',async()=>{const f=fixture();await f.send();assert.equal((await f.send('release')).status,200);assert.equal((await f.send('release')).status,200);assert.equal((await f.send('claim',b)).status,200);});
test('simultaneous claims have one winner',async()=>{const f=fixture();const results=await Promise.all([f.send('claim',a),f.send('claim',b)]);assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);});
test('invalid, expired, disabled and unrelated identities cannot reserve',async()=>{for(const overrides of [{verify:async()=>{throw Error();}},{lookup:async()=>({email:'teste@usuarios.nrdlojas.com',disabled:true})},{lookup:async()=>({email:'teste@usuarios.nrdlojas.com',validSince:'101'})},{grant:async()=>null},{grant:async()=>({login:'other'})},{verify:async()=>({uid:'uid',email:'mestre@nrdlojas.com',authTime:100}),lookup:async()=>({email:'mestre@nrdlojas.com'})}]){const f=fixture(overrides);assert.ok([401,403].includes((await f.send()).status));assert.equal(f.state(),null);}});
test('malformed capabilities and actions are rejected',async()=>{const f=fixture();assert.equal((await f.send('claim','short')).status,400);assert.equal((await f.send('delete')).status,400);assert.equal((await f.send('claim',a,'')).status,401);});
test('contention or server failure never reports success',async()=>{assert.equal((await fixture({writeSession:async()=>false}).send()).status,503);assert.equal((await fixture({readSession:async()=>{throw Error();}}).send()).status,503);});
test('failed logout keeps original reservation',async()=>{const f=fixture({deleteSession:async()=>{throw Error();}});await f.send();assert.equal((await f.send('release')).status,503);assert.equal((await f.send('claim',b)).status,409);});
