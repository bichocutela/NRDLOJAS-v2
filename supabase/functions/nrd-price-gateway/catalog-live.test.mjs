import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createLiveCatalogLookup,catalogIdentity} from './catalog-live.mjs';
const boundedJson = response => response.json();
const record = code => ({produto_id:code,codigo_erp:code,descricao:`Produto ${code}`,imagem:`${code}.jpg`,link:`produto-${code}`});
const login = () => Response.json({data:'public-token-'.repeat(6)});
test('live uses current ACP fields, preserves leading zeros and skips empty aliases',()=>{
  assert.deepEqual(catalogIdentity({codproduto:'',code:'00123',desc_prod:'',description:'Café 1L'}),{code:'00123',description:'Café 1L'});
});
test('parallel live reads share login and cached terms, including safe description encoding',async()=>{
  let logins=0,searches=0,active=0,peak=0;
  const fetcher=async url=>{
    if(url.includes('/auth/')){logins++;return login();}
    searches++;active++;peak=Math.max(peak,active);
    await new Promise(resolve=>setTimeout(resolve,5));active--;
    const term=decodeURIComponent(new URL(url).pathname.split('/termo/')[1].split('/rapida')[0]);
    return Response.json({data:{produtos:[record(term)]}});
  };
  const lookup=createLiveCatalogLookup({boundedJson,fetcher});
  const items=[{code:'00123'},{code:'2'},{code:'3'},{code:'4'},{description:'G&T 269ml'}];
  const first=await lookup(items);assert.equal(first.length,5);assert.equal(peak,4);assert.equal(logins,1);
  assert.equal(first.find(p=>p.codigo_interno==='00123').produto_id,'00123');
  assert.ok(first.some(p=>p.descricao==='Produto G&T+269ml'));
  await lookup(items);assert.equal(searches,5);
});
test('one failed search retains other images and empty code results fall back to description',async()=>{
  const fetcher=async url=>{
    if(url.includes('/auth/'))return login();
    if(url.includes('/termo/broken/'))throw Error('upstream');
    if(url.includes('/termo/missing/'))return Response.json({data:{produtos:[]}});
    return Response.json({data:{produtos:[record('2021000')]}});
  };
  const lookup=createLiveCatalogLookup({boundedJson,fetcher});
  const items=await lookup([{code:'broken'},{code:'missing',description:'Beats G&T 269ml'}]);
  assert.equal(items.length,1);assert.equal(items[0].codigo_interno,'2021000');
});
test('deadline ends slow requests and invalid max-items does not disable lookup',async()=>{
  const fetcher=async(url,{signal})=>{
    if(url.includes('/auth/'))return login();
    return new Promise((resolve,reject)=>signal.addEventListener('abort',()=>reject(signal.reason),{once:true}));
  };
  const lookup=createLiveCatalogLookup({boundedJson,fetcher,budgetMs:20,maxItems:NaN});
  const keepAlive=setTimeout(()=>{},100);
  assert.deepEqual(await lookup([{code:'2021000'}]),[]);clearTimeout(keepAlive);
});
