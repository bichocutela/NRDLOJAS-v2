import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createLiveCatalogLookup,catalogIdentity} from './catalog-live.mjs';
const boundedJson = response => response.json();
const record = code => ({produto_id:code,codigo_erp:code,descricao:`Produto ${code}`,imagem:`${code}.jpg`,link:`produto-${code}`});
const login = () => Response.json({data:'public-token-'.repeat(6)});
test('live uses current ACP fields, preserves leading zeros and skips empty aliases',()=>{
  assert.deepEqual(catalogIdentity({codproduto:'',code:'00123',desc_prod:'',description:'Café 1L'}),{code:'00123',ean:'',description:'Café 1L'});
});
test('parallel live reads share login and cached terms, including safe description encoding',async()=>{
  let logins=0,searches=0,active=0,peak=0;
  const fetcher=async url=>{
    if(url.includes('/auth/')){logins++;return login();}
    if(url.includes('/departamentos/arvore'))return Response.json({data:[]});
    searches++;active++;peak=Math.max(peak,active);
    await new Promise(resolve=>setTimeout(resolve,5));active--;
    const term=decodeURIComponent(new URL(url).pathname.split('/termo/')[1].split('/rapida')[0]);
    return Response.json({data:{produtos:[{...record(term),descricao:term}]}});
  };
  const lookup=createLiveCatalogLookup({boundedJson,fetcher});
  const items=[{code:'00123'},{code:'2'},{code:'3'},{code:'4'},{description:'G&T 269ml'}];
  const first=await lookup(items);assert.equal(first.length,5);assert.equal(peak,4);assert.equal(logins,1);
  assert.equal(first.find(p=>p.codigo_interno==='00123').produto_id,'00123');
  assert.ok(first.some(p=>p.descricao==='G&T+269ml'));
  await lookup(items);assert.equal(searches,5);
});
test('one failed search retains other images and empty code results fall back to description',async()=>{
  const fetcher=async url=>{
    if(url.includes('/auth/'))return login();
    if(url.includes('/departamentos/arvore'))return Response.json({data:[]});
    if(url.includes('/termo/broken/'))throw Error('upstream');
    if(url.includes('/termo/missing/'))return Response.json({data:{produtos:[]}});
    return Response.json({data:{produtos:[{...record('2021000'),descricao:'Beats G&T 269ml'}]}});
  };
  const lookup=createLiveCatalogLookup({boundedJson,fetcher});
  const items=await lookup([{code:'broken'},{code:'missing',description:'Beats G&T 269ml'}]);
  assert.equal(items.length,1);assert.equal(items[0].codigo_interno,'2021000');
});
test('deadline ends slow requests and invalid max-items does not disable lookup',async()=>{
  const fetcher=async(url,{signal})=>{
    if(url.includes('/auth/'))return login();
    if(url.includes('/departamentos/arvore'))return Response.json({data:[]});
    return new Promise((resolve,reject)=>signal.addEventListener('abort',()=>reject(signal.reason),{once:true}));
  };
  const lookup=createLiveCatalogLookup({boundedJson,fetcher,budgetMs:20,maxItems:NaN});
  const keepAlive=setTimeout(()=>{},100);
  assert.deepEqual(await lookup([{code:'2021000'}]),[]);clearTimeout(keepAlive);
});


test('durable associations advance beyond the per-response limit across cold starts',async()=>{
  const entries=new Map();
  const store={many:async keys=>new Map(keys.filter(key=>entries.has(key)).map(key=>[key,{payload:entries.get(key)}])),
    get:async key=>entries.has(key)?{payload:entries.get(key)}:null,write:async(key,payload)=>{entries.set(key,payload);}};
  let searches=0;
  const fetcher=async url=>{
    if(url.includes('/auth/'))return login();
    if(url.includes('/departamentos/arvore'))return Response.json({data:[]});
    searches++;
    const code=decodeURIComponent(new URL(url).pathname.split('/termo/')[1].split('/rapida')[0]);
    return Response.json({data:{produtos:[{...record(code),classificacao_mercadologica_id:337}]}});
  };
  const items=Array.from({length:5},(_,i)=>({code:String(i+1)}));
  for(const expected of [2,4,5]){
    const rows=await createLiveCatalogLookup({boundedJson,fetcher,store,maxItems:2})(items);
    assert.equal(rows.length,expected);assert.equal(rows[0].department,'Bebidas alcoólicas');
  }
  assert.equal(searches,5);
});
test('EAN verifies identity when the code search returns the wrong product',async()=>{
  const fetcher=async url=>{
    if(url.includes('/auth/'))return login();
    if(url.includes('/departamentos/arvore'))return Response.json({data:[]});
    const correct=url.includes('/termo/7891149840878/');
    return Response.json({data:{produtos:[{...record(correct?'2021000':'wrong'),codigo_barras:correct?'7891149840878':'7891149840000',classificacao_mercadologica_id:337}]}});
  };
  const rows=await createLiveCatalogLookup({boundedJson,fetcher})([{code:'outdated',barCode:'7891149840878'}]);
  assert.equal(rows.length,1);assert.equal(rows[0].codigo_interno,'2021000');
});

test('official department tree maps every nested section in all sixteen departments',async()=>{
  const {OFFICIAL_DEPARTMENTS}=await import('../nordestao-catalog-resolver/handler.mjs');
  const {buildDepartmentMap,vipRecord}=await import('./catalog-live.mjs');
  const roots=OFFICIAL_DEPARTMENTS.map((descricao,i)=>({descricao,classificacao_mercadologica_id:i*10,
    children:[{classificacao_mercadologica_id:i*10+1,children:[{classificacao_mercadologica_id:i*10+2}]}]}));
  const map=buildDepartmentMap(roots);
  for(let i=0;i<OFFICIAL_DEPARTMENTS.length;i++) {
    for(const id of [i*10,i*10+1,i*10+2]) assert.equal(map[id],OFFICIAL_DEPARTMENTS[i]);
    assert.equal(vipRecord({produto_id:String(i),descricao:'Produto',classificacao_mercadologica_id:i*10+2},map).department,OFFICIAL_DEPARTMENTS[i]);
  }
});
