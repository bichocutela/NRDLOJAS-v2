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
