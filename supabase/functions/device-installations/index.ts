import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import * as jose from "https://deno.land/x/jose@v4.14.4/index.ts";

const firebaseProjectId = "appcodigo-7f245";
const firebaseIssuer = "https://securetoken.google.com/" + firebaseProjectId;
const firebaseCertUrl = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-firebase-token",
};
const json = (body: Record<string, unknown>, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

type Identity = { uid: string; email: string };
type FirebaseField = {
  stringValue?: string;
  timestampValue?: string;
  doubleValue?: number;
  integerValue?: string;
};

async function verifyFirebaseToken(token: string): Promise<Identity> {
  const header = jose.decodeProtectedHeader(token);
  const payload = jose.decodeJwt(token);
  if (header.alg !== "RS256" || !header.kid || payload.aud !== firebaseProjectId || payload.iss !== firebaseIssuer) {
    throw new Error("Token Firebase inválido");
  }
  const certs = await (await fetch(firebaseCertUrl)).json() as Record<string, string>;
  const certificate = certs[header.kid];
  if (!certificate) throw new Error("Chave Firebase desconhecida");
  const key = await jose.importX509(certificate, "RS256");
  const { payload: verified } = await jose.jwtVerify(token, key, {
    algorithms: ["RS256"],
    audience: firebaseProjectId,
    issuer: firebaseIssuer,
  });
  if (typeof verified.sub !== "string" || typeof verified.email !== "string") {
    throw new Error("Identidade Firebase incompleta");
  }
  return { uid: verified.sub, email: verified.email.toLowerCase() };
}

async function serviceAccessToken(account: Record<string, string>) {
  const key = await jose.importPKCS8(account.private_key, "RS256");
  const now = Math.floor(Date.now() / 1000);
  const assertion = await new jose.SignJWT({
    scope: "https://www.googleapis.com/auth/datastore",
  })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(account.client_email)
    .setSubject(account.client_email)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(key);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const payload = await response.json();
  if (!response.ok || !payload.access_token) throw new Error("Falha ao autenticar serviço Firebase");
  return payload.access_token as string;
}

async function documentId(installationId: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(installationId));
  return Array.from(new Uint8Array(digest)).map((value) => value.toString(16).padStart(2, "0")).join("");
}

function cleanString(value: unknown, maxLength: number) {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function optionalCoordinate(value: unknown, min: number, max: number): number | null {
  return typeof value === "number" && Number.isFinite(value) && value >= min && value <= max ? value : null;
}

function stringField(fields: Record<string, FirebaseField>, key: string) {
  return fields[key]?.stringValue ?? "";
}

function timestampMillis(fields: Record<string, FirebaseField>, key: string) {
  const value = fields[key]?.timestampValue;
  const parsed = value ? Date.parse(value) : NaN;
  return Number.isFinite(parsed) ? parsed : 0;
}

async function registerInstallation(
  body: Record<string, unknown>,
  accountProject: string,
  accessToken: string,
) {
  const installationId = cleanString(body.installationId, 80);
  if (!/^[0-9a-fA-F-]{32,80}$/.test(installationId)) {
    return json({ ok: false, error: "Identificador de instalação inválido" }, 400);
  }
  const id = await documentId(installationId);
  const documentUrl = "https://firestore.googleapis.com/v1/projects/" + accountProject +
    "/databases/(default)/documents/deviceInstallations/" + id;
  const existingResponse = await fetch(documentUrl, {
    headers: { Authorization: "Bearer " + accessToken },
  });
  const existing = existingResponse.ok ? await existingResponse.json() : null;
  const now = new Date().toISOString();
  const latitude = optionalCoordinate(body.latitude, -90, 90);
  const longitude = optionalCoordinate(body.longitude, -180, 180);
  const fields: Record<string, FirebaseField> = {
    manufacturer: { stringValue: cleanString(body.manufacturer, 80) || "Não informado" },
    model: { stringValue: cleanString(body.model, 120) || "Não informado" },
    deviceName: { stringValue: cleanString(body.deviceName, 180) || "Aparelho Android" },
    androidVersion: { stringValue: cleanString(body.androidVersion, 40) },
    appVersion: { stringValue: cleanString(body.appVersion, 40) },
    city: { stringValue: cleanString(body.city, 120) },
    state: { stringValue: cleanString(body.state, 120) },
    installedAt: { timestampValue: existing?.fields?.installedAt?.timestampValue ?? now },
    lastSeenAt: { timestampValue: now },
  };
  if (latitude != null) fields.latitude = { doubleValue: latitude };
  if (longitude != null) fields.longitude = { doubleValue: longitude };
  const write = await fetch(documentUrl, {
    method: "PATCH",
    headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
    body: JSON.stringify({ fields }),
  });
  if (!write.ok) throw new Error("Falha ao registrar instalação: HTTP " + write.status);
  return json({ ok: true });
}

async function installationCount(accountProject: string, accessToken: string) {
  const response = await fetch(
    "https://firestore.googleapis.com/v1/projects/" + accountProject +
      "/databases/(default)/documents:runAggregationQuery",
    {
      method: "POST",
      headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
      body: JSON.stringify({
        structuredAggregationQuery: {
          structuredQuery: { from: [{ collectionId: "deviceInstallations" }] },
          aggregations: [{ alias: "total", count: {} }],
        },
      }),
    },
  );
  if (!response.ok) throw new Error("Falha ao contar instalações: HTTP " + response.status);
  const rows = await response.json();
  return Number(rows?.[0]?.result?.aggregateFields?.total?.integerValue ?? 0);
}

async function listInstallations(
  body: Record<string, unknown>,
  accountProject: string,
  accessToken: string,
) {
  const pageSize = Math.min(25, Math.max(1, Number(body.pageSize) || 25));
  const pageToken = cleanString(body.pageToken, 2000);
  const params = new URLSearchParams({
    pageSize: String(pageSize),
    orderBy: "installedAt desc",
  });
  if (pageToken) params.set("pageToken", pageToken);
  const response = await fetch(
    "https://firestore.googleapis.com/v1/projects/" + accountProject +
      "/databases/(default)/documents/deviceInstallations?" + params.toString(),
    { headers: { Authorization: "Bearer " + accessToken } },
  );
  if (!response.ok) throw new Error("Falha ao listar instalações: HTTP " + response.status);
  const payload = await response.json();
  const items = (payload.documents ?? []).map((document: { name: string; fields?: Record<string, FirebaseField> }) => {
    const fields = document.fields ?? {};
    return {
      id: document.name.split("/").pop() ?? "",
      manufacturer: stringField(fields, "manufacturer"),
      model: stringField(fields, "model"),
      deviceName: stringField(fields, "deviceName"),
      installedAt: timestampMillis(fields, "installedAt"),
      lastSeenAt: timestampMillis(fields, "lastSeenAt"),
      city: stringField(fields, "city"),
      state: stringField(fields, "state"),
      latitude: fields.latitude?.doubleValue ?? null,
      longitude: fields.longitude?.doubleValue ?? null,
    };
  });
  return json({
    ok: true,
    total: await installationCount(accountProject, accessToken),
    items,
    nextPageToken: payload.nextPageToken ?? null,
  });
}

serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json({ ok: false, error: "Method not allowed" }, 405);
  let body: Record<string, unknown>;
  try {
    body = await request.json();
  } catch {
    return json({ ok: false, error: "Invalid JSON" }, 400);
  }
  try {
    const accountRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!accountRaw) throw new Error("FIREBASE_SERVICE_ACCOUNT ausente");
    const account = JSON.parse(accountRaw) as Record<string, string>;
    const accountProject = account.project_id || firebaseProjectId;
    const accessToken = await serviceAccessToken(account);
    if (body.action === "REGISTER") {
      return await registerInstallation(body, accountProject, accessToken);
    }
    if (body.action === "LIST") {
      const firebaseToken = request.headers.get("x-firebase-token");
      if (!firebaseToken) return json({ ok: false, error: "Autenticação ausente" }, 401);
      const identity = await verifyFirebaseToken(firebaseToken);
      if (identity.email !== "mestre@nrdlojas.com") {
        return json({ ok: false, error: "Acesso exclusivo do Mestre" }, 403);
      }
      return await listInstallations(body, accountProject, accessToken);
    }
    return json({ ok: false, error: "Ação inválida" }, 400);
  } catch (error) {
    console.error("[device-installations]", error);
    return json({ ok: false, error: "Não foi possível concluir a operação" }, 500);
  }
});
