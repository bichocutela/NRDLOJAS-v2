import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";

const firebaseProject = "appcodigo-7f245";
const jwks = createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
const origins = new Set(["https://bichocutela.github.io", "http://localhost:5173", "http://127.0.0.1:5173"]);
function headers(req: Request) {
  const origin = req.headers.get("origin") ?? "";
  return {
    "Access-Control-Allow-Origin": origins.has(origin) ? origin : "https://bichocutela.github.io",
    "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-firebase-token",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  };
}
function reply(req: Request, body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: headers(req) });
}
async function verifyMestre(req: Request) {
  const token = req.headers.get("x-firebase-token")?.trim();
  if (!token) throw new Error("unauthorized");
  const { payload } = await jwtVerify(token, jwks, {
    issuer: `https://securetoken.google.com/${firebaseProject}`, audience: firebaseProject,
  });
  if (String(payload.email ?? "").toLowerCase() !== "mestre@nrdlojas.com" &&
      String(payload.role ?? "").toLowerCase() !== "mestre") throw new Error("unauthorized");
}
function parsed(raw: string): any {
  const clean = raw.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "").trim();
  const begin = clean.indexOf("{"); const end = clean.lastIndexOf("}");
  if (begin < 0 || end <= begin) throw new Error("invalid_response");
  return JSON.parse(clean.slice(begin, end + 1));
}
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: headers(req) });
  if (req.method !== "POST") return reply(req, { error: "Método não permitido." }, 405);
  try {
    await verifyMestre(req);
    const key = Deno.env.get("GEMINI_API_KEY");
    if (!key) return reply(req, { error: "Gemini não configurado no servidor." }, 503);
    const body = await req.json();
    const image = typeof body.imageBase64 === "string" ? body.imageBase64 : "";
    if (!/^[A-Za-z0-9+/=]+$/.test(image) || image.length < 100 || image.length > 8_000_000)
      return reply(req, { error: "Foto inválida ou grande demais." }, 400);
    const prompt = `Leia esta FOTO de uma escala mensal. Cada linha pertence a um funcionário e as colunas são numeradas de 1 a 31. Analise visualmente cada cruzamento linha/coluna: X significa folga e FE significa férias. Não infira dias pelo dia da semana. Inclua apenas matrículas legíveis; não invente valores. Responda somente JSON: {"year":0,"month":0,"employees":[{"registration":"","name":"","shift":"","daysOff":[],"vacationDays":[]}]}. Se mês/ano não estiverem impressos de forma legível use 0. Uma célula sombreada ou vazia não significa folga. A revisão humana é obrigatória.`;
    const upstream = await fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", {
      method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": key },
      body: JSON.stringify({ contents: [{ role: "user", parts: [{ text: prompt }, { inline_data: { mime_type: "image/jpeg", data: image } }] }], generationConfig: { temperature: 0, maxOutputTokens: 8192, responseMimeType: "application/json" } }),
    });
    const result = await upstream.json();
    if (!upstream.ok) return reply(req, { error: upstream.status === 429 ? "Limite do Gemini atingido. Tente mais tarde." : `Gemini indisponível (${upstream.status}).` }, 502);
    const text = result?.candidates?.[0]?.content?.parts?.map((p: any) => p.text ?? "").join("") ?? "";
    const schedule = parsed(text);
    if (!Array.isArray(schedule.employees)) throw new Error("invalid_response");
    return reply(req, { schedule });
  } catch (error) {
    if (String(error).includes("unauthorized")) return reply(req, { error: "Acesso Mestre necessário." }, 403);
    return reply(req, { error: "Não foi possível ler a escala. Confira a foto e tente novamente." }, 502);
  }
});
