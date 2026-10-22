// Natal edition includes the metropolitan stores served by the same leaflet.
const stores = new Set(['alecrim', 'cidade jardim', 'igapo', 'lagoa nova', 'nova parnamirim',
  'petropolis', 'ponta negra', 'rota do sol', 'santa catarina', 'tirol'].map(s => `nordestao ${s}`));
const normalize = s => String(s ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase().replace(/\s+/g, ' ');
export function assetUrl(value, extension) {
  try {
    const u = new URL(value);
    return u.protocol === 'https:' && u.hostname === 'datalakeidever.blob.core.windows.net' &&
      !u.username && !u.password && u.port === '' && u.pathname.toLowerCase().endsWith(extension) ? u.href : null;
  } catch { return null; }
}
export function validDate(value) {
  // Idever sends retailer-local timestamps with no zone. RN is UTC-03 year round.
  if (typeof value !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?$/.test(value)) return NaN;
  return Date.parse(value + '-03:00');
}
export function natalCatalog(payload, now = Date.now()) {
  if (!Array.isArray(payload?.tabloids)) throw new Error('Invalid catalog');
  const ids = new Set();
  return payload.tabloids.flatMap(t => {
    const id = Number(t.id), start = validDate(t.startDate), end = validDate(t.endDate);
    const pdf = assetUrl(t.uri, '.pdf'), image = assetUrl(t.image, '.png');
    if (!Number.isSafeInteger(id) || id <= 0 || ids.has(id) || !pdf || !image ||
      typeof t.name !== 'string' || !t.name.trim() || start > now || end < now ||
      !Number.isFinite(start) || !Number.isFinite(end) || start > end ||
      !Array.isArray(t.availableStores) || !t.availableStores.some(s => stores.has(normalize(s)))) return [];
    ids.add(id);
    return [{ id, name: t.name.trim().slice(0, 180), startDate: t.startDate, endDate: t.endDate, uri: pdf, image }];
  }).sort((a, b) => b.startDate.localeCompare(a.startDate) || a.id - b.id);
}
