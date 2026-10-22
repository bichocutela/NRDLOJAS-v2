import test from 'node:test';
import assert from 'node:assert/strict';
import { notificationPayload, notifyCatalog, TOPIC } from './notifications.mjs';
const item = { id: 66738, name: 'Encarte Nordestão Saudável', startDate: '2026-10-22T00:00:00', endDate: '2099-10-31T23:59:59' };
test('one fixed topic carries complete title, validity, stable ID and routing type', () => {
 const data = notificationPayload(item, Date.parse('2026-10-22T12:00:00-03:00'));
 assert.equal(data.title,'Novo Encarte: Encarte Nordestão Saudável');
 assert.match(data.body,/22\/10 a 31\/10/); assert.equal(data.type,'NEW_ENCARTE');
 assert.equal(data.encarteId,'66738'); assert.equal(TOPIC,'encartes_natal');
 assert.throws(()=>notificationPayload({...item,endDate:'2020-01-01T00:00:00'}));
});
test('only newly claimed events are sent; known items are not replayed', async () => {
 const sent=[],ack=[];
 const result=await notifyCatalog([item], {claim:async rows => [{id:rows[0].id,payload:rows[0],claim_id:'lease'}],
 send:async data=>sent.push(data),acknowledge:async event=>ack.push(event.id),release:async()=>{throw Error('unexpected release');}});
 assert.equal(result.sent,1); assert.equal(sent.length,1); assert.deepEqual(ack,[66738]);
 const second=await notifyCatalog([item], {claim:async()=>[],send:async()=>{throw Error('duplicate');}});
 assert.equal(second.sent,0);
});
test('baseline seeds current catalog without notification and failure releases for retry', async () => {
 await notifyCatalog([item], {baseline:true,claim:async(rows,baseline)=>{assert.equal(baseline,true);assert.equal(rows[0].expiresAt,'2099-11-01T02:59:59.000Z');return[];},send:async()=>{throw Error('baseline must not notify');}});
 let released=0,acked=0;
 await assert.rejects(()=>notifyCatalog([item], {claim:async()=>[{id:item.id,payload:item,claim_id:'lease'}],
 send:async()=>{throw Error('FCM down');},acknowledge:async()=>acked++,release:async()=>released++}),/FCM down/);
 assert.equal(acked,0); assert.equal(released,1);
});
