import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHandler,clean,validate,TENANT} from './handler.mjs';
import {createAuthorizer} from './access.mjs';
const secret = () => ({login:'synthetic-user',password:'synthetic-password'});
const req = (body,token) => new Request('https://gateway.invalid',{method:'POST',body:JSON.stringify(body),headers:token?{'x-firebase-token':token}:{}});
const input = {path:'Product/all',parameters:[['description','Açúcar & Café'],['productCategoryIds','1'],['productCategoryIds','2']]};
const allow = async () => ({allowed:true,master:false});
test('promotion permission cannot read the general catalogue or arbitrary category',async()=>{
  const authorize=createAuthorizer({verify:async()=>({uid:'uid',email:'user@example.com'}),
    document:async path=>path.startsWith('config/')?{}:{enabled:{booleanValue:true},promotions:{booleanValue:true},prices:{booleanValue:false}}});
  const scoped = body => new Request('https://gateway.invalid',{method:'POST',body:JSON.stringify(body),
    headers:{'x-firebase-token':'synthetic','x-nrd-scope':'promotions'}});
  assert.equal((await authorize(req({},'synthetic'))).allowed,false);
  assert.equal((await authorize(scoped({}))).allowed,true);
  let calls=0;
  const handler=createHandler({authorize,credentials:secret,fetcher:async()=>{calls++;throw Error();}});
  assert.equal((await handler(scoped(input))).status,403);
  assert.equal((await handler(scoped({path:'ProductCategory/all',parameters:[]}))).status,403);
  assert.equal((await handler(scoped({path:'Promotion/all',parameters:[['productCategoryIds','9']]}))).status,400);
  assert.equal(calls,0);
});
test('promotion read dynamically resolves De/Por and preserves pagination and prices',async()=>{
  const server=upstream();
  const handler=createHandler({authorize:async()=>({allowed:true,master:false,promotionsOnly:true}),credentials:secret,
    fetcher:async(url,options)=>new URL(url).pathname.endsWith('/ProductCategory/all')
      ? Response.json({items:[{id:77,description:'De-Por'}],totalPages:1}) : server.fetcher(url,options)});
  const response=await handler(req({path:'Promotion/all',parameters:[['pageIndex','2'],['pageSize','250']]}));
  assert.equal(response.status,200);
  assert.equal((await response.json()).items[0].value,45.49);
  const url=new URL(server.calls.at(-1).url);
  assert.equal(url.searchParams.get('productCategoryIds'),'77');
  assert.equal(url.searchParams.get('pageIndex'),'2');
});
test('promotion scope can force a fresh snapshot without opening general catalogue access',async()=>{
  let operation='';
  const handler=createHandler({
    authorize:async()=>({allowed:true,master:false,promotionsOnly:true}),
    credentials:secret,
    promotionSync:async ({input})=>{
      operation=input.operation;
      return {revision:'a'.repeat(64),checkedAt:1,count:0,items:[],removedIds:[]};
    }
  });
  const response=await handler(req({operation:'promotion_refresh',revision:'',manifest:[]}));
  assert.equal(response.status,200);
  assert.equal(operation,'promotion_refresh');
  assert.equal((await response.json()).count,0);
});
test('promotion readiness is a fixed CI read and never returns product data',async()=>{
  const server=upstream();
  const handler=createHandler({authorize:async()=>({allowed:true,probe:true}),credentials:secret,
    fetcher:async(url,options)=>new URL(url).pathname.endsWith('/ProductCategory/all')
      ? Response.json({items:[{id:3,description:'De-Por'}],totalPages:1}) : server.fetcher(url,options)});
  assert.deepEqual(await (await handler(req({operation:'health_promotions'}))).json(),{ok:true});
  const url=new URL(server.calls.at(-1).url);
  assert.equal(url.searchParams.get('pageSize'),'1');
  assert.equal(url.searchParams.get('pageIndex'),'0');
  assert.equal((await createHandler({authorize:allow,credentials:secret})(req({operation:'health_promotions'}))).status,403);
});
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
test('optionally enriches ACP items from the Nordestão catalog and caches the snapshot',async()=>{
  const server=upstream();let catalogLoads=0;
  const handler=createHandler({authorize:allow,credentials:secret,fetcher:server.fetcher,catalogLoader:async()=>{
    catalogLoads++;
    return [{produto_id:'54487',codigo_interno:'1',descricao:'Café',imagem:'https://cdn.example.test/cafe.jpg',slug:'cafe'}];
  }});
  const first=await handler(req(input));
  const second=await handler(req(input));
  assert.equal(first.status,200);assert.equal(second.status,200);assert.equal(catalogLoads,1);
  const body=await first.json();
  assert.equal(body.items[0].imageUrl,'https://cdn.example.test/cafe.jpg');
  assert.match(body.items[0].productUrl,/produto\/54487\/cafe$/);
  assert.equal(body.items[0].catalog_match_type,'internal_code');
  assert.equal(body.items[0].accessToken,undefined);
});
test('catalog failure does not break the ACP response',async()=>{
  const server=upstream();
  const handler=createHandler({authorize:allow,credentials:secret,fetcher:server.fetcher,catalogLoader:async()=>{throw Error('offline');}});
  const response=await handler(req(input));
  assert.equal(response.status,200);
  const body=await response.json();
  assert.equal(body.items[0].imageUrl,undefined);
  assert.equal(body.items[0].clubValue,39.99);
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
test('readiness distinguishes cache quota from unavailable sync and classification',async()=>{
 for (const [reason,limited] of [['CACHE_RATE_LIMITED',true],['CACHE_UNAVAILABLE',false]]) {
  const server=upstream();
  const enrich=async items=>items;
  enrich.verify=async()=>{throw Error(reason);};
  const handler=createHandler({authorize:async()=>({allowed:true,probe:true}),credentials:secret,
   promotionSync:async()=>{throw Error(reason);},enrichPromotions:enrich,
   fetcher:async(url,options)=>new URL(url).pathname.endsWith('/ProductCategory/all')
    ? Response.json({items:[{id:77,description:'De-Por'}],totalPages:1}) : server.fetcher(url,options)});
  for (const [operation,status,error] of [['health_sync',502,'SYNC_UNAVAILABLE'],['health_categories',503,'CLASSIFICATION_NOT_READY']]) {
   const response=await handler(req({operation}));
   assert.equal(response.status,limited?429:status);
   assert.deepEqual(await response.json(),{error:limited?'SYNC_RATE_LIMITED':error});
  }
 }
});
test('restricted accounts need the owning device capability and matching auth time',async()=>{
  const device='a'.repeat(64);let session={deviceHash:{stringValue:'hashed'},authTime:{integerValue:'100'}};
  const auth=createAuthorizer({verify:async()=>({uid:'uid',email:'teste@usuarios.nrdlojas.com',authTime:100}),hash:async()=> 'hashed',document:async path=>path.startsWith('access_sessions/')?session:{enabled:{booleanValue:true},prices:{booleanValue:true}}});
  assert.equal((await auth(req({},'user'))).allowed,false);
  const request=new Request('https://gateway.invalid',{headers:{'x-firebase-token':'user','x-device-session':device}});
  assert.equal((await auth(request)).allowed,true);
  session.authTime.integerValue='99';assert.equal((await auth(request)).allowed,false);
  session.authTime.integerValue='100';session.deviceHash.stringValue='other';assert.equal((await auth(request)).allowed,false);
});

test('expired session during De/Por category discovery signs in once again',async()=>{
  const server=upstream(); let categoryCalls=0;
  const handler=createHandler({authorize:async()=>({allowed:true,promotionsOnly:true}),credentials:secret,
    fetcher:async(url,options)=>{
      if(new URL(url).pathname.endsWith('/ProductCategory/all')) {
        if(++categoryCalls===1) return new Response('',{status:401});
        return Response.json({items:[{id:77,description:'De-Por'}],totalPages:1});
      }
      return server.fetcher(url,options);
    }});
  const response=await handler(req({path:'Promotion/all',parameters:[['pageIndex','0'],['pageSize','1']]}));
  assert.equal(response.status,200);
  assert.equal(categoryCalls,2);
});


test('configured snapshot takes priority over live lookup',async()=>{
  const server=upstream();let liveCalls=0;
  const handler=createHandler({authorize:allow,credentials:secret,fetcher:server.fetcher,
    catalogLoader:async()=>[{produto_id:'9',codigo_interno:'1',descricao:'Café',imagem:'https://cdn.example.test/cafe.jpg'}],
    catalogLookup:async()=>{liveCalls++;return [];}});
  const body=await (await handler(req(input))).json();
  assert.equal(body.items[0].imageUrl,'https://cdn.example.test/cafe.jpg');assert.equal(liveCalls,0);
});

test('catalog readiness is restricted to CI and verifies the fixed code and image',async()=>{
  const server=upstream();
  const fetcher=async (url,options)=>{
    if(options?.method==='HEAD')return new Response(null,{headers:{'Content-Type':'image/jpeg'}});
    if(new URL(url).pathname.endsWith('/Product/all')){
      assert.equal(new URL(url).searchParams.get('code'),'2021000');
      return Response.json({items:[{code:'2021000',description:'Beats G&T Lata 269ml'}]});
    }
    return server.fetcher(url,options);
  };
  const options={credentials:secret,fetcher,catalogLookup:async()=>[
    {produto_id:'3653',codigo_interno:'2021000',department:'Bebidas alcoólicas',descricao:'Beats G&T Lata 269ml',imagem:'https://cdn.example.test/beats.jpg'}]};
  const handler=createHandler({...options,authorize:async()=>({allowed:true,probe:true})});
  assert.deepEqual(await (await handler(req({operation:'health_catalog'}))).json(),{ok:true});
  assert.equal((await createHandler({...options,authorize:allow})(req({operation:'health_catalog'}))).status,403);
  assert.equal(validate({operation:'health_catalog',code:'another-code'}),false);
});

