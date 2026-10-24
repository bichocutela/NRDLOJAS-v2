/** Browser transport only; every handler retains its existing authentication/authorization. */
export function withBrowserCors(handler) {
  const headers = {
    'Access-Control-Allow-Origin': 'https://bichocutela.github.io',
    'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-firebase-token, x-device-session, x-nrd-scope',
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Max-Age': '600',
    'Vary': 'Origin',
  };
  return async req => {
    if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers });
    const response = await handler(req);
    const merged = new Headers(response.headers);
    for (const [key, value] of Object.entries(headers)) merged.set(key, value);
    return new Response(response.body, { status: response.status, statusText: response.statusText, headers: merged });
  };
}
