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
    {ok: true, status: 200, body: [{id: 'item-1', campaign_id: 'camp-1', code: '0012', barcode: '789', description: 'Produto teste', image_url: 'https://cdn.test/item.png', price: 4.99}]}
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
  assert.equal(result.items[0].imageUrl, 'https://cdn.test/item.png');
  assert.match(requests[0].url, /functions\/v1\/store-auth/);
  assert.match(requests[1].url, /auth\/v1\/token\?grant_type=password/);
  assert.match(requests[3].url, /rest\/v1\/campaigns/);
  assert.match(requests[4].url, /rest\/v1\/campaign_items/);
  assert.equal(requests[4].options.headers.Authorization, 'Bearer jwt-test');
});

test('ACP Tech falha fechado sem chave pública configurada', async () => {
  const client = createAcpTechClient({
    boundedJson: async response => response.body,
    credentials: () => ({login: 'user', password: 'password'}),
    fetcher: async () => { throw new Error('network must not be reached'); }
  });
  await assert.rejects(() => client.consult({path: 'Promotion/all', parameters: [['pageIndex', '0'], ['pageSize', '1']]}), /ANON_KEY_NOT_CONFIGURED/);
});
