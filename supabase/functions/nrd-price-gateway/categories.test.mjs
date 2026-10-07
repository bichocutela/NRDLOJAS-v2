import {test} from 'node:test';
import assert from 'node:assert/strict';
import {strongCategory,validateCategories,categoryKey} from './categories.mjs';
test('full description disambiguates water and milk used as cosmetics or cleaners',()=>{
  assert.equal(strongCategory('ÁGUA MICELAR LOREAL 200ML'),'Higiene e beleza');
  assert.equal(strongCategory('AGUA SANITARIA YPE 1L'),'Limpeza');
  assert.equal(strongCategory('LEITE DE ROSAS 100ML'),'Higiene e beleza');
  assert.equal(strongCategory('AGUA MINERAL 500ML'),null);
});
test('strict category mapping rejects foreign products, duplicate EAN, unknown category and extra keys',()=>{
  const products=[{barCode:'1',description:'agua micelar'}];
  assert.equal(validateCategories([{ean:'1',categoria_correta:'Bebidas'}],products)[0].category,'Higiene e beleza');
  for(const result of [[{ean:'2',categoria_correta:'Bebidas'}],[{ean:'1',categoria_correta:'inventada'}],[{ean:'1',categoria_correta:'Bebidas',extra:1}]]) assert.throws(()=>validateCategories(result,products));
});
test('cache key stable for unchanged full description and changes on revised description',async()=>{
  const a={barCode:'1',code:'9',description:'Produto A'};
  assert.equal(await categoryKey(a),await categoryKey({...a}));
  assert.notEqual(await categoryKey(a),await categoryKey({...a,description:'Produto B'}));
});

test('persistent page summary reduces 250 category reads to one across cold starts',async()=>{
  const {createCategorizer}=await import('./categories.mjs');
  const products=Array.from({length:250},(_,i)=>({code:String(i),barCode:String(i),description:`Produto ${i}`}));
  const docs=new Map(await Promise.all(products.map(async item=>[await categoryKey(item),{payload:{category:'Mercearia'},version:'v1'}])));
  let reads=0, calls=0;
  const cache={many:async keys=>{reads+=keys.length;return new Map(keys.filter(key=>docs.has(key)).map(key=>[key,docs.get(key)]));},
    write:async(key,payload)=>{docs.set(key,{payload,version:'v1'});return docs.get(key);}};
  const options={cache,apiKey:'',background:()=>{},fetcher:async()=>{calls++;throw Error('unexpected');}};
  const warm=await createCategorizer(options)(products);
  assert.equal(reads,251); assert.ok(warm.every(item=>item.nrdCategory==='Mercearia'));
  reads=0;
  // Recreate categorizer to simulate losing all Edge memory between invocations.
  const cold=await createCategorizer(options)([...products].reverse());
  assert.equal(reads,1); assert.ok(cold.every(item=>item.nrdCategory==='Mercearia'));
  assert.equal(calls,0);
  reads=0;
  await createCategorizer(options)(products.map((p,i)=>i===0?{...p,description:'Descrição alterada'}:p));
  assert.equal(reads,251); // Revised descriptions cannot inherit another classification.
});

test('classification pipeline persists strict Gemini result and never resends a cached product',async()=>{
  const {createCategorizer}=await import('./categories.mjs');
  const values=new Map(), tasks=[]; let calls=0;
  const cache={many:async keys=>new Map(keys.map(k=>[k,values.get(k)])),
    claim:async(key)=>({payload:values.get(key)?.payload ?? {},version:'claim'}),
    finish:async(key,claim,payload)=>{values.set(key,{payload});return true;},write:async()=>true};
  const product={code:'1',barCode:'3017620422003',description:'CREME DE AVELÃ 350G'};
  const enrich=createCategorizer({cache,apiKey:'synthetic',background:p=>tasks.push(p),fetcher:async(url,options)=>{
    calls++; const request=JSON.parse(options.body);
    assert.equal(request.generationConfig.responseMimeType,'application/json');
    assert.ok(request.generationConfig.responseSchema.items.properties.categoria_correta.enum.includes('Mercearia'));
    return Response.json({candidates:[{content:{parts:[{text:JSON.stringify([{ean:product.barCode,categoria_correta:'Mercearia'}])}]}}]});
  }});
  const initial=await enrich([product]); assert.equal(initial[0].nrdCategory,null);
  await Promise.all(tasks); assert.equal(calls,1);
  assert.equal((await enrich([product]))[0].nrdCategory,'Mercearia');
  await Promise.all(tasks); assert.equal(calls,1);
  assert.equal(await enrich.verify([product]),true); assert.equal(calls,1);
});
