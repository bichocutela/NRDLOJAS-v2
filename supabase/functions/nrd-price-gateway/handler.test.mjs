import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHandler,clean,validate,TENANT} from './handler.mjs';
import {createAuthorizer} from './access.mjs';
const secret = () => ({login:'synthetic-user',password:'synthetic-password'});
const req = (body,token) => new Request('https://gateway.invalid',{method:'POST',body:JSON.stringify(body),headers:token?{'x-firebase-token':token}:{}});
const input = {path:'Product/all',parameters:[['description','Açúcar & Café'],['productCategoryIds','1'],['productCategoryIds','2']]};
const allow = async () => ({allowed:true,master:false});
test('master, per-account grants, public access and revocation use current documents',async()=>{
  let settings={},account={enabled:{booleanValue:true},prices:{booleanValue:true}};
  const auth=createAuthorizer({verify:async t=>{if(t==='bad')throw Error();return {uid:'uid',email:t==='master'?'mestre@nrdlojas.com':'user@example.com'};},
    document:async path=>path.startsWith('config/')?settings:account});
  assert.deepEqual(await auth(req({},'master')),{allowed:true,master:true});
  assert.equal((await auth(req({},'user'))).allowed,true);
  account.prices.booleanValue=false; assert.equal((await auth(req({},'user'))).allowed,false);
  account.prices.booleanValue=true;account.enabled.booleanValue=false;assert.equal((await auth(req({},'user'))).allowed,false);
  assert.equal((await auth(req({}))).allowed,false);
  settings={publicAccess:{booleanValue:true}};assert.equal((await auth(req({}))).allowed,true);
  await assert.rejects(()=>auth(req({},'bad')));
});
test('denied, invalid token, unsupported route and non-master diagnostics never contact ACP',async()=>{
  let calls=0;const fetcher=async()=>{calls++;throw Error();};
  for(const [authorize,body,status] of [[async()=>({allowed:false}),input,403],[async()=>{throw Error();},input,401],
    [allow,{path:'https://evil.invalid',parameters:[]},400],[allow,{path:'TemplatePrintLog/all',parameters:[]},403]]){
    assert.equal((await createHandler({authorize,credentials:secret,fetcher})(req(body))).status,status);
  }
  assert.equal(calls,0);
});
test('bounded input rejects large bodies and ambiguous/invalid parameters',async()=>{
  for(const parameters of [[['pageSize','251']],[['pageIndex','3001']],[['description','a'],['description','b']],[['url','https://evil.invalid']]])assert.equal(validate({path:'Product/all',parameters}),false);
  const handler=createHandler({authorize:allow,credentials:secret});
  assert.equal((await handler(req({...input,extra:'x'.repeat(5000)}))).status,400);
});
function upstream(proxy=false,expire=false){
  const calls=[];let failed=false;
  const fetcher=async(url,options)=>{
    calls.push({url:String(url),options});const path=new URL(url).pathname;
    if(path.endsWith('/csrf'))return Response.json({csrfToken:'csrf'},{headers:{'Set-Cookie':'csrf=cookie; secure; httponly'}});
    if(path.endsWith('/credentials'))return Response.json({url:'/home'},{headers:{'Set-Cookie':'session=server-cookie; secure; httponly'}});
    if(path.endsWith('/session'))return Response.json({user:{accessToken:'server-only-token',proxyEnable:proxy}});
    if(expire&&!failed){failed=true;return Response.json({}, {status:401});}
    return Response.json({items:[{code:'1',description:'Café',value:45.49,clubValue:39.99,quantityTake:3,quantityPay:2,
      stockQuantity:8,validOffer:'01/10/2026',cashback:30,accessToken:'never-return',user:{email:'private'}}],totalPages:1});
  };return {fetcher,calls};
}
test('preserves prices/offers, encodes repeated filters, isolates cookies and reuses server session',async()=>{
  for(const proxy of [false,true]){
    const server=upstream(proxy);const handler=createHandler({authorize:allow,credentials:secret,fetcher:server.fetcher});
    const response=await handler(req(input));assert.equal(response.status,200);const body=await response.json();
    assert.equal(body.items[0].clubValue,39.99);assert.equal(body.items[0].quantityTake,3);assert.equal(body.items[0].cashback,30);
    assert.equal(body.items[0].accessToken,undefined);assert.equal(body.items[0].user,undefined);
    const call=server.calls.at(-1),url=new URL(call.url);assert.equal(url.searchParams.get('description'),'Açúcar & Café');
    assert.deepEqual(url.searchParams.getAll('productCategoryIds'),['1','2']);
    assert.equal(url.origin,proxy?TENANT:'https://api.acp.app.br');
    assert.equal(!!call.options.headers.Cookie,proxy);
    await handler(req(input));assert.equal(server.calls.filter(x=>x.url.endsWith('/credentials')).length,1);
  }
});
test('session expiry renews exactly once; upstream error bodies are never disclosed',async()=>{
  const server=upstream(false,true);const handler=createHandler({authorize:allow,credentials:secret,fetcher:server.fetcher});
  assert.equal((await handler(req(input))).status,200);
  assert.equal(server.calls.filter(x=>x.url.endsWith('/credentials')).length,2);
  const failure=createHandler({authorize:allow,credentials:secret,fetcher:async()=>new Response('private detail',{status:500})});
  assert.deepEqual(await (await failure(req(input))).json(),{error:'CONSULTATION_UNAVAILABLE'});
});
test('missing secrets fail closed; historical embedded JSON also strips identities and secrets',async()=>{
  const handler=createHandler({authorize:allow,credentials:()=>({})});assert.equal((await handler(req(input))).status,503);
  const out=clean({dataLog:JSON.stringify({value:12,userId:3,password:'secret',product:{code:'1'}})});
  assert.deepEqual(JSON.parse(out.dataLog),{value:12,product:{code:'1'}});
});
test('CI probe can only execute a fixed readiness read and never receive product data',async()=>{
 const server=upstream();const handler=createHandler({authorize:async()=>({allowed:true,probe:true}),credentials:secret,fetcher:server.fetcher});
 assert.equal((await handler(req(input))).status,403);
 assert.deepEqual(await (await handler(req({operation:'health'}))).json(),{ok:true});
 assert.equal(new URL(server.calls.at(-1).url).pathname,'/api/v1/ProductCategory/all');
 assert.equal(new URL(server.calls.at(-1).url).searchParams.get('pageSize'),'1');
 assert.equal((await createHandler({authorize:allow,credentials:secret})(req({operation:'health'}))).status,403);
});
test('Firestore quota and availability failures deny access without querying ACP',async()=>{
 let calls=0;
 for(const [reason,status] of [['PERMISSIONS_RATE_LIMITED',429],['PERMISSIONS_UNAVAILABLE',503]]){
  const handler=createHandler({authorize:async()=>{throw Error(reason);},credentials:secret,fetcher:async()=>{calls++;throw Error();}});
  assert.equal((await handler(req(input))).status,status);
 }
 assert.equal(calls,0);
});
