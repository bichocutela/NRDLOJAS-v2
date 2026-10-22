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
  const docs=new Map(await Promise.all(products.map(async item=>[await categoryKey(item),{payload:{category:'Alimentos'},version:'v1'}])));
  let reads=0, calls=0;
  const cache={many:async keys=>{reads+=keys.length;return new Map(keys.filter(key=>docs.has(key)).map(key=>[key,docs.get(key)]));},
    write:async(key,payload)=>{docs.set(key,{payload,version:'v1'});return docs.get(key);}};
  const options={cache,apiKey:'',background:()=>{},fetcher:async()=>{calls++;throw Error('unexpected');}};
  const warm=await createCategorizer(options)(products);
  assert.equal(reads,251); assert.ok(warm.every(item=>item.nrdCategory==='Alimentos'));
  reads=0;
  // Recreate categorizer to simulate losing all Edge memory between invocations.
  const cold=await createCategorizer(options)([...products].reverse());
  assert.equal(reads,1); assert.ok(cold.every(item=>item.nrdCategory==='Alimentos'));
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
    assert.ok(request.generationConfig.responseSchema.items.properties.categoria_correta.enum.includes('Alimentos'));
    return Response.json({candidates:[{content:{parts:[{text:JSON.stringify([{ean:product.barCode,categoria_correta:'Alimentos'}])}]}}]});
  }});
  const initial=await enrich([product]); assert.equal(initial[0].nrdCategory,null);
  await Promise.all(tasks); assert.equal(calls,1);
  assert.equal((await enrich([product]))[0].nrdCategory,'Alimentos');
  await Promise.all(tasks); assert.equal(calls,1);
  assert.equal(await enrich.verify([product]),true); assert.equal(calls,1);
});

test('all sixteen official departments are shared by catalog, classifier and Android',async()=>{
  const {CATEGORIES}=await import('./categories.mjs');
  const {OFFICIAL_DEPARTMENTS}=await import('../nordestao-catalog-resolver/handler.mjs');
  const {VERIFIED_DEPARTMENT_MAP}=await import('./department-map.mjs');
  const {readFile}=await import('node:fs/promises');
  assert.deepEqual(CATEGORIES,[...OFFICIAL_DEPARTMENTS,'Outras ofertas']);
  assert.deepEqual([...new Set(Object.values(VERIFIED_DEPARTMENT_MAP))].sort(),[...OFFICIAL_DEPARTMENTS].sort());
  const source=await readFile(new URL('../../../app/src/main/java/com/example/data/promotions/PromotionCategory.kt',import.meta.url),'utf8');
  const declaration=source.match(/val categories = listOf\(([\s\S]*?)\)/)[1];
  const android=[...declaration.matchAll(/"([^"\n]+)"/g)].map(match=>match[1]);
  assert.deepEqual(android,CATEGORIES);
  for(const category of CATEGORIES) assert.equal(validateCategories([{ean:'test',categoria_correta:category}],
    [{barCode:'test',description:'Produto desconhecido'}])[0].category,category);
});
test('official site departments cannot be overwritten by inferred category or old cache',async()=>{
  const {createCategorizer}=await import('./categories.mjs');
  const official={code:'1',description:'BATATA PALHA',nrdCategory:'Alimentos',catalog_category_source:'official'};
  const cache={many:async()=>new Map(),write:async()=>true};
  const tasks=[];
  const classify=createCategorizer({cache,apiKey:'',background:p=>tasks.push(p)});
  const result=await classify([official,{code:'2',description:'BATATA RUFFLES'}]);
  assert.equal(result[0].nrdCategory,'Alimentos');assert.equal(result[0].catalog_category_source,'official');
  assert.equal(result[1].nrdCategory,'Snacks');assert.equal(result[1].catalog_category_source,'inferred');
  await Promise.all(tasks);
});
test('fallback distinguishes fresh, processed, frozen, baby and adult products',()=>{
  assert.equal(strongCategory('BATATA INGLESA KG'),null);
  assert.equal(strongCategory('BATATA PALHA YOKI 105G'),'Snacks');
  assert.equal(strongCategory('BATATA FRITA CONGELADA 1KG'),'Congelados');
  assert.equal(strongCategory('TOMATE PELADO LATA 400G'),'Alimentos');
  assert.equal(strongCategory('CERVEJA 350ML'),'Bebidas alcoólicas');
  assert.equal(strongCategory('FRALDA INFANTIL 20UN'),'Bebês e crianças');
  assert.equal(strongCategory('FRALDA GERIATRICA 20UN'),'Higiene e beleza');
  assert.equal(strongCategory('PANELA 20CM'),'Bazar');
});

test('verified official or deterministic categories remain ready without an AI request',async()=>{
  const {createCategorizer}=await import('./categories.mjs');
  const classify=createCategorizer({cache:{},apiKey:'',background:()=>{}});
  assert.equal(await classify.verify([{description:'BATATA RUFFLES'}]),true);
  assert.equal(await classify.verify([{description:'Produto',nrdCategory:'Saudáveis',catalog_category_source:'official'}]),true);
  await assert.rejects(classify.verify([{description:'Produto desconhecido'}]),/GEMINI_NOT_CONFIGURED/);
});
