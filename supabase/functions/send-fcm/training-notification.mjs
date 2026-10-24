/** Authoritative saved content only; the client supplies just a training ID. */
export function trainingNotice(visibility, raw, id) {
  if (!/^[A-Za-z0-9_-]{1,100}$/.test(id ?? '')) throw new Error('Identificador de treinamento inválido');
  if (visibility !== 'ALL') throw new Error('O Treinamento precisa estar ativado para todos');
  const config = JSON.parse(raw);
  const mission = config.missions?.find(m => m.id === id && m.enabled === true);
  if (!mission || typeof mission.title !== 'string' || !mission.title.trim() || mission.title.length > 160)
    throw new Error('Treinamento indisponível ou título inválido');
  return { title: `Novo treinamento: ${mission.title.trim()}`, body: 'Toque para abrir a Academia NRD.',
    type: 'NEW_TRAINING', trainingId: id };
}

export async function publishTraining({ role, id, project, firestoreToken, messagingToken, fetcher = fetch, now = Date.now(), notifyWeb = async () => {} }) {
  if (role !== 'mestre') return { status: 403, result: { ok: false, error: 'Somente o Mestre pode publicar treinamentos' } };
  if (!/^[A-Za-z0-9_-]{1,100}$/.test(id ?? '')) return { status: 400, result: { ok: false, error: 'Identificador inválido' } };
  const base = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents`;
  const headers = { Authorization: `Bearer ${firestoreToken}`, 'Content-Type': 'application/json' };
  const saved = await fetcher(`${base}/config/training`, { headers });
  if (!saved.ok) throw new Error('Não foi possível carregar o Treinamento salvo');
  const fields = (await saved.json()).fields;
  let data;
  try { data = trainingNotice(fields?.visibility?.stringValue, fields?.payload?.stringValue, id); }
  catch (error) { return { status: 400, result: { ok: false, error: error.message } }; }
  const ref = `${base}/training_announcements/${id}`;
  const pending = { fields: { status: { stringValue: 'pending' }, createdAt: { integerValue: String(now) } } };
  let claimed = await fetcher(`${base}/training_announcements?documentId=${encodeURIComponent(id)}`, { method: 'POST', headers, body: JSON.stringify(pending) });
  if (claimed.status === 409) {
    const old = await fetcher(ref, { headers });
    if (!old.ok) throw new Error('Não foi possível conferir o aviso anterior');
    const record = await old.json();
    if (record.fields?.status?.stringValue === 'sent') return { status: 200, result: { ok: true, alreadyAnnounced: true } };
    if (now - Number(record.fields?.createdAt?.integerValue ?? now) < 300_000) return { status: 409, result: { ok: false, error: 'O aviso já está sendo enviado. Tente novamente em alguns minutos.' } };
    claimed = await fetcher(`${ref}?currentDocument.updateTime=${encodeURIComponent(record.updateTime)}`, { method: 'PATCH', headers, body: JSON.stringify(pending) });
  }
  if (!claimed.ok) throw new Error('Não foi possível reservar o envio do aviso');
  try {
    const sent = await fetcher(`https://fcm.googleapis.com/v1/projects/${project}/messages:send`, {
      method: 'POST', headers: { Authorization: `Bearer ${messagingToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: { topic: 'training_updates', data, android: { priority: 'high', ttl: '3600s' } } })
    });
    if (!sent.ok) throw new Error(`Falha ao enviar aviso (HTTP ${sent.status})`);
    await notifyWeb(data);
    const marked = await fetcher(ref, { method: 'PATCH', headers, body: JSON.stringify({ fields: { status: { stringValue: 'sent' }, createdAt: { integerValue: String(now) } } }) });
    if (!marked.ok) throw new Error('Aviso enviado, mas a confirmação falhou. Aguarde antes de tentar novamente.');
    return { status: 200, result: { ok: true, alreadyAnnounced: false } };
  } catch (error) {
    // Clients also deduplicate by mission ID if FCM accepted a request before a timeout.
    await fetcher(ref, { method: 'DELETE', headers });
    throw error;
  }
}
