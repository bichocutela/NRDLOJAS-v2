import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import * as jose from "https://deno.land/x/jose@v4.14.4/index.ts"

const FIREBASE_PROJECT_ID = "appcodigo-7f245"
const FIREBASE_TOKEN_ISSUER = `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`
const FIREBASE_PUBLIC_KEYS_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-firebase-token",
}

class FirebaseTokenValidationError extends Error {
  constructor(message: string, readonly status: 401 | 403) {
    super(message)
    this.name = "FirebaseTokenValidationError"
  }
}

function jsonResponse(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  })
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Erro desconhecido"
}

async function verifyFirebaseIdToken(idToken: string): Promise<string> {
  let protectedHeader: jose.JWTHeaderParameters
  let unverifiedPayload: jose.JWTPayload

  try {
    protectedHeader = jose.decodeProtectedHeader(idToken)
    unverifiedPayload = jose.decodeJwt(idToken)
  } catch {
    throw new FirebaseTokenValidationError("Firebase ID Token malformado", 401)
  }

  if (protectedHeader.alg !== "RS256" || !protectedHeader.kid) {
    throw new FirebaseTokenValidationError("Firebase ID Token com cabeçalho inválido", 401)
  }

  if (unverifiedPayload.aud !== FIREBASE_PROJECT_ID || unverifiedPayload.iss !== FIREBASE_TOKEN_ISSUER) {
    throw new FirebaseTokenValidationError("Firebase ID Token pertence a outro projeto", 403)
  }

  const keysResponse = await fetch(FIREBASE_PUBLIC_KEYS_URL)
  if (!keysResponse.ok) {
    throw new Error(`Não foi possível obter as chaves públicas do Firebase: HTTP ${keysResponse.status}`)
  }

  const certificates = await keysResponse.json() as Record<string, string>
  const certificate = certificates[protectedHeader.kid]
  if (!certificate) {
    throw new FirebaseTokenValidationError("Firebase ID Token assinado com chave desconhecida", 401)
  }

  try {
    const publicKey = await jose.importX509(certificate, "RS256")
    const { payload } = await jose.jwtVerify(idToken, publicKey, {
      algorithms: ["RS256"],
      audience: FIREBASE_PROJECT_ID,
      issuer: FIREBASE_TOKEN_ISSUER,
    })

    if (!payload.sub || typeof payload.sub !== "string") {
      throw new FirebaseTokenValidationError("Firebase ID Token sem UID", 401)
    }

    return payload.sub
  } catch (error) {
    if (error instanceof FirebaseTokenValidationError) throw error
    throw new FirebaseTokenValidationError("Firebase ID Token inválido ou expirado", 401)
  }
}

async function getAccessToken(serviceAccount: any): Promise<string> {
  const privateKey = await jose.importPKCS8(serviceAccount.private_key, "RS256")
  const now = Math.floor(Date.now() / 1000)

  const assertion = await new jose.SignJWT({
    scope: "https://www.googleapis.com/auth/firebase.messaging",
  })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(serviceAccount.client_email)
    .setSubject(serviceAccount.client_email)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(privateKey)

  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  })

  const tokenJson = await tokenResponse.json()
  if (!tokenResponse.ok || !tokenJson.access_token) {
    throw new Error(`OAuth token error: ${JSON.stringify(tokenJson)}`)
  }

  return tokenJson.access_token
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders })
  }

  const firebaseToken = req.headers.get("x-firebase-token")
  if (!firebaseToken) {
    console.warn("[send-fcm] Firebase ID Token ausente")
    return jsonResponse({ ok: false, error: "Missing x-firebase-token" }, 401)
  }

  console.info("[send-fcm] Firebase ID Token recebido", { tokenLength: firebaseToken.length })

  try {
    const uid = await verifyFirebaseIdToken(firebaseToken)
    console.info("[send-fcm] Firebase ID Token validado", { uid })
  } catch (error) {
    const status = error instanceof FirebaseTokenValidationError ? error.status : 401
    console.warn("[send-fcm] Firebase ID Token rejeitado", {
      status,
      error: errorMessage(error),
    })
    return jsonResponse({ ok: false, error: "Invalid, expired, or unauthorized Firebase ID Token" }, status)
  }

  try {
    const rawServiceAccount = Deno.env.get("FIREBASE_SERVICE_ACCOUNT")
    if (!rawServiceAccount) {
      throw new Error("FIREBASE_SERVICE_ACCOUNT is not configured")
    }

    const serviceAccount = JSON.parse(rawServiceAccount)
    const { title, body, topic = "products" } = await req.json()

    if (!title || !body) {
      throw new Error("Missing title or body")
    }

    const allowedTitles = ["Produto adicionado", "Código alterado", "Sugestão corrigida"]
    if (!allowedTitles.includes(title)) {
      throw new Error("Unsupported notification type")
    }

    const accessToken = await getAccessToken(serviceAccount)
    const projectId = serviceAccount.project_id || FIREBASE_PROJECT_ID
    const notificationChannelId = title === "Código alterado"
      ? "product_code_changed"
      : title === "Sugestão corrigida"
      ? "suggestion_fixed"
      : "product_added"

    const fcmResponse = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            topic,
            notification: {
              title,
              body,
            },
            data: {
              title,
              body,
              type: title === "Código alterado"
                ? "CODE_CHANGED"
                : title === "Sugestão corrigida"
                ? "SUGGESTION_FIXED"
                : "NEW_PRODUCT",
            },
            android: {
              priority: "high",
              notification: {
                channel_id: notificationChannelId,
                icon: "ic_launcher_multicolor",
              },
            },
          },
        }),
      },
    )

    const resultText = await fcmResponse.text()
    if (!fcmResponse.ok) {
      console.error("[send-fcm] Firebase FCM rejeitou o envio", {
        status: fcmResponse.status,
        result: resultText,
      })
      throw new Error(`FCM error ${fcmResponse.status}: ${resultText}`)
    }

    console.info("[send-fcm] FCM data-only enviado com sucesso", {
      topic,
      title,
      result: resultText,
    })

    return jsonResponse({ ok: true, result: resultText }, 200)
  } catch (error) {
    console.error("[send-fcm] Erro no envio FCM", { error: errorMessage(error) })
    return jsonResponse(
      { ok: false, error: errorMessage(error) },
      400,
    )
  }
})
