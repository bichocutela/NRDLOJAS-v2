import test from 'node:test';
import assert from 'node:assert/strict';
import { withBrowserCors } from './browser-cors.mjs';
test('browser preflight allows Firebase headers without invoking privileged work', async () => {
  let calls=0;
  const handler=withBrowserCors(()=>{calls++;return new Response('{}');});
  const response=await handler(new Request('https://example.test',{method:'OPTIONS'}));
  assert.equal(response.status,204);assert.equal(calls,0);
  assert.match(response.headers.get('Access-Control-Allow-Headers'),/x-firebase-token/);
  assert.equal(response.headers.get('Access-Control-Allow-Origin'),'https://bichocutela.github.io');
});
test('CORS preserves authorization rejection and never echoes arbitrary origins',async()=>{
 const response=await withBrowserCors(()=>new Response('{"error":"ACCESS_DENIED"}',{status:403,headers:{'Cache-Control':'no-store'}}))(new Request('https://example.test',{method:'POST',headers:{Origin:'https://evil.test'}}));
 assert.equal(response.status,403);assert.equal(response.headers.get('Cache-Control'),'no-store');
 assert.notEqual(response.headers.get('Access-Control-Allow-Origin'),'https://evil.test');
 assert.equal(await response.text(),'{"error":"ACCESS_DENIED"}');
});
