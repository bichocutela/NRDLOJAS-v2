import test from 'node:test';
import assert from 'node:assert/strict';
import { natalCatalog, validDate, assetUrl } from './catalog.mjs';
const now = Date.parse('2026-10-22T12:00:00-03:00');
const item = (id, overrides = {}) => ({id, name:'Encarte', startDate:'2026-10-21T00:00:00', endDate:'2026-10-22T23:59:59',
  uri:'https://datalakeidever.blob.core.windows.net/pdfs/86/a.pdf', image:'https://datalakeidever.blob.core.windows.net/pdfs/86/a.png',
  availableStores:['Nordestão Alecrim'], ...overrides});
test('includes Natal and shared editions, excludes Mossoró-only and unknown stores', () => {
  const result = natalCatalog({tabloids:[item(1),item(2,{availableStores:['Nordestão Doze Anos']}),
    item(3,{availableStores:['Nordestão Doze Anos','Nordestão Nova Parnamirim']}),
    item(4,{availableStores:[' NORDESTAO  IGAPO ']}),item(5,{availableStores:['Unknown']})]},now);
  assert.deepEqual(result.map(x=>x.id),[1,3,4]);
  assert.equal('availableStores' in result[0],false);
});
test('removes source deletions and includes new IDs by replacing snapshots', () => {
  assert.deepEqual(natalCatalog({tabloids:[item(1),item(2)]},now).map(x=>x.id),[1,2]);
  assert.deepEqual(natalCatalog({tabloids:[item(3)]},now).map(x=>x.id),[3]);
  assert.deepEqual(natalCatalog({tabloids:[]},now),[]);
});
test('RN midnight, expiry and future schedules use UTC minus three', () => {
  assert.equal(validDate('2026-10-22T23:59:59'),Date.parse('2026-10-23T02:59:59Z'));
  assert.equal(natalCatalog({tabloids:[item(1)]},Date.parse('2026-10-23T02:59:59Z')).length,1);
  assert.equal(natalCatalog({tabloids:[item(1)]},Date.parse('2026-10-23T03:00:00Z')).length,0);
  assert.equal(natalCatalog({tabloids:[item(1,{startDate:'2026-10-23T00:00:00'})]},now).length,0);
  assert.equal(natalCatalog({tabloids:[item(1,{endDate:'invalid'})]},now).length,0);
});
test('allowlists assets and deduplicates IDs, retains equally named distinct editions', () => {
  assert.equal(assetUrl('https://evil.example/a.pdf','.pdf'),null);
  assert.equal(assetUrl('http://datalakeidever.blob.core.windows.net/a.pdf','.pdf'),null);
  assert.equal(assetUrl('https://user@datalakeidever.blob.core.windows.net/a.pdf','.pdf'),null);
  assert.equal(natalCatalog({tabloids:[item(1),item(1),item(2),item(3,{uri:'https://evil.example/x.pdf'})]},now).length,2);
});
test('sorts recent campaigns first and fails closed on malformed source', () => {
  assert.deepEqual(natalCatalog({tabloids:[item(1),item(2,{startDate:'2026-10-22T00:00:00'})]},now).map(x=>x.id),[2,1]);
  assert.throws(()=>natalCatalog({}),/Invalid catalog/);
});
