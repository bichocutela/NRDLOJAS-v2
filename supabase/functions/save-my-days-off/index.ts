import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { importPKCS8, SignJWT } from "npm:jose@5.9.6";

const projectId = "appcodigo-7f245";
const apiBase = "https://app.nordestao.com.br/nossa-gente/v1";
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-nossa-gente-token",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};
const reply = (body: Record<string, unknown>, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: cors });

async function firestoreAccessToken(account: Record<string, string>) {
  const key = await importPKCS8(account.private_key, "RS256");
  const now = Math.floor(Date.now() / 1000);
  const assertion = await new SignJWT({ scope: "https://www.googleapis.com/auth/datastore" })
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
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  const data = await response.json();
  if (!response.ok || !data.access_token) throw new Error("Não foi possível acessar a escala para sincronizar.");
  return data.access_token as string;
}

function readValue(value: any): any {
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("nullValue" in value) return null;
  if ("arrayValue" in value) return (value.arrayValue.values ?? []).map(readValue);
  if ("mapValue" in value) return readFields(value.mapValue.fields ?? {});
  return null;
}
function readFields(fields: Record<string, any>) {
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, readValue(value)]));
}
function writeValue(value: any): any {
  if (value === null || value === undefined) return { nullValue: null };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(writeValue) } };
  return { mapValue: { fields: writeFields(value) } };
}
function writeFields(fields: Record<string, any>) {
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, writeValue(value)]));
}
function findRegistration(value: any, depth = 0): string {
  if (!value || typeof value !== "object" || depth > 5) return "";
  const keys = ["matricula", "MATRICULA", "registro", "registration", "numeroMatricula", "numero_matricula"];
  for (const key of keys) {
    const raw = value[key];
    if (typeof raw === "string" || typeof raw === "number") {
      const digits = String(raw).replace(/\D/g, "");
      if (digits) return digits;
    }
  }
  for (const child of Object.values(value)) {
    if (child && typeof child === "object") {
      const found = findRegistration(child, depth + 1);
      if (found) return found;
    }
  }
  return "";
}

function findEmployeeName(value: any, depth = 0): string {
  if (!value || typeof value !== "object" || depth > 5) return "";
  const keys = ["nomeCompleto", "nome_completo", "nome", "NOME", "name", "fullName"];
  for (const key of keys) {
    const raw = value[key];
    if (typeof raw === "string" && raw.trim().length > 2) return raw.trim();
  }
  for (const child of Object.values(value)) {
    if (child && typeof child === "object") {
      const found = findEmployeeName(child, depth + 1);
      if (found) return found;
    }
  }
  return "";
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  if (req.method !== "POST") return reply({ error: "Método não permitido." }, 405);
  try {
    const ngToken = req.headers.get("x-nossa-gente-token")?.trim();
    if (!ngToken) return reply({ error: "Entre no Nossa Gente para salvar suas folgas." }, 401);
    const identityResponse = await fetch(apiBase + "/me", {
      headers: { Accept: "application/json", Authorization: "Bearer " + ngToken, "X-Requested-With": "XMLHttpRequest" },
    });
    if (identityResponse.status === 401 || identityResponse.status === 403) {
      return reply({ error: "Sua sessão Nossa Gente expirou. Entre novamente para salvar." }, 401);
    }
    if (!identityResponse.ok) return reply({ error: "Não foi possível confirmar sua matrícula na Nossa Gente." }, 502);
    const identity = await identityResponse.json();
    const registration = findRegistration(identity);
    const employeeName = findEmployeeName(identity);
    if (!registration) return reply({ error: "A Nossa Gente não informou sua matrícula. As folgas não foram alteradas." }, 422);
    if (!employeeName) return reply({ error: "A Nossa Gente não informou seu nome. As folgas não foram alteradas." }, 422);

    const input = await req.json();
    const year = Number(input.year);
    const month = Number(input.month);
    const action = String(input.action ?? "saveDaysOff");
    if (!Number.isInteger(year) || year < 2000 || year > 2100 || !Number.isInteger(month) || month < 1 || month > 12 || !["saveDaysOff", "saveRosterPhoto"].includes(action)) {
      return reply({ error: "Mês, ano ou ação inválidos." }, 400);
    }
    const days = action === "saveDaysOff"
      ? (Array.isArray(input.daysOff) ? [...new Set(input.daysOff.map(Number))].sort((a, b) => a - b) : null)
      : [];
    if (action === "saveDaysOff" && !days) return reply({ error: "As datas informadas são inválidas." }, 400);
    const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
    if (days.some((day: number) => !Number.isInteger(day) || day < 1 || day > lastDay)) {
      return reply({ error: "Uma das datas não pertence ao mês escolhido." }, 400);
    }
    const accountRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!accountRaw) return reply({ error: "Sincronização segura indisponível no servidor." }, 503);
    const account = JSON.parse(accountRaw) as Record<string, string>;
    const accessToken = await firestoreAccessToken(account);
    const monthKey = String(year) + "-" + String(month).padStart(2, "0");
    const documentUrl = "https://firestore.googleapis.com/v1/projects/" + projectId +
      "/databases/(default)/documents/work_schedules/" + monthKey;
    const currentResponse = await fetch(documentUrl, { headers: { Authorization: "Bearer " + accessToken } });
    if (!currentResponse.ok && currentResponse.status !== 404) return reply({ error: "Não foi possível carregar a escala deste mês." }, 502);
    const exists = currentResponse.ok;
    const document = exists ? await currentResponse.json() : null;
    const schedule = exists ? readFields(document.fields ?? {}) : { year, month, employees: [], updatedAt: 0, revision: 0 };
    const employees = Array.isArray(schedule.employees) ? schedule.employees : [];
    const index = employees.findIndex((row: any) => String(row.registration ?? "").replace(/\D/g, "") === registration);
    const ownRow = index >= 0 ? employees[index] : { registration, name: employeeName, shift: "", vacationDays: [], verified: true };
    if (index < 0 && employees.some((row: any) => String(row.name ?? "").trim().toLocaleLowerCase("pt-BR") === employeeName.toLocaleLowerCase("pt-BR"))) {
      return reply({ error: "Há um funcionário com o mesmo nome e outra matrícula nesta escala. Peça ao Mestre para conferir antes de cadastrar." }, 409);
    }
    if (index < 0) employees.push(ownRow);
    const ownIndex = index >= 0 ? index : employees.length - 1;

    if (action === "saveRosterPhoto") {
      const monthKey = String(year) + "-" + String(month).padStart(2, "0");
      const expectedUrl = (Deno.env.get("SUPABASE_URL") ?? "").replace(/\/$/, "") +
        "/storage/v1/object/public/nrdlojas-images/work-schedule-photos/" + registration + "/" + monthKey + ".jpg";
      const photoUrl = typeof input.photoUrl === "string" ? input.photoUrl : "";
      if (!expectedUrl.startsWith("https://") || photoUrl !== expectedUrl) {
        return reply({ error: "A foto não corresponde ao mês ou à matrícula autenticada." }, 400);
      }
      employees[ownIndex] = { ...employees[ownIndex], registration, name: employees[ownIndex].name || employeeName,
        rosterPhotoUrl: photoUrl, verified: true };
      schedule.employees = employees;
      if (!exists) {
        const commitResponse = await fetch("https://firestore.googleapis.com/v1/projects/" + projectId + "/databases/(default)/documents:commit", {
          method: "POST",
          headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
          body: JSON.stringify({ writes: [{
            update: { name: "projects/" + projectId + "/databases/(default)/documents/work_schedules/" + monthKey, fields: writeFields(schedule) },
            currentDocument: { exists: false },
          }] }),
        });
        if (commitResponse.status === 409 || commitResponse.status === 412) return reply({ error: "A escala acabou de ser criada. Atualize o perfil e tente novamente." }, 409);
        if (!commitResponse.ok) return reply({ error: "Não foi possível salvar a foto no perfil." }, 502);
      } else {
        const updateUrl = documentUrl + "?updateMask.fieldPaths=employees&currentDocument.updateTime=" + encodeURIComponent(document.updateTime);
        const updateResponse = await fetch(updateUrl, {
          method: "PATCH",
          headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
          body: JSON.stringify({ fields: writeFields({ employees }) }),
        });
        if (updateResponse.status === 409 || updateResponse.status === 412) return reply({ error: "A escala mudou enquanto a foto era salva. Atualize e tente novamente." }, 409);
        if (!updateResponse.ok) return reply({ error: "Não foi possível salvar a foto no perfil." }, 502);
      }
      return reply({ ok: true, action, registration, monthKey, photoUrl });
    }

    employees[ownIndex] = { ...employees[ownIndex], daysOff: days, verified: true };
    schedule.employees = employees;
    schedule.updatedAt = Date.now();
    schedule.revision = Number(schedule.revision ?? 0) + 1;

    if (!exists) {
      const commitResponse = await fetch("https://firestore.googleapis.com/v1/projects/" + projectId + "/databases/(default)/documents:commit", {
        method: "POST",
        headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
        body: JSON.stringify({ writes: [{
          update: { name: "projects/" + projectId + "/databases/(default)/documents/work_schedules/" + monthKey, fields: writeFields(schedule) },
          currentDocument: { exists: false },
        }] }),
      });
      if (commitResponse.status === 409 || commitResponse.status === 412) {
        return reply({ error: "Uma escala acabou de ser criada neste mês. Atualize o perfil e tente novamente." }, 409);
      }
      if (!commitResponse.ok) return reply({ error: "Não foi possível criar o registro das suas folgas. Tente novamente." }, 502);
      return reply({ ok: true, registration, monthKey, daysOff: days });
    }

    const updateUrl = documentUrl +
      "?updateMask.fieldPaths=employees&updateMask.fieldPaths=updatedAt&updateMask.fieldPaths=revision" +
      "&currentDocument.updateTime=" + encodeURIComponent(document.updateTime);
    const updateResponse = await fetch(updateUrl, {
      method: "PATCH",
      headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
      body: JSON.stringify({ fields: writeFields(schedule) }),
    });
    if (updateResponse.status === 409 || updateResponse.status === 412) {
      return reply({ error: "A escala mudou enquanto você editava. Atualize o perfil e tente salvar novamente." }, 409);
    }
    if (!updateResponse.ok) return reply({ error: "Não foi possível sincronizar suas folgas. Tente novamente." }, 502);
    return reply({ ok: true, registration, monthKey, daysOff: days });
  } catch (error) {
    console.error("save-my-days-off failed", error);
    return reply({ error: error instanceof Error ? error.message : "Falha ao salvar as folgas." }, 500);
  }
});
