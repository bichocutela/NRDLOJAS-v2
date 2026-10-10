import {test} from 'node:test';
import assert from 'node:assert/strict';
import {buildCatalogIndex, enrichPromotion, createHandler, normalizeText, resolveCatalogItem, resolvePromotions} from './handler.mjs';

const catalog = [
  {produto_id: 54487, codigo_interno: 'ACP-1001', descricao: 'Chocolate Nestlé Recheados Prestígio 90g', slug: 'chocolate-nestle-recheados-prestigio-90g', imagens: [{src: 'https://cdn.example.test/54487.jpg'}]},
  {produto_id: 54488, codigo_interno: 'ACP-1002', descricao: 'Chocolate Nestlé Recheados Charge 90g', slug: 'chocolate-nestle-recheados-charge-90g', imagem: 'https://cdn.example.test/54488.jpg'},
  {produto_id: 60001, codigo_interno: 'ACP-1003', descricao: 'Leite Integral Nordestão 1L', slug: 'leite-integral-nordestao-1l', imagem: 'https://cdn.example.test/60001.jpg'}
];

test('normalizes accents, punctuation and whitespace', () => {
  assert.equal(normalizeText('  Prestígio — 90g  '), 'prestigio 90g');
});

test('matches exact internal code and preserves leading zeroes', () => {
  const index = buildCatalogIndex([{...catalog[0], codigo_interno: '00123'}]);
  const result = resolveCatalogItem({codproduto: '00123', desc_prod: 'texto diferente'}, index);
  assert.equal(result.matched, true);
  assert.equal(result.matchType, 'internal_code');
  assert.equal(result.product.productId, '54487');
});

test('falls back to a high-confidence description match and returns image/link', () => {
  const result = resolveCatalogItem({codproduto: 'UNKNOWN', desc_prod: 'Chocolate Nestlé Recheados Prestígio 90g'}, buildCatalogIndex(catalog));
  assert.equal(result.matched, true);
  assert.equal(result.matchType, 'description');
  assert.equal(result.product.productId, '54487');
  assert.equal(result.product.imageUrl, 'https://cdn.example.test/54487.jpg');
  assert.match(result.product.productUrl, /produto\/54487\/chocolate-nestle-recheados-prestigio-90g$/);
});

test('does not guess between similar products', () => {
  const result = resolveCatalogItem({desc_prod: 'Chocolate Nestlé Recheados 90g'}, buildCatalogIndex(catalog));
  assert.equal(result.matched, false);
  assert.equal(result.matchType, 'ambiguous');
  assert.equal(result.candidates.filter(candidate => candidate.score > 0.5).length, 2);
});

test('prefers the official catalog image and preserves fallback fields when unavailable', () => {
  const [result] = resolvePromotions([
    {codproduto: 'ACP-1001', desc_prod: catalog[0].descricao, imagem: 'https://existing.test/image.jpg'}
  ], catalog);
  assert.equal(result.promotion.imagem, 'https://cdn.example.test/54487.jpg');
  assert.match(result.promotion.linkloja, /produto\/54487/);
  assert.equal(result.promotion.catalog_match_type, 'internal_code');
});

test('handler loads catalog once per request and returns match diagnostics', async () => {
  let loads = 0;
  const handler = createHandler({loadCatalog: async () => { loads++; return catalog; }});
  const response = await handler(new Request('https://resolver.test', {
    method: 'POST',
    body: JSON.stringify({items: [{codproduto: 'ACP-1003', desc_prod: 'Leite Integral Nordestão 1L'}]}),
    headers: {'content-type': 'application/json'}
  }));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(loads, 1);
  assert.equal(body.items[0].catalog_product_id, '60001');
  assert.equal(body.matches[0].matchType, 'internal_code');
});


test('EAN exact match carries the official department and rejects conflicting barcodes',()=>{
  const index=buildCatalogIndex([{produto_id:'3653',codigo_interno:'2021000',codigo_barras:'7891149840878',descricao:'Beats G&T 269ml',department:'Bebidas alcoólicas',sectionId:'337',imagem:'https://cdn.example.test/beats.jpg'}]);
  const matched=resolveCatalogItem({code:'unknown',barCode:'7891149840878'},index);
  assert.equal(matched.matchType,'ean');
  const promotion=enrichPromotion({code:'unknown',nrdCategory:'Mercearia'},matched);
  assert.equal(promotion.nrdCategory,'Bebidas alcoólicas');assert.equal(promotion.catalog_category_source,'official');
  assert.equal(resolveCatalogItem({code:'2021000',barCode:'7891149840000',description:'Beats G&T 269ml'},index).matched,false);
});
test('description matching rejects incompatible sizes and identical ambiguous candidates',()=>{
  const products=[{produto_id:'1',descricao:'Chocolate Marca Especial 90g',imagem:'https://cdn.example.test/1.jpg'},
    {produto_id:'2',descricao:'Chocolate Marca Especial 90g',imagem:'https://cdn.example.test/2.jpg'}];
  assert.equal(resolveCatalogItem({description:'Chocolate Marca Especial 90g'},buildCatalogIndex(products)).matched,false);
  assert.equal(resolveCatalogItem({description:'Chocolate Marca Especial 100g'},buildCatalogIndex([products[0]])).matched,false);
});
