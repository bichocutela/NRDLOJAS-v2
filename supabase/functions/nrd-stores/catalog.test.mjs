import { test } from 'node:test';
import assert from 'node:assert/strict';
import { storeCatalog } from './catalog.mjs';
const store = (extra = {}) => ({id: 1, name: 'Nordestão Igapó', businessHours: '', phones:['8436154806'], address:{street:'Rua',number:'26',latitude:-5.77,longitude:-35.24},...extra});
test('preserves source hours, coordinates and phones without inventing WhatsApp or hours', () => {
 const result = storeCatalog({stores:[store()]})[0];
 assert.equal(result.businessHours, ''); assert.deepEqual(result.phones, ['8436154806']); assert.equal(result.address.latitude,-5.77);
 assert.equal(result.isOpen,null); assert.equal(result.capacity,null); assert.equal(result.whatsapp,undefined);
});
test('exposes only store fields and respects capacity visibility', () => {
 const result = storeCatalog({stores:[store({token:'secret',user:{cpf:'private'},isOpen:false,capacity:10,showCapacity:true})]})[0];
 assert.equal(result.token,undefined); assert.equal(result.user,undefined); assert.equal(result.capacity,10); assert.equal(result.isOpen,false);
 assert.equal(storeCatalog({stores:[store({capacity:10,showCapacity:false})]})[0].capacity,null);
});
test('invalid snapshots fail instead of silently deleting source items', () => {
 assert.throws(()=>storeCatalog({})); assert.throws(()=>storeCatalog({stores:[store(),store()]})); assert.throws(()=>storeCatalog({stores:[store({id:0})]}));
});
test('empty source removes stores and invalid location cannot create map action',()=>{
 assert.deepEqual(storeCatalog({stores:[]}),[]);
 assert.equal(storeCatalog({stores:[store({address:{latitude:999,longitude:1}})]})[0].address.latitude,null);
});
test('deterministic ordering, source edits, phone filtering and no fixed store list',()=>{
 const result=storeCatalog({stores:[store({id:2,name:'Zulu',phones:['invalid','8436154806','8436154806']}),store({name:'Alecrim',businessHours:'Segunda: 8h–20h'})]});
 assert.deepEqual(result.map(x=>x.id),[1,2]); assert.equal(result[0].businessHours,'Segunda: 8h–20h'); assert.equal(result[1].phones.length,1);
});
