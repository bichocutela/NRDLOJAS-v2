import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHandler} from '../nrd-price-gateway/handler.mjs';
const request=operation=>new Request('https://test',{method:'POST',body:JSON.stringify({operation})});
test('only the verified scheduler can trigger independent background sync',async()=>{
 let ticks=0;
 const handler=createHandler({authorize:async()=>({allowed:true,scheduler:true}),credentials:()=>({login:'test',password:'test'}),promotionSync:async({input})=>{assert.equal(input.operation,'promotion_status');ticks++;return {revision:'a'.repeat(64),count:0,checkedAt:1};}});
 assert.equal((await handler(request('promotion_tick'))).status,202);assert.equal(ticks,1);
 assert.equal((await handler(request('access'))).status,403);
 const publicHandler=createHandler({authorize:async()=>({allowed:true}),credentials:()=>({login:'test',password:'test'}),promotionSync:async()=>{throw Error('must not run');}});
 assert.equal((await publicHandler(request('promotion_tick'))).status,403);
});
