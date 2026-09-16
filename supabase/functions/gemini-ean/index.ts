import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const MODELS = ["gemini-2.5-flash-lite", "gemini-2.5-flash"];
const FIREBASE_PROJECT_ID = "appcodigo-7f245";
const FIREBASE_JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);
const ALLOWED_ORIGINS = new Set([
  "https://bichocutela.github.io",
  "http://localhost:5173",
  "http://127.0.0.1:5173",
]);

function cors(req: Request) {
  const origin = req.headers.get("origin") ?? "";
  return {
    "Access-Control-Allow-Origin": ALLOWED_ORIGINS.has(origin) ? origin : "https://bichocutela.github.io",
    "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-firebase-token",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Vary": "Origin",
  };
}

function json(req: Request, body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors(req), "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

async function verifyMestre(req: Request) {
  const token = req.headers.get("x-firebase-token")?.trim();
  if (!token) throw new Error("mestre_required");
  const { payload } = await jwtVerify(token, FIREBASE_JWKS, {
    issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`,
    audience: FIREBASE_PROJECT_ID,
  });
  const email = typeof payload.email === "string" ? payload.email.trim().toLowerCase() : "";
  const role = typeof payload.role === "string" ? payload.role.trim().toLowerCase() : "";
  if (email !== "mestre@nrdlojas.com" && role !== "mestre") throw new Error("mestre_required");
}

function plainText(payload: any): string {
  const parts = payload?.candidates?.[0]?.content?.parts;
  return Array.isArray(parts)
    ? parts.map((p: any) => typeof p?.text === "string" ? p.text : "").filter(Boolean).join("\n").trim()
    : "";
}

function sources(payload: any) {
  const chunks = payload?.candidates?.[0]?.groundingMetadata?.groundingChunks;
  if (!Array.isArray(chunks)) return [];
  const seen = new Set<string>();
  const out: Array<{ title: string; url: string }> = [];
  for (const chunk of chunks) {
    const url = typeof chunk?.web?.uri === "string" ? chunk.web.uri.trim() : "";
    if (!url || seen.has(url)) continue;
    seen.add(url);
    out.push({
      title: typeof chunk?.web?.title === "string" ? chunk.web.title.trim().slice(0, 160) : "Fonte da web",
      url,
    });
    if (out.length >= 6) break;
  }
  return out;
}

function searchQueries(payload: any) {
  const q = payload?.candidates?.[0]?.groundingMetadata?.webSearchQueries;
  return Array.isArray(q) ? q.filter((x: unknown) => typeof x === "string").slice(0, 8) : [];
}

function parseJsonText(raw: string) {
  const clean = raw.trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/i, "")
    .trim();
  try { return JSON.parse(clean); } catch {
    const start = clean.indexOf("{");
    const end = clean.lastIndexOf("}");
    if (start >= 0 && end > start) return JSON.parse(clean.slice(start, end + 1));
    throw new Error("ean_json_invalid");
  }
}

function hasValidGtinCheckDigit(digits: string): boolean {
  if (![8, 12, 13, 14].includes(digits.length) || !/^\d+$/.test(digits)) return false;
  const body = digits.slice(0, -1);
  const expected = Number(digits.at(-1));
  let sum = 0;
  let factor = 3;
  for (let i = body.length - 1; i >= 0; i--) {
    sum += Number(body[i]) * factor;
    factor = factor === 3 ? 1 : 3;
  }
  const check = (10 - (sum % 10)) % 10;
  return check === expected;
}

function validGtin(value: unknown): string | null {
  const digits = typeof value === "string" || typeof value === "number"
    ? String(value).replace(/\D/g, "")
    : "";
  return hasValidGtinCheckDigit(digits) ? digits : null;
}

async function searchEan(model: string, description: string) {
  if (!GEMINI_API_KEY) throw new Error("gemini_not_configured");

  const system = `Você é um pesquisador de produtos de supermercado para o NRD Lojas.
Sua única tarefa é usar a ferramenta Google Search para localizar um GTIN/EAN real a partir da descrição recebida.
Nunca invente, complete ou estime um código.
Compare obrigatoriamente marca, variante/sabor/tipo e peso/volume/quantidade.
Prefira evidências de fabricante, GS1, catálogo de produto, grandes varejistas ou páginas que mostrem claramente o mesmo item e o código de barras.
Se houver mais de um produto parecido, só escolha quando a variante e o tamanho coincidirem.
Se a evidência não for suficiente, retorne ean null.`;

  const googleQuery = `"${description}" EAN GTIN "código de barras"`;
  const prompt = `Faça uma pesquisa Google para localizar o EAN/GTIN deste produto do encarte.

Descrição: ${description}
Pesquisa inicial sugerida: ${googleQuery}

Se a descrição tiver abreviação ou erro de OCR, faça também pesquisas equivalentes corrigindo apenas o texto, sem mudar marca, variante nem tamanho.

Retorne SOMENTE JSON válido:
{"ean":"somente dígitos ou null","productName":"nome exato encontrado na web","reason":"resumo curto de por que o código corresponde ao mesmo produto"}`;

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_API_KEY },
      body: JSON.stringify({
        system_instruction: { parts: [{ text: system }] },
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        tools: [{ google_search: {} }],
        generationConfig: { temperature: 0.0, maxOutputTokens: 1600 },
      }),
    },
  );

  const raw = await response.text();
  let payload: any = null;
  try { payload = raw ? JSON.parse(raw) : null; } catch { payload = null; }

  if (!response.ok) {
    const status = String(payload?.error?.status ?? "");
    const message = String(payload?.error?.message ?? "");
    const searchable = `${status} ${message}`.toLowerCase();
    if (response.status === 429 || status === "RESOURCE_EXHAUSTED" || searchable.includes("quota")) {
      throw new Error("gemini_rate_limited");
    }
    if (response.status === 404 || status === "NOT_FOUND") throw new Error("gemini_model_unavailable");
    if (response.status === 401 || searchable.includes("api key") || searchable.includes("api_key")) {
      throw new Error("gemini_key_rejected");
    }
    if (
      response.status === 400 || response.status === 403 || status === "PERMISSION_DENIED" ||
      searchable.includes("grounding") || searchable.includes("google search") ||
      searchable.includes("billing") || searchable.includes("not available")
    ) {
      throw new Error("gemini_search_unavailable");
    }
    throw new Error("gemini_upstream");
  }

  const groundedSources = sources(payload);
  const queries = searchQueries(payload);
  const answer = parseJsonText(plainText(payload));
  const checkedEan = validGtin(answer?.ean);
  const ean = groundedSources.length > 0 && queries.length > 0 ? checkedEan : null;

  return {
    ean,
    productName: typeof answer?.productName === "string" ? answer.productName.trim().slice(0, 300) : "",
    reason: typeof answer?.reason === "string" ? answer.reason.trim().slice(0, 500) : "",
    sources: groundedSources,
    queries,
    requestedQuery: googleQuery,
    model,
  };
}

async function searchWithFallback(description: string) {
  let last: unknown = new Error("gemini_upstream");
  for (const model of MODELS) {
    try { return await searchEan(model, description); }
    catch (e) {
      last = e;
      const msg = e instanceof Error ? e.message : "";
      if (
        msg === "gemini_rate_limited" ||
        msg === "gemini_model_unavailable" ||
        msg === "gemini_search_unavailable" ||
        msg === "gemini_upstream"
      ) continue;
      throw e;
    }
  }
  throw last;
}

function publicError(e: unknown) {
  const m = e instanceof Error ? e.message : "";
  if (m === "mestre_required") return [403, "Pesquisa de EAN disponível somente para o Mestre."] as const;
  if (m === "gemini_not_configured") return [503, "A chave do Gemini não está configurada no servidor."] as const;
  if (m === "gemini_rate_limited") return [429, "A cota da Pesquisa Google via Gemini foi atingida agora. Tente novamente mais tarde."] as const;
  if (m === "gemini_key_rejected") return [502, "O Google recusou a GEMINI_API_KEY."] as const;
  if (m === "gemini_search_unavailable") return [502, "A Pesquisa Google não está disponível neste projeto/modelo agora."] as const;
  if (m === "ean_json_invalid") return [502, "A pesquisa Google não retornou um EAN estruturado."] as const;
  return [502, "Não foi possível concluir a pesquisa Google do EAN agora."] as const;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors(req) });
  if (req.method !== "POST") return json(req, { error: "Método não permitido." }, 405);
  try {
    await verifyMestre(req);
    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const description = typeof body.description === "string" ? body.description.trim().slice(0, 300) : "";
    if (description.length < 3) return json(req, { error: "Descrição insuficiente para pesquisar o EAN." }, 400);
    const result = await searchWithFallback(description);
    return json(req, {
      ok: true,
      ...result,
      searchedAt: new Date().toISOString(),
    });
  } catch (e) {
    const [status, error] = publicError(e);
    return json(req, { error }, status);
  }
});
