import { test } from 'node:test';
import assert from 'node:assert/strict';
import { trainingNotice, publishTraining } from './training-notification.mjs';
const payload = JSON.stringify({ missions: [{ id: 'lesson-1', title: 'Abertura de caixa', enabled: true }, { id: 'disabled', title: 'Oculto', enabled: false }] });
test('only published, enabled saved content can create a notification', () => {
  assert.equal(trainingNotice('ALL', payload, 'lesson-1').title, 'Novo treinamento: Abertura de caixa');
  for (const mode of ['TEST', 'OFF', undefined]) assert.throws(() => trainingNotice(mode, payload, 'lesson-1'));
  for (const id of ['disabled', 'missing', '../secret']) assert.throws(() => trainingNotice('ALL', payload, id));
});
const response = (body, status = 200) => new Response(JSON.stringify(body), { status });
function scenario({ visibility = 'ALL', existing = false, failedSend = false, pending = false } = {}) {
  const calls = [];
  const fetcher = async (url, opts = {}) => {
    calls.push({ url, ...opts });
    if (url.endsWith('/config/training')) return response({ fields: { visibility: { stringValue: visibility }, payload: { stringValue: payload } } });
    if (opts.method === 'POST' && url.includes('training_announcements?')) return response({}, existing ? 409 : 200);
    if (!opts.method && url.endsWith('/lesson-1')) return response({ fields: { status: { stringValue: pending ? 'pending' : 'sent' }, createdAt: { integerValue: '1000' } }, updateTime: '2026-01-01T00:00:00Z' });
    if (url.includes('fcm.googleapis.com')) return response({}, failedSend ? 503 : 200);
    return response({});
  };
  return { calls, execute: (role = 'mestre') => publishTraining({ role, id: 'lesson-1', project: 'test', firestoreToken: 'private', messagingToken: 'private', fetcher, now: 2000 }) };
}
test('admin and anonymous roles cannot access content or send notices', async () => {
  for (const role of ['admin', null, 'user']) { const s = scenario(); assert.equal((await s.execute(role)).status, 403); assert.equal(s.calls.length, 0); }
});
test('TEST cannot notify public topic', async () => {
  const s = scenario({ visibility: 'TEST' }); assert.equal((await s.execute()).status, 400); assert.equal(s.calls.length, 1);
});
test('public announcement sends data only once and marks the authoritative ID', async () => {
  const s = scenario(); assert.equal((await s.execute()).result.ok, true);
  const push = JSON.parse(s.calls.find(c => c.url.includes('fcm.googleapis.com')).body).message;
  assert.equal(push.topic, 'training_updates'); assert.equal(push.notification, undefined);
  assert.equal(push.data.trainingId, 'lesson-1'); assert.equal(push.data.type, 'NEW_TRAINING');
  assert.equal(JSON.parse(s.calls.at(-1).body).fields.status.stringValue, 'sent');
});
test('duplicate public announcements do not send FCM', async () => {
  const s = scenario({ existing: true }); assert.equal((await s.execute()).result.alreadyAnnounced, true);
  assert.equal(s.calls.some(c => c.url.includes('fcm.googleapis.com')), false);
});
test('simultaneous requests respect pending lease', async () => {
  const s = scenario({ existing: true, pending: true }); assert.equal((await s.execute()).status, 409);
  assert.equal(s.calls.some(c => c.url.includes('fcm.googleapis.com')), false);
});
test('failed sends release reservation for a safe retry', async () => {
  const s = scenario({ failedSend: true }); await assert.rejects(s.execute()); assert.equal(s.calls.at(-1).method, 'DELETE');
});
