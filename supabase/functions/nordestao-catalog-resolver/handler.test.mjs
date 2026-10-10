import {test} from 'node:test';
import assert from 'node:assert/strict';
import {buildCatalogIndex, createHandler, normalizeText, resolveCatalogItem, resolvePromotions} from './handler.mjs';

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
