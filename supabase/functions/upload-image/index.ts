import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.7.1"
import * as jose from "https://deno.land/x/jose@v4.14.4/index.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-firebase-token',
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

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  try {
    const firebaseToken = req.headers.get('x-firebase-token')
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

    const formData = await req.formData()
    const pathValue = formData.get('path')
    const fileValue = formData.get('file')
    if (!pathValue || !(fileValue instanceof File)) throw new Error('Missing path or file')

    const safePath = String(pathValue).replace(/^\/+/, '')
    if (!safePath || safePath.includes('..')) throw new Error('Invalid path')
    if (safePath === 'banners/themes' || safePath.startsWith('banners/themes/')) {
      throw new Error('Protected theme path')
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

    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

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
