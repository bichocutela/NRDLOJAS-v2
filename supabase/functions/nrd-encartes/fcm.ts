import * as jose from 'https://deno.land/x/jose@v4.14.4/index.ts';
import { TOPIC } from './notifications.mjs';
let cached = '', expires = 0;
async function firebaseToken(account: Record<string, string>) {
  if (cached && expires > Date.now()) return cached;
  const now = Math.floor(Date.now() / 1000);
  const key = await jose.importPKCS8(account.private_key, 'RS256');
  const assertion = await new jose.SignJWT({ scope: 'https://www.googleapis.com/auth/firebase.messaging' })
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' }).setIssuer(account.client_email).setSubject(account.client_email)
    .setAudience('https://oauth2.googleapis.com/token').setIssuedAt(now).setExpirationTime(now + 3600).sign(key);
  const response = await fetch('https://oauth2.googleapis.com/token', { method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
    signal: AbortSignal.timeout(10000) });
  if (!response.ok) throw new Error('Firebase authentication unavailable');
  const result = await response.json();
  if (!result.access_token) throw new Error('Invalid Firebase authentication');
  cached = result.access_token; expires = Date.now() + 3000000;
  return cached;
}
export async function sendLeaflet(data: Record<string, string>, validateOnly = false) {
  const raw = Deno.env.get('FIREBASE_SERVICE_ACCOUNT');
  if (!raw) throw new Error('Firebase not configured');
  const account = JSON.parse(raw);
  if (account.project_id !== 'appcodigo-7f245') throw new Error('Unexpected Firebase project');
  const ttl = Math.min(86400, Math.max(0, Math.floor((Date.parse(data.endDate + '-03:00') - Date.now()) / 1000)));
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
    method: 'POST', headers: { Authorization: `Bearer ${await firebaseToken(account)}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ validate_only: validateOnly, message: { topic: TOPIC, data,
      android: { priority: 'high', ttl: `${ttl}s` } } }), signal: AbortSignal.timeout(12000),
  });
  if (!response.ok) throw new Error(`FCM unavailable (${response.status})`);
}
