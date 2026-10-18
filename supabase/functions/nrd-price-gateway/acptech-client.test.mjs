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
    {ok: true, status: 200, body: [{id: 'item-1', campaign_id: 'camp-1', code: '0012', barcode: '789', description: 'Produto teste', image_url: 'https://cdn.test/item.png', price: 4.99, previous_price: 5.99}]},
    {ok: true, status: 200, body: []}
  ];
  const client = createAcpTechClient({
    boundedJson: async response => response.body,
    credentials: () => ({login: 'nordestao12', password: 'not-a-real-password'}),
    config: {anonKey: 'anon-test', storeSlug: 'nordestao12'},
    fetcher: async (url, options) => {
      requests.push({url: String(url), options});
      return responses.shift() ?? {ok:true, status:200, body:[]};
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

function fixture({products = [], campaigns = [], items = [], categories = [], overrides = [], categoryProducts = {}, cap = 250, failItems = false, campaignRpcEmpty = false} = {}) {
  const requests = [];
  const now = () => Date.parse('2026-10-18T19:00:00Z');
  const client = createAcpTechClient({now, boundedJson: async response => response.body,
    credentials: () => ({login:'mock', password:'mock'}),
    fetcher: async (url, options = {}) => {
      const request = new URL(url); requests.push({url:request, options});
      const path = request.pathname;
      if (path.endsWith('/store-auth')) return {ok:true, body:JSON.parse(options.body).action === 'resolve_email'
        ? {email:'mock@example.invalid'} : {user:{store_id:'s', chain_id:'c'}}};
      if (path.includes('/auth/v1/token')) return {ok:true, body:{access_token:'mock'}};
      if (path.endsWith('/rpc/count_chain_products')) return {ok:true, body:(categoryProducts[JSON.parse(options.body).p_category_id] ?? products).length};
      if (path.endsWith('/rpc/search_chain_products')) {
        const body = JSON.parse(options.body);
        if (campaignRpcEmpty && body.p_codes?.length) return {ok:true, body:[]};
        return {ok:true, body:(categoryProducts[body.p_category_id] ?? products).slice(body.p_offset, body.p_offset + body.p_limit)};
      }
      if (failItems && path.endsWith('/campaign_items')) return {ok:false, status:500, body:{error:'mock'}};
      let all = path.endsWith('/campaigns') ? campaigns : path.endsWith('/campaign_items') ? items :
        path.endsWith('/product_categories') ? categories : path.endsWith('/store_product_price_overrides') ? overrides : products;
      if (request.searchParams.has('or') && path.endsWith('/store_product_price_overrides')) {
        all = all.filter(row => Number(row.previous_price) > 0 || Number(row.club_price) > 0 || (Number(row.leve_quantity) > 0 && Number(row.pague_quantity) > 0));
      }
      if (request.searchParams.has('or') && path.endsWith('/chain_products')) {
        const filters = request.searchParams.get('or');
        const values = key => {
          const match = filters.match(new RegExp(`${key}\\.in\\.\\(([^)]*)\\)`));
          return match ? [...match[1].matchAll(/"([^"]+)"/g)].map(value => value[1]) : [];
        };
        const ids = values('id'), codes = values('code'), barcodes = values('barcode');
        all = all.filter(row => ids.includes(String(row.id)) || codes.includes(String(row.code)) || barcodes.includes(String(row.barcode)));
      }
      if (request.searchParams.has('id')) {
        const ids = JSON.parse('[' + request.searchParams.get('id').slice(4, -1) + ']');
        all = all.filter(row => ids.includes(String(row.id)));
      }
      const offset = Number(request.searchParams.get('offset') || 0);
      const limit = Math.min(Number(request.searchParams.get('limit') || 250), cap);
      return {ok:true, status:200, body:all.slice(offset, offset + limit),
        headers:new Headers({'content-range':`${offset}-${Math.max(offset, Math.min(all.length, offset + limit) - 1)}/${all.length}`})};
    }});
  return {client, requests};
}
const consult = (client, path = 'Product/all', parameters = [['code','123']]) => client.consult({path, parameters});

test('product search returns current campaign price instead of a positive normal price', async () => {
  const {client} = fixture({products:[{id:'p', code:123, description:'Teste', price:10, previous_price:0}],
    campaigns:[{id:'c', start_date:'2026-10-16', end_date:'2026-10-18'}],
    items:[{id:'i', campaign_id:'c', code:123, price:6, previous_price:10}]});
  const result = (await consult(client)).items[0];
  assert.equal(result.value, 6);
  assert.equal(result.previousValue, 10);
  assert.ok(result.productCategories.some(category => category.description === 'De-Por'));
});

test('Campaign/all preserves separate club and multi-buy rules using the existing Android contract', async () => {
  const {client} = fixture({campaigns:[{id:'c1', name:'Atual', start_date:'2026-10-01', end_date:'2026-10-31'},
    {id:'future', name:'Futura', start_date:'2026-10-19', end_date:'2026-10-31'}],
    items:[{id:'club', campaign_id:'c1', code:123, price:10, club_price:5},
      {id:'take', campaign_id:'c1', code:123, price:12, leve_quantity:3, pague_quantity:2},
      {id:'future', campaign_id:'future', code:123, price:2}]});
  const result = await consult(client, 'Campaign/all', []);
  assert.equal(result.items.length, 2);
  const active = result.items.find(campaign => campaign.id === 'c1');
  assert.equal(active.products[0].clubValue, 5);
  assert.equal(active.products[1].value, 12);
  assert.equal(active.products[1].quantityTake, 3);
  assert.equal(active.products[1].quantityPay, 2);
});

test('campaign item inherits the store club override and campaign validity by product identity', async () => {
  const {client} = fixture({
    campaignRpcEmpty: true,
    products:[{id:'p1', code:2000451, barcode:'7896106900053', description:'Açúcar Triturado Dumel 1kg', price:4.19}],
    overrides:[{chain_product_id:'p1', price:4.19, club_price:2.89}],
    campaigns:[{id:'c1', code:'1476', name:'NORDCARD', start_date:'2026-10-19', end_date:'2026-10-21'}],
    // ACP's campaign row may expose a different/legacy barcode. A unique ACP
    // product code remains the primary identity and still finds its override.
    items:[{id:'i1', campaign_id:'c1', code:2000451, barcode:'1137989', description:'Açúcar Triturado Dumel',
      price:0, previous_price:3.79}]
  });
  const campaign = (await consult(client, 'Campaign/all', [])).items[0];
  const product = campaign.products[0];
  assert.equal(product.value, 4.19);
  assert.equal(product.clubValue, 2.89);
  assert.equal(product.previousValue, 3.79);
  assert.equal(product.startDate, '2026-10-19');
  assert.equal(product.endDate, '2026-10-21');
  assert.ok(product.productCategories.some(category => category.description === 'Clube de Vantagens'));
});

test('promotions include club and take/pay from catalogue categories even without campaigns', async () => {
  const {client, requests} = fixture({categories:[{id:'club', name:'Clube de Vantagens'}, {id:'take', name:'Leve-Pague'}],
    categoryProducts:{club:[{id:'p1', code:123, description:'Clube', price:10, club_price:5}],
      take:[{id:'p2', code:456, description:'Leve', price:12, leve_quantity:3, pague_quantity:2}]}});
  const result = await consult(client, 'Promotion/all', []);
  assert.equal(result.totalCount, 2);
  assert.equal(result.items.find(item => item.id === 'p1').clubValue, 5);
  assert.equal(result.items.find(item => item.id === 'p2').quantityTake, 3);
  assert.equal(requests.filter(row => row.url.pathname.endsWith('/rpc/search_chain_products')).length, 2);
});

test('upstream page caps do not truncate categories or campaign items', async () => {
  const {client, requests} = fixture({cap:2, campaigns:[{id:'c'}],
    items:Array.from({length:5}, (_, i) => ({id:`i${i}`, campaign_id:'c', code:i, price:5, previous_price:10}))});
  const result = await consult(client, 'Promotion/all', []);
  assert.equal(result.totalCount, 5);
  assert.deepEqual(requests.filter(row => row.url.pathname.endsWith('/campaign_items')).map(row => row.url.searchParams.get('offset')), ['0','2','4']);
});

test('campaign failure fails the query instead of silently returning only normal prices', async () => {
  const {client} = fixture({products:[{id:'p', code:123, price:10}], campaigns:[{id:'c'}], failItems:true});
  await assert.rejects(() => consult(client), /ACPTECH_UPSTREAM_FAILURE/);
});

test('unfiltered catalogue stays complete and applies store prices and stock aliases', async () => {
  const {client, requests} = fixture({products:[{id:'p', code:123, price:10, stock:110, unit:'cada'}],
    overrides:[{chain_product_id:'p', price:9, club_price:7}]});
  const result = await consult(client, 'Product/all', [['pageIndex','0'], ['pageSize','250']]);
  assert.equal(result.totalCount, 1);
  assert.equal(result.items[0].value, 9);
  assert.equal(result.items[0].clubValue, 7);
  assert.equal(result.items[0].stockQuantity, 110);
  assert.deepEqual(result.items[0].unit, {description:'cada'});
  const query = requests.find(row => row.url.pathname.endsWith('/chain_products')).url;
  assert.equal(query.searchParams.has('category_id'), false);
  assert.equal(query.searchParams.get('chain_id'), 'eq.c');
});

test('catalogue-only conditions are also visible through existing Campaign/all product rules', async () => {
  const {client} = fixture({categories:[{id:'take', name:'Leve-Pague'}],
    categoryProducts:{take:[{id:'p', code:123, description:'Teste', price:12, leve_quantity:3, pague_quantity:2}]}});
  const result = await consult(client, 'Campaign/all', []);
  assert.equal(result.items[0].name, 'Condições do cadastro de produtos');
  assert.equal(result.items[0].products[0].value, 12);
  assert.equal(result.items[0].products[0].quantityTake, 3);
});

