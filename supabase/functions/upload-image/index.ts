import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.7.1"
import * as jose from "https://deno.land/x/jose@v4.14.4/index.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-firebase-token, x-nossa-gente-token',
}

const JWKS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'
const JWKS = jose.createRemoteJWKSet(new URL(JWKS_URL))
const FIREBASE_PROJECT_ID = 'appcodigo-7f245'
const MAX_DYNAMIC_MEDIA_BYTES = 80 * 1024 * 1024
const DYNAMIC_MEDIA_TYPES = new Set([
  'image/jpeg', 'image/png', 'image/webp',
  'video/mp4', 'video/webm', 'video/quicktime',
  'audio/mpeg', 'audio/mp4', 'audio/aac', 'audio/ogg', 'audio/wav', 'audio/x-wav',
  'application/pdf',
])
const NG_API_BASE = 'https://app.nordestao.com.br/nossa-gente/v1'

function findRegistration(value: any, depth = 0): string {
  if (!value || typeof value !== 'object' || depth > 5) return ''
  for (const key of ['matricula', 'MATRICULA', 'registro', 'registration', 'numeroMatricula', 'numero_matricula']) {
    const raw = value[key]
    if (typeof raw === 'string' || typeof raw === 'number') {
      const digits = String(raw).replace(/\D/g, '')
      if (digits) return digits
    }
  }
  for (const child of Object.values(value)) {
    if (child && typeof child === 'object') {
      const found = findRegistration(child, depth + 1)
      if (found) return found
    }
  }
  return ''
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  try {
    const firebaseToken = req.headers.get('x-firebase-token')
    const nossaGenteToken = req.headers.get('x-nossa-gente-token')?.trim()
    let rosterRegistration = ''
    if (nossaGenteToken) {
      if (req.method !== 'POST' || req.headers.get('content-type')?.includes('application/json')) {
        throw new Error('Nossa Gente sessions may only upload roster photos')
      }
      const identityResponse = await fetch(NG_API_BASE + '/me', {
        headers: { Accept: 'application/json', Authorization: 'Bearer ' + nossaGenteToken, 'X-Requested-With': 'XMLHttpRequest' },
      })
      if (identityResponse.status === 401 || identityResponse.status === 403) throw new Error('Sua sessão Nossa Gente expirou. Entre novamente.')
      if (!identityResponse.ok) throw new Error('Não foi possível confirmar sua matrícula na Nossa Gente.')
      rosterRegistration = findRegistration(await identityResponse.json())
      if (!rosterRegistration) throw new Error('A Nossa Gente não informou sua matrícula.')
    } else {
      if (!firebaseToken) throw new Error('Missing x-firebase-token')
      const { payload } = await jose.jwtVerify(firebaseToken, JWKS, {
        issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`,
        audience: FIREBASE_PROJECT_ID,
      })
      if (!payload.sub) throw new Error('Missing subject in token')
      const email = String(payload.email ?? '').toLowerCase()
      if (email !== 'mestre@nrdlojas.com' && email !== 'admin@nrdlojas.com') {
        throw new Error('Unauthorized administrative account')
      }
    }

    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    if (req.method === 'POST' && req.headers.get('content-type')?.includes('application/json')) {
      const actionPayload = await req.json()
      if (actionPayload.action !== 'delete') throw new Error('Unsupported storage action')
      const deletePath = String(actionPayload.path ?? '').replace(/^\/+/, '')
      if (!deletePath.startsWith('glass_particles/') || deletePath.includes('..')) {
        throw new Error('Only custom glass particle images can be deleted')
      }
      const { error } = await supabaseAdmin.storage
        .from('nrdlojas-images')
        .remove([deletePath])
      if (error) throw new Error(`Storage deletion failed: ${error.message}`)
      return new Response(JSON.stringify({ deleted: true }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200
      })
    }

    const formData = await req.formData()
    const pathValue = formData.get('path')
    const fileValue = formData.get('file')
    if (!pathValue || !(fileValue instanceof File)) throw new Error('Missing path or file')

    const safePath = String(pathValue).replace(/^\/+/, '')
    if (!safePath || safePath.includes('..')) throw new Error('Invalid path')
    if (safePath === 'banners/themes' || safePath.startsWith('banners/themes/')) {
      throw new Error('Protected theme path')
    }

    if (nossaGenteToken) {
      const expectedPath = new RegExp(`^work-schedule-photos/${rosterRegistration}/\\d{4}-\\d{2}\\.jpg$`)
      if (!expectedPath.test(safePath) || fileValue.size <= 0 || fileValue.size > 10 * 1024 * 1024 || (fileValue.type || '').toLowerCase() !== 'image/jpeg') {
        throw new Error('Foto de escala inválida. Envie um JPEG de até 10 MB para o mês selecionado.')
      }
    }


    if (safePath.startsWith('dynamic-pages/')) {
      if (fileValue.size <= 0) throw new Error('Empty media file')
      if (fileValue.size > MAX_DYNAMIC_MEDIA_BYTES) {
        throw new Error('Dynamic media exceeds the 80 MB limit')
      }
      const dynamicType = (fileValue.type || '').toLowerCase()
      if (!DYNAMIC_MEDIA_TYPES.has(dynamicType)) {
        throw new Error(`Unsupported dynamic media type: ${dynamicType || 'unknown'}`)
      }
    }

    const contentType = safePath.toLowerCase().endsWith('.json')
      ? 'application/json'
      : (fileValue.type || 'application/octet-stream')
    const bytes = new Uint8Array(await fileValue.arrayBuffer())
    const { error } = await supabaseAdmin.storage
      .from('nrdlojas-images')
      .upload(safePath, bytes, { upsert: true, contentType })
    if (error) {
      console.error('Storage upload failed', { path: safePath, contentType, message: error.message, statusCode: error.statusCode })
      throw new Error(`Storage upload failed: ${error.message}`)
    }

    const { data: publicUrlData } = supabaseAdmin.storage
      .from('nrdlojas-images')
      .getPublicUrl(safePath)

    const publicUrl = publicUrlData.publicUrl
    if (!publicUrl || !/^https?:\/\//i.test(publicUrl)) {
      throw new Error('Storage did not return a valid public URL')
    }

    return new Response(JSON.stringify({ url: publicUrl }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200
    })
  } catch (error) {
    console.error('Upload error:', error)
    return new Response(JSON.stringify({ error: error instanceof Error ? error.message : String(error) }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400
    })
  }
})
