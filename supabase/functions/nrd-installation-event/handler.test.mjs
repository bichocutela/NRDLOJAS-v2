import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHandler} from './handler.mjs';
const hash='a'.repeat(64),proof='b'.repeat(64);
const fresh={deviceIdHash:hash,appVersion:'1.0.708',appVersionCode:708,firstSeenAt:'2026-10-03T12:00:00Z',createdAt:'2026-10-03T12:00:00Z'};
function fixture(overrides={}) {
  const messages=[],actions=[];
  const deps={readEvent:async()=>fresh,claim:async id=>({lease:{id}}),send:async data=>messages.push(data),
    finish:async()=>actions.push('finish'),release:async()=>actions.push('release'),...overrides};
  const handler=createHandler(deps);
  return {messages,actions,send:(value=proof)=>handler(new Request('https://test.example',{method:'POST',headers:value?{'x-installation-proof':value}:{},body:'{}'}))};
}
test('first installation sends fixed high-level event with version',async()=>{const f=fixture();assert.equal((await f.send()).status,200);assert.equal(f.messages[0].title,'Nova instalação do NRD V2');assert.equal(f.messages[0].eventId,hash+'_708');assert.deepEqual(f.actions,['finish']);});
test('existing device updating sends update rather than new installation',async()=>{const f=fixture({readEvent:async()=>({...fresh,firstSeenAt:'2026-09-01T12:00:00Z'})});await f.send();assert.equal(f.messages[0].title,'Aplicativo atualizado');assert.match(f.messages[0].body,/1\.0\.708/);});
test('missing or malformed capability never sends',async()=>{for(const p of ['', 'invalid','../'+proof]){const f=fixture();assert.equal((await f.send(p)).status,401);assert.equal(f.messages.length,0);}});
test('proof without immutable registered event never sends',async()=>{const f=fixture({readEvent:async()=>null});assert.equal((await f.send()).status,401);assert.equal(f.messages.length,0);});
test('already acknowledged delivery returns success without duplicate',async()=>{const f=fixture({claim:async()=>({sent:true})});assert.equal((await f.send()).status,200);assert.equal(f.messages.length,0);});
test('concurrent in-progress delivery asks client to retry',async()=>{const f=fixture({claim:async()=>({})});assert.equal((await f.send()).status,409);assert.equal(f.messages.length,0);});
test('FCM failure releases lease and asks for network retry',async()=>{const f=fixture({send:async()=>{throw Error('FCM down');}});assert.equal((await f.send()).status,503);assert.deepEqual(f.actions,['release']);});
test('invalid metadata cannot produce arbitrary notifications',async()=>{for(const change of [{appVersion:'attack'},{appVersionCode:-1},{deviceIdHash:'other'},{createdAt:'not-a-date'}]){const f=fixture({readEvent:async()=>({...fresh,...change})});assert.equal((await f.send()).status,400);assert.equal(f.messages.length,0);}});
test('claim failure does not send or falsely acknowledge',async()=>{const f=fixture({claim:async()=>{throw Error();}});assert.equal((await f.send()).status,503);assert.equal(f.messages.length,0);assert.deepEqual(f.actions,[]);});
test('a new version uses a distinct per-device delivery key',async()=>{const f=fixture({readEvent:async()=>({...fresh,appVersion:'1.0.709',appVersionCode:709})});await f.send();assert.equal(f.messages[0].eventId,hash+'_709');});
