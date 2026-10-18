import test from 'node:test';
import assert from 'node:assert/strict';
import {createAcpTechClient} from './acptech-client.mjs';

test('ACP Tech resolve username, autentica e retorna itens de campanhas', async () => {
  const requests = [];
  const responses = [
    {ok: true, status: 200, body: {email: 'resolved@example.invalid'}},
    {ok: true, status: 200, body: {access_token: 'jwt-test'}},
    {ok: true, status: 200, body: {user: {store_id: 'store-12', chain_id: 'chain-1'}}},
    {ok: true, status: 200, body: [{id: 'camp-1', name: 'Ofertas', active: true}]},
    {ok: true, status: 200, body: [{id: 'item-1', campaign_id: 'camp-1', code: '0012', barcode: '789', description: 'Produto teste', image_url: 'https://cdn.test/item.png', price: 4.99, previous_price: 5.99}]}
  ];
  const client = createAcpTechClient({
    boundedJson: async response => response.body,
    credentials: () => ({login: 'nordestao12', password: 'not-a-real-password'}),
    config: {anonKey: 'anon-test', storeSlug: 'nordestao12'},
    fetcher: async (url, options) => {
      requests.push({url: String(url), options});
      return responses.shift();
    }
  });

  const result = await client.consult({path: 'Promotion/all', parameters: [['pageIndex', '0'], ['pageSize', '20']]});
  assert.equal(result.totalCount, 1);
  assert.equal(result.items[0].code, '0012');
  assert.equal(result.items[0].value, 4.99);
  assert.equal(result.items[0].previousValue, 5.99);
  assert.deepEqual(result.items[0].productCategories, [{description: 'De-Por'}]);
  assert.equal(result.items[0].imageUrl, 'https://cdn.test/item.png');
  assert.match(requests[0].url, /functions\/v1\/store-auth/);
  assert.match(requests[1].url, /auth\/v1\/token\?grant_type=password/);
  assert.match(requests[3].url, /rest\/v1\/campaigns/);
  assert.match(requests[4].url, /rest\/v1\/campaign_items/);
  assert.equal(requests[4].options.headers.Authorization, 'Bearer jwt-test');
});

test('ACP Tech usa a chave pública padrão quando nenhuma chave é informada', async () => {
  const client = createAcpTechClient({
    boundedJson: async response => response.body,
    credentials: () => ({login: 'user', password: 'password'}),
    fetcher: async (url, options) => {
      assert.match(options.headers.apikey, /^sb_publishable_/);
      return {ok: false, status: 500, body: {error: 'expected mock failure'}};
    }
  });
  await assert.rejects(() => client.consult({path: 'Promotion/all', parameters: [['pageIndex', '0'], ['pageSize', '1']]}), /ACPTECH_UPSTREAM_FAILURE/);
});

test('ACP Tech resolve chain_id pela loja antes de consultar produtos', async () => {
  const requests = [];
  const responses = [
    {ok: true, status: 200, body: {email: 'resolved@example.invalid'}},
    {ok: true, status: 200, body: {access_token: 'jwt-test'}},
    {ok: true, status: 200, body: {user: {store_id: 'store-12', chain_id: null}}},
    {ok: true, status: 200, body: [{chain_id: 'chain-1'}]},
    {ok: true, status: 200, body: [{code: 2021000, description: 'Produto teste', image_url: ''}]},
    {ok: true, status: 200, body: []}
  ];
  const client = createAcpTechClient({
    boundedJson: async response => response.body,
    credentials: () => ({login: 'nordestao12', password: 'not-a-real-password'}),
    config: {anonKey: 'anon-test', storeSlug: 'nordestao12'},
    fetcher: async (url, options) => {
      requests.push({url: String(url), options});
      return responses.shift();
    }
  });

  const result = await client.consult({path: 'Product/all', parameters: [['code', '2021000'], ['pageIndex', '0'], ['pageSize', '10']]});
  assert.equal(result.totalCount, 1);
  assert.ok(requests.some(request => /rest\/v1\/stores\?/.test(request.url)));
  const productsRequest = requests.find(request => /rest\/v1\/rpc\/search_chain_products/.test(request.url));
  assert.ok(productsRequest);
  assert.equal(JSON.parse(productsRequest.options.body).p_chain_id, 'chain-1');
});

test('ACP Tech usa offset e total do PostgREST para páginas de produtos', async () => {
  const requests = [];
  const responses = [
    {ok: true, status: 200, body: {email: 'resolved@example.invalid'}},
    {ok: true, status: 200, body: {access_token: 'jwt-test'}},
    {ok: true, status: 200, body: {user: {store_id: 'store-12', chain_id: 'chain-1'}}},
    {ok: true, status: 200, headers: new Headers({'content-range': '20-29/87'}), body: [{code: 2020020, description: 'Página 2'}]},
    {ok: true, status: 200, body: 87},
    {ok: true, status: 200, body: []}
  ];
  const client = createAcpTechClient({
    boundedJson: async response => response.body,
    credentials: () => ({login: 'nordestao12', password: 'not-a-real-password'}),
    config: {anonKey: 'anon-test', storeSlug: 'nordestao12'},
    fetcher: async (url, options) => { requests.push({url: String(url), options}); return responses.shift(); }
  });
  const result = await client.consult({path: 'Product/all', parameters: [['pageIndex', '2'], ['pageSize', '10']]});
  assert.equal(result.pageIndex, 2);
  assert.equal(result.totalCount, 87);
  assert.equal(result.totalPages, 9);
  const productsRequest = requests.find(request => /rest\/v1\/chain_products\?/.test(request.url));
  assert.match(productsRequest.url, /offset=20/);
});
