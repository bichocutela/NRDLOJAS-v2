import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import * as jose from "https://deno.land/x/jose@v4.14.4/index.ts";

const projectId = "appcodigo-7f245";
const issuer = `https://securetoken.google.com/${projectId}`;
const certUrl = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
const githubOidcIssuer = "https://token.actions.githubusercontent.com";
const githubOidcAudience = "nrdlojas-master-updates";
const githubRepository = "bichocutela/NRDLOJAS-v2";
const githubRepositoryId = "1332397983";
const githubOwnerId = "186314089";
const githubWorkflowRef = `${githubRepository}/.github/workflows/main.yml@refs/heads/main`;
const githubJwks = jose.createRemoteJWKSet(new URL(`${githubOidcIssuer}/.well-known/jwks`));
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-firebase-token, x-github-oidc-token" };
const json = (body: Record<string, unknown>, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
const roles = { "admin@nrdlojas.com": "admin", "mestre@nrdlojas.com": "mestre" } as const;
const categories = ["Açougue", "Cafeteria", "Frios", "Hortifruti", "Mercearia", "Padaria"];
const units = ["KG", "G", "L", "ML", "UN"];

type Identity = { uid: string; email: string };

async function verify(token: string): Promise<Identity> {
  const header = jose.decodeProtectedHeader(token);
  const payload = jose.decodeJwt(token);
  if (header.alg !== "RS256" || !header.kid || payload.aud !== projectId || payload.iss !== issuer) throw new Error("Token Firebase inválido");
  const certs = await (await fetch(certUrl)).json() as Record<string, string>;
  const cert = certs[header.kid];
  if (!cert) throw new Error("Chave Firebase desconhecida");
  const key = await jose.importX509(cert, "RS256");
  const { payload: valid } = await jose.jwtVerify(token, key, { algorithms: ["RS256"], audience: projectId, issuer });
  if (!valid.sub || typeof valid.sub !== "string" || !valid.email || typeof valid.email !== "string") throw new Error("Identidade Firebase incompleta");
  return { uid: valid.sub, email: valid.email.toLowerCase() };
}

function roleFor(identity: Identity) { return roles[identity.email as keyof typeof roles] ?? null; }

async function verifyGitHubActionsToken(token: string) {
  const { payload } = await jose.jwtVerify(token, githubJwks, {
    algorithms: ["RS256"],
    issuer: githubOidcIssuer,
    audience: githubOidcAudience,
  });
  const validEvent = payload.event_name === "push" || payload.event_name === "workflow_dispatch";
  if (
    payload.repository !== githubRepository ||
    payload.repository_id !== githubRepositoryId ||
    payload.repository_owner_id !== githubOwnerId ||
    payload.ref !== "refs/heads/main" ||
    payload.workflow_ref !== githubWorkflowRef ||
    !validEvent
  ) {
    throw new Error("Fluxo GitHub não autorizado");
  }
  return payload;
}

async function accessToken(account: Record<string, string>, scope: string) {
  const key = await jose.importPKCS8(account.private_key, "RS256");
  const now = Math.floor(Date.now() / 1000);
  const assertion = await new jose.SignJWT({ scope }).setProtectedHeader({ alg: "RS256", typ: "JWT" }).setIssuer(account.client_email).setSubject(account.client_email).setAudience("https://oauth2.googleapis.com/token").setIssuedAt(now).setExpirationTime(now + 3600).sign(key);
  const response = await fetch("https://oauth2.googleapis.com/token", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }) });
  const data = await response.json();
  if (!response.ok || !data.access_token) throw new Error("Não foi possível autenticar o serviço Firebase");
  return data.access_token as string;
}

async function tokenId(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((entry) => entry.toString(16).padStart(2, "0")).join("");
}

function webPushMirrorConfig() {
  const url = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceRoleKey) throw new Error("Configuração de armazenamento Web Push ausente");
  return { url: url.replace(/\/$/, ""), serviceRoleKey };
}

async function saveWebPushToken(token: string, uid: string) {
  const { url, serviceRoleKey } = webPushMirrorConfig();
  const response = await fetch(`${url}/rest/v1/web_push_subscriptions`, {
    method: "POST",
    headers: { apikey: serviceRoleKey, Authorization: `Bearer ${serviceRoleKey}`, "Content-Type": "application/json", Prefer: "resolution=merge-duplicates,return=minimal" },
    body: JSON.stringify({ token, uid, updated_at: new Date().toISOString() }),
  });
  if (!response.ok) throw new Error(`Não foi possível espelhar inscrição Web Push (HTTP ${response.status})`);
}

async function removeWebPushToken(token: string) {
  const { url, serviceRoleKey } = webPushMirrorConfig();
  const response = await fetch(url + "/rest/v1/web_push_subscriptions?token=eq." + encodeURIComponent(token), { method: "DELETE", headers: { apikey: serviceRoleKey, Authorization: "Bearer " + serviceRoleKey } });
  if (!response.ok) throw new Error(`Não foi possível remover inscrição Web Push (HTTP ${response.status})`);
}

async function readWebPushTokens() {
  const { url, serviceRoleKey } = webPushMirrorConfig();
  const response = await fetch(`${url}/rest/v1/web_push_subscriptions?select=token`, { headers: { apikey: serviceRoleKey, Authorization: `Bearer ${serviceRoleKey}` } });
  if (!response.ok) throw new Error(`Não foi possível ler inscrições Web Push (HTTP ${response.status})`);
  const rows = await response.json() as Array<{ token?: string }>;
  const tokens = rows.map((row) => row.token).filter(Boolean) as string[];
  console.info("[send-fcm] Assinaturas Web Push carregadas", { webSubscriptions: tokens.length });
  return tokens;
}

function normalizeProductName(name: string) {
  return name.replace(/[\[\]\(\)]/g, " ").replace(/\s+/g, " ").trim();
}

function searchNameFor(name: string) {
  return name.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
}

async function listProductNames(account: Record<string, string>, accountProject: string) {
  const firestoreToken = await accessToken(account, "https://www.googleapis.com/auth/datastore");
  const endpoint = "https://firestore.googleapis.com/v1/projects/" + accountProject + "/databases/(default)/documents:runQuery";
  const response = await fetch(endpoint, { method: "POST", headers: { Authorization: "Bearer " + firestoreToken, "Content-Type": "application/json" }, body: JSON.stringify({ structuredQuery: { from: [{ collectionId: "products" }], select: { fields: [{ fieldPath: "name" }, { fieldPath: "code" }, { fieldPath: "category" }, { fieldPath: "timestamp" }] }, orderBy: [{ field: { fieldPath: "timestamp" }, direction: "DESCENDING" }], limit: 120 } }) });
  if (!response.ok) throw new Error("Não foi possível ler produtos recentes (HTTP " + response.status + ")");
  const rows = await response.json() as Array<{ document?: { name: string; fields?: Record<string, { stringValue?: string }> } }>;
  const targetCategories = new Set(["Frios", "Açougue"]);
  const items: Array<{ documentId: string; code: string; name: string; normalizedName: string; category: string }> = [];
  for (const row of rows) {
    const document = row.document;
    const name = document?.fields?.name?.stringValue || "";
    const category = document?.fields?.category?.stringValue || "";
    const normalizedName = normalizeProductName(name);
    if (document && targetCategories.has(category) && name !== normalizedName && /[\[\]\(\)]/.test(name)) {
      items.push({ documentId: document.name.split("/").pop() || "", code: document.fields?.code?.stringValue || document.name.split("/").pop() || "", name, normalizedName, category });
    }
  }
  return items.sort((first, second) => first.category.localeCompare(second.category, "pt-BR") || first.name.localeCompare(second.name, "pt-BR"));
}

async function sendEvent(
  account: Record<string, string>,
  accountProject: string,
  title: "Produto adicionado" | "Código alterado" | "Sugestão corrigida" | "Atualização disponível",
  messageBody: string,
  uid: string,
  productCode?: string,
  targetTopic = "products",
  mirrorToWeb = true,
) {
  const fcmToken = await accessToken(account, "https://www.googleapis.com/auth/firebase.messaging");
  const type = title === "Código alterado" ? "CODE_CHANGED" : title === "Sugestão corrigida" ? "SUGGESTION_FIXED" : title === "Atualização disponível" ? "APP_UPDATE" : "NEW_PRODUCT";
  const channel = type === "CODE_CHANGED" ? "product_code_changed" : type === "SUGGESTION_FIXED" ? "suggestion_fixed" : type === "APP_UPDATE" ? "app_update" : "product_added";
  const webLink = productCode ? "https://bichocutela.github.io/?product=" + encodeURIComponent(productCode) : "https://bichocutela.github.io/";
  const message = { data: { title, body: messageBody, type, productCode: productCode ?? "", url: webLink } };
  const send = await fetch("https://fcm.googleapis.com/v1/projects/" + accountProject + "/messages:send", { method: "POST", headers: { Authorization: "Bearer " + fcmToken, "Content-Type": "application/json" }, body: JSON.stringify({ message: { topic: targetTopic, ...message, android: { priority: "high", notification: { channel_id: channel } } } }) });
  if (!send.ok) throw new Error("FCM error " + send.status + ": " + await send.text());
  const webTokens = mirrorToWeb ? await readWebPushTokens() : [];
  const webResults = mirrorToWeb
    ? await Promise.allSettled(webTokens.map((webToken) => fetch("https://fcm.googleapis.com/v1/projects/" + accountProject + "/messages:send", { method: "POST", headers: { Authorization: "Bearer " + fcmToken, "Content-Type": "application/json" }, body: JSON.stringify({ message: { token: webToken, ...message, webpush: { headers: { Urgency: "high", TTL: "2419200" }, fcm_options: { link: webLink } } } }) })))
    : [];
  const deliveredWeb = webResults.filter((result) => result.status === "fulfilled" && result.value.ok).length;
  console.info("[send-fcm] Eventos enviados", { uid, type, topic: targetTopic, webSubscriptions: webTokens.length, deliveredWeb });
  return { deliveredWeb };
}

serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  let body: Record<string, unknown>;
  try { body = await request.json(); } catch { return json({ ok: false, error: "Invalid JSON" }, 400); }
  const managingWebPushSubscription = body.action === "REGISTER_WEB_TOKEN" || body.action === "UNREGISTER_WEB_TOKEN";
  const notifyingMasterUpdate = body.action === "NOTIFY_MASTER_UPDATE";
  const idToken = request.headers.get("x-firebase-token");
  let identity: Identity | null = null;
  if (notifyingMasterUpdate) {
    const githubToken = request.headers.get("x-github-oidc-token");
    if (!githubToken) return json({ ok: false, error: "Missing GitHub OIDC token" }, 401);
    try {
      await verifyGitHubActionsToken(githubToken);
    } catch {
      return json({ ok: false, error: "Invalid or unauthorized GitHub OIDC token" }, 403);
    }
  } else if (!managingWebPushSubscription || idToken) {
    if (!idToken) return json({ ok: false, error: "Missing x-firebase-token" }, 401);
    try { identity = await verify(idToken); } catch { return json({ ok: false, error: "Invalid Firebase ID Token" }, 401); }
  }
  try {
    const accountRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!accountRaw) throw new Error("FIREBASE_SERVICE_ACCOUNT is not configured");
    const account = JSON.parse(accountRaw) as Record<string, string>;
    const accountProject = account.project_id || projectId;
    if (notifyingMasterUpdate) {
      const version = typeof body.version === "string" ? body.version.trim() : "";
      if (!/^v\d+\.\d+\.\d+$/.test(version)) return json({ ok: false, error: "Invalid release version" }, 400);
      const notification = await sendEvent(
        account,
        accountProject,
        "Atualização disponível",
        `A versão ${version} já está disponível para testes internos.`,
        "github-actions",
        undefined,
        "master_updates",
        false,
      );
      return json({ ok: true, notification });
    }
    if (body.action === "UNREGISTER_WEB_TOKEN") {
      if (!body.webToken || typeof body.webToken !== "string" || body.webToken.length < 80) return json({ ok: false, error: "Invalid web token" }, 400);
      const firestoreToken = await accessToken(account, "https://www.googleapis.com/auth/datastore");
      const id = await tokenId(body.webToken);
      const result = await fetch("https://firestore.googleapis.com/v1/projects/" + accountProject + "/databases/(default)/documents/webPushSubscriptions/" + id, { method: "DELETE", headers: { Authorization: "Bearer " + firestoreToken } });
      if (!result.ok && result.status !== 404) throw new Error("Não foi possível remover inscrição Web Push (HTTP " + result.status + ")");
      await removeWebPushToken(body.webToken);
      console.info("[send-fcm] Web Push removido", { uid: identity?.uid ?? "anonymous" });
      return json({ ok: true, unregistered: true });
    }
    if (body.action === "REGISTER_WEB_TOKEN") {
      if (!body.webToken || typeof body.webToken !== "string" || body.webToken.length < 80) return json({ ok: false, error: "Invalid web token" }, 400);
      const firestoreToken = await accessToken(account, "https://www.googleapis.com/auth/datastore");
      const id = await tokenId(body.webToken);
      const result = await fetch(`https://firestore.googleapis.com/v1/projects/${accountProject}/databases/(default)/documents/webPushSubscriptions/${id}`, { method: "PATCH", headers: { Authorization: `Bearer ${firestoreToken}`, "Content-Type": "application/json" }, body: JSON.stringify({ fields: { token: { stringValue: body.webToken }, uid: { stringValue: identity?.uid ?? "anonymous" }, updatedAt: { timestampValue: new Date().toISOString() } } }) });
      if (!result.ok) throw new Error(`Não foi possível registrar Web Push (HTTP ${result.status})`);
      await saveWebPushToken(body.webToken, identity?.uid ?? "anonymous");
      console.info("[send-fcm] Web Push registrado", { uid: identity?.uid ?? "anonymous" });
      return json({ ok: true, registered: true });
    }
    if (!identity) return json({ ok: false, error: "Missing Firebase identity" }, 401);
    const role = roleFor(identity);
    if (!role) return json({ ok: false, error: "Usuário não autorizado para esta ação" }, 403);
    if (body.action === "VALIDATE_MANAGE_ACCESS") return json({ ok: true, role });
    if (body.action === "AUDIT_PRODUCT_NAMES") {
      const candidates = await listProductNames(account, accountProject);
      console.info("[send-fcm] Auditoria de nomes concluída", { uid: identity.uid, role, affected: candidates.length });
      return json({ ok: true, role, affected: candidates.length, candidates });
    }
    if (body.action === "NORMALIZE_PRODUCT_NAMES") {
      const submitted = body.candidates;
      if (!Array.isArray(submitted) || submitted.length === 0 || submitted.length > 100) return json({ ok: false, error: "Lista de produtos para normalização inválida" }, 400);
      const candidateIds = new Set<string>();
      const candidates = submitted.map((value) => {
        const item = value as Record<string, unknown>;
        const documentId = typeof item.documentId === "string" ? item.documentId.trim() : "";
        const code = typeof item.code === "string" ? item.code.trim() : "";
        const name = typeof item.name === "string" ? item.name : "";
        const normalizedName = typeof item.normalizedName === "string" ? item.normalizedName : "";
        const category = typeof item.category === "string" ? item.category : "";
        if (!documentId || documentId !== code || !["Açougue", "Frios"].includes(category) || !/[\[\]\(\)]/.test(name) || normalizedName !== normalizeProductName(name) || candidateIds.has(documentId)) throw new Error("Produto inválido na lista de normalização");
        candidateIds.add(documentId);
        return { documentId, code, name, normalizedName, category };
      });
      const firestoreToken = await accessToken(account, "https://www.googleapis.com/auth/datastore");
      for (const item of candidates) {
        const fields = { name: { stringValue: item.normalizedName }, searchName: { stringValue: searchNameFor(item.normalizedName) }, timestamp: { integerValue: String(Date.now()) } };
        const endpoint = "https://firestore.googleapis.com/v1/projects/" + accountProject + "/databases/(default)/documents/products/" + encodeURIComponent(item.documentId) + "?updateMask.fieldPaths=name&updateMask.fieldPaths=searchName&updateMask.fieldPaths=timestamp";
        const response = await fetch(endpoint, { method: "PATCH", headers: { Authorization: "Bearer " + firestoreToken, "Content-Type": "application/json" }, body: JSON.stringify({ fields }) });
        if (!response.ok) throw new Error("Não foi possível normalizar " + item.code + " (HTTP " + response.status + ")");
      }
      console.info("[send-fcm] Nomes normalizados por lista confirmada", { uid: identity.uid, role, affected: candidates.length });
      return json({ ok: true, role, affected: candidates.length, candidates });
    }
    if (body.action === "MANAGE_PRODUCT") {
      const product = body.product as Record<string, unknown> | undefined;
      const previousCode = typeof body.previousCode === "string" ? body.previousCode.trim() : "";
      const code = typeof product?.code === "string" ? product.code.trim() : "";
      const name = typeof product?.name === "string" ? product.name.trim() : "";
      const category = typeof product?.category === "string" ? product.category : "";
      const unit = typeof product?.unit === "string" ? product.unit.toUpperCase() : "";
      if (!code || code.length > 60 || !name || name.length > 240 || !categories.includes(category) || !units.includes(unit)) return json({ ok: false, error: "Dados do produto inválidos" }, 400);
      const fields: Record<string, unknown> = { code: { stringValue: code }, name: { stringValue: name }, searchName: { stringValue: name.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase() }, category: { stringValue: category }, unit: { stringValue: unit }, timestamp: { integerValue: String(Date.now()) }, updatedBy: { stringValue: identity.uid }, updatedByRole: { stringValue: role } };
      const imageUrl = typeof product?.imageUrl === "string" ? product.imageUrl : null;
      fields.imageUrl = imageUrl ? { stringValue: imageUrl } : { nullValue: null };
      const firestoreToken = await accessToken(account, "https://www.googleapis.com/auth/datastore");
      const write = await fetch(`https://firestore.googleapis.com/v1/projects/${accountProject}/databases/(default)/documents/products/${encodeURIComponent(code)}`, { method: "PATCH", headers: { Authorization: `Bearer ${firestoreToken}`, "Content-Type": "application/json" }, body: JSON.stringify({ fields }) });
      if (!write.ok) throw new Error(`Não foi possível salvar o produto (HTTP ${write.status})`);
      if (previousCode && previousCode !== code) {
        const remove = await fetch(`https://firestore.googleapis.com/v1/projects/${accountProject}/databases/(default)/documents/products/${encodeURIComponent(previousCode)}`, { method: "DELETE", headers: { Authorization: `Bearer ${firestoreToken}` } });
        if (!remove.ok) throw new Error(`Produto salvo, mas não foi possível remover o código anterior (HTTP ${remove.status})`);
      }
      const title = !previousCode ? "Produto adicionado" : previousCode !== code ? "Código alterado" : null;
      const notification = title ? await sendEvent(account, accountProject, title, name, identity.uid, code) : null;
      console.info("[send-fcm] Produto salvo", { uid: identity.uid, role, code, previousCode: previousCode || null });
      return json({ ok: true, role, notification });
    }
    if (body.action === "DELETE_PRODUCT") {
      const code = typeof body.code === "string" ? body.code.trim() : "";
      if (!code || code.length > 60) return json({ ok: false, error: "Código do produto inválido" }, 400);
      const firestoreToken = await accessToken(account, "https://www.googleapis.com/auth/datastore");
      const remove = await fetch(`https://firestore.googleapis.com/v1/projects/${accountProject}/databases/(default)/documents/products/${encodeURIComponent(code)}`, { method: "DELETE", headers: { Authorization: `Bearer ${firestoreToken}` } });
      if (remove.status === 404) return json({ ok: false, error: "Produto não encontrado ou já removido" }, 404);
      if (!remove.ok) throw new Error(`Não foi possível excluir o produto (HTTP ${remove.status})`);
      console.info("[send-fcm] Produto excluído", { uid: identity.uid, role, code });
      return json({ ok: true, role });
    }
    const { title, body: messageBody } = body;
    const allowed = ["Produto adicionado", "Código alterado", "Sugestão corrigida", "Atualização disponível"];
    if (!allowed.includes(title) || typeof messageBody !== "string" || !messageBody) return json({ ok: false, error: "Unsupported notification payload" }, 400);
    const notification = await sendEvent(account, accountProject, title as "Produto adicionado" | "Código alterado" | "Sugestão corrigida" | "Atualização disponível", messageBody, identity.uid);
    return json({ ok: true, notification });
  } catch (error) {
    console.error("[send-fcm] Erro", { message: error instanceof Error ? error.message : "unknown" });
    return json({ ok: false, error: error instanceof Error ? error.message : "unknown" }, 400);
  }
});
