import { test } from 'node:test';
import assert from 'node:assert/strict';
import { offerCatalog, activeOffers, sourceDate } from './catalog.mjs';
const campaign = items => ({ promotions:[{ id:1,status:'A',type:'A',startDate:'2026-10-23T00:00:00',endDate:'2026-10-23T23:59:00',items }] });
const item = overrides => ({id:2,description:'Produto',price:2.31,originalPrice:5.79,amount:6,categoryName:'Bebidas',...overrides});
test('uses local Fortaleza dates and removes expired or future campaigns', () => {
  const offers=offerCatalog(campaign([item({})]));
  assert.equal(sourceDate('2026-10-23T00:00:00'),'2026-10-23T03:00:00.000Z');
  assert.equal(activeOffers(offers,Date.parse('2026-10-23T03:00Z')).length,1);
  assert.equal(activeOffers(offers,Date.parse('2026-10-23T02:59Z')).length,0);
  assert.equal(activeOffers(offers,Date.parse('2026-10-24T03:00Z')).length,0);
});
test('cashback and multi-buy zero prices never become free products', () => {
  const [discount,cashback,multibuy,special]=offerCatalog(campaign([
    item({}), item({id:3,price:0,originalPrice:5.29,cashbackRule:{value:30,calculationType:'%'}}),
    item({id:4,price:0,originalPrice:7.19,rules:[{message:'Leve 3 pague 2'}]}),
    item({id:5,price:0,indexibirPrecoBase:false})]));
  assert.equal(discount.priceCents,231); assert.equal(discount.discount,60);
  assert.equal(cashback.priceCents,529); assert.equal(cashback.cashback,'30% de cashback'); assert.equal(cashback.discount,0);
  assert.equal(multibuy.priceCents,719); assert.match(multibuy.conditions,/Leve 3 pague 2/);
  assert.equal(special.showPrice,false);
});
test('keeps all items including families, without copying personal activation state', () => {
  const result=offerCatalog(campaign(Array.from({length:136},(_,id)=>item({id,type:'Family',isActivated:true,images:['http://unsafe','https://cdn.test/semImagem.jpg','https://cdn.test/product.jpg']}))));
  assert.equal(result.length,136); assert.equal(result[0].image,'https://cdn.test/product.jpg');
  assert.equal(result[0].isActivated,undefined); assert.equal(result[0].activationRequired,true);
});
test('malformed upstream does not become an empty successful catalog', () => {
  assert.throws(()=>offerCatalog({})); assert.throws(()=>offerCatalog(campaign([{id:1}])));
  assert.deepEqual(offerCatalog({promotions:[]}),[]);
});
test('preserves every official image in source order for client fallback without guessing URLs', () => {
  const images=['https://images.idever.io/first.png','https://cdn.test/backup.jpg'];
  const [offer]=offerCatalog(campaign([item({images:[null,' http://unsafe ', 'https://cdn.test/semImagem.png',
    ' '+images[0]+' ',images[1],images[0]]})]));
  assert.equal(offer.image,images[0]);
  assert.deepEqual(offer.images,images);
  assert.deepEqual(offerCatalog(campaign([item({images:[]})]))[0].images,[]);
});
