import {decodeFields} from '../nrd-promotion-control/documents.mjs';

export function providerSettingsFromFields(fields, defaultProvider = 'legacy') {
  const stored = decodeFields(fields ?? {});
  if (typeof stored.legacyEnabled === 'boolean' || typeof stored.acpTechEnabled === 'boolean') {
    return {legacyEnabled:stored.legacyEnabled, acpTechEnabled:stored.acpTechEnabled, primary:stored.primary};
  }
  return {legacyEnabled:defaultProvider === 'legacy', acpTechEnabled:defaultProvider === 'acptech', primary:defaultProvider};
}

/** Select one enabled ACP source and use the other only after an upstream failure. */
export function providerOrder(settings = {}, defaultProvider = 'legacy') {
  const fallback = defaultProvider === 'acptech' ? 'acptech' : 'legacy';
  const legacyEnabled = typeof settings?.legacyEnabled === 'boolean'
    ? settings.legacyEnabled : fallback === 'legacy';
  const acpTechEnabled = typeof settings?.acpTechEnabled === 'boolean'
    ? settings.acpTechEnabled : fallback === 'acptech';
  const enabled = ['legacy', 'acptech'].filter(source =>
    source === 'legacy' ? legacyEnabled : acpTechEnabled);
  if (enabled.length < 2) return enabled;
  const primary = settings?.primary === 'acptech' ? 'acptech' :
    settings?.primary === 'legacy' ? 'legacy' : fallback;
  return [primary, primary === 'legacy' ? 'acptech' : 'legacy'];
}
