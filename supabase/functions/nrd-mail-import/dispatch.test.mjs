import {test} from 'node:test';
import assert from 'node:assert/strict';
import {dispatchScan} from './dispatch.mjs';
const args={url:'https://script.google.com/macros/s/synthetic-deployment/exec',token:'a'.repeat(64),requestId:'11111111-1111-4111-8111-111111111111'};
test('dispatch sends only the private capability and pending request to the configured Google endpoint',async()=>{
 let calls=0;
 await dispatchScan({...args,fetcher:async(url,options)=>{calls++;assert.equal(url,args.url);assert.equal(options.method,'POST');assert.deepEqual(JSON.parse(options.body),{token:args.token,requestId:args.requestId});return Response.json({ok:true});}});
 assert.equal(calls,1);
});
test('dispatch rejects foreign destinations, credentials, queries and failed execution',async()=>{
 for(const url of ['https://evil.test/exec','https://script.google.com.evil.test/macros/s/a/exec',args.url+'?token=x',args.url.replace('https:','http:'),args.url.replace('script.google.com','user@script.google.com')]) {
  await assert.rejects(()=>dispatchScan({...args,url,fetcher:()=>{throw Error('must not send');}}),/INVALID_CONNECTOR/);
 }
 await assert.rejects(()=>dispatchScan({...args,fetcher:async()=>Response.json({ok:false})}),/CONNECTOR_FAILED/);
});
