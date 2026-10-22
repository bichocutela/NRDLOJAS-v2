import { validDate } from './catalog.mjs';
export const TOPIC = 'encartes_natal';
export function notificationPayload(item, now = Date.now()) {
  if (!Number.isSafeInteger(item?.id) || item.id <= 0 || !item.name?.trim() ||
      !(validDate(item.startDate) <= now && validDate(item.endDate) >= now)) throw new Error('Invalid leaflet');
  const validity = `${item.startDate.slice(8,10)}/${item.startDate.slice(5,7)} a ${item.endDate.slice(8,10)}/${item.endDate.slice(5,7)}`;
  return { title: `Novo Encarte: ${item.name}`, body: `Validade: ${validity}. Toque para conferir.`,
    type: 'NEW_ENCARTE', encarteId: String(item.id), encarteName: item.name,
    startDate: item.startDate, endDate: item.endDate };
}
export async function notifyCatalog(catalog, { baseline = false, claim, send, acknowledge, release }) {
  const queue = await claim(catalog.map(t => ({ id: t.id, name: t.name, startDate: t.startDate,
    endDate: t.endDate, expiresAt: new Date(validDate(t.endDate)).toISOString() })), baseline);
  let sent = 0;
  for (const event of queue) {
    try {
      const data = notificationPayload(event.payload);
      await send(data);
      await acknowledge(event);
      sent++;
    } catch (error) {
      await release(event).catch(() => {});
      throw error;
    }
  }
  return { sent, eligible: catalog.length };
}
