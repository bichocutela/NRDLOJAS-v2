const text = (value, max = 500) => typeof value === 'string' ? value.trim().slice(0, max) : '';
const coordinate = (value, max) => value !== null && value !== '' && Number.isFinite(Number(value)) && Math.abs(Number(value)) <= max ? Number(value) : null;
export function storeCatalog(payload) {
  if (!Array.isArray(payload?.stores) || payload.stores.length > 1000) throw new Error('INVALID_CATALOG');
  const seen = new Set();
  return payload.stores.map(raw => {
    const id = Number(raw?.id), name = text(raw?.name, 180);
    if (!Number.isSafeInteger(id) || id <= 0 || !name || seen.has(id)) throw new Error('INVALID_STORE');
    seen.add(id);
    const address = raw.address ?? {};
    const phones = Array.isArray(raw.phones) ? [...new Set(raw.phones.map(p => text(p, 40).replace(/\D/g, '')).filter(p => /^\d{10,13}$/.test(p)))] : [];
    const lat = coordinate(address.latitude, 90), lon = coordinate(address.longitude, 180);
    return { id, name, identifier: text(raw.identifier, 100), businessHours: text(raw.businessHours, 4000),
      isOpen: typeof raw.isOpen === 'boolean' ? raw.isOpen : null,
      capacity: raw.showCapacity === true && Number.isFinite(Number(raw.capacity)) && Number(raw.capacity) >= 0 ? Number(raw.capacity) : null,
      address: { type: text(address.type, 80), street: text(address.street), number: text(String(address.number ?? ''), 40),
        complement: text(address.complement), neighborhood: text(address.neighborhood), city: text(address.city), state: text(address.state, 80), zipCode: text(address.zipCode, 20),
        latitude: lat !== null && lon !== null && (lat !== 0 || lon !== 0) ? lat : null,
        longitude: lat !== null && lon !== null && (lat !== 0 || lon !== 0) ? lon : null }, phones };
  }).sort((a, b) => a.name.localeCompare(b.name, 'pt-BR') || a.id - b.id);
}
