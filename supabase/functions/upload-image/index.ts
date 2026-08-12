import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.7.1"
import * as jose from "https://deno.land/x/jose@v4.14.4/index.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-firebase-token',
}

const JWKS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'
const JWKS = jose.createRemoteJWKSet(new URL(JWKS_URL))

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const firebaseToken = req.headers.get('x-firebase-token')
    if (!firebaseToken) {
      throw new Error('Missing x-firebase-token')
    }
    
    // Hardcoding as requested
    const FIREBASE_PROJECT_ID = 'appcodigo-7f245'

    // Validate the Firebase JWT 
    const { payload } = await jose.jwtVerify(firebaseToken, JWKS, {
      issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`,
      audience: FIREBASE_PROJECT_ID,
    })
    
    // Check subject/uid
    if (!payload.sub) {
        throw new Error('Missing subject in token')
    }
    
    const email = payload.email as string
    
    if (email !== 'mestre@nrdlojas.com' && email !== 'admin@nrdlojas.com') {
        throw new Error('Unauthorized email: ' + email)
    }

    // Process multipart/form-data
    const formData = await req.formData()
    const path = formData.get('path')
    const file = formData.get('file')

    if (!path || !file) {
      throw new Error('Missing path or file')
    }

    // Upload to Supabase Storage using service role
    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const { data, error } = await supabaseAdmin.storage
      .from('nrdlojas-images')
      .upload(path as string, file as File, {
        upsert: true,
      })

    if (error) {
      throw error
    }

    const { data: publicUrlData } = supabaseAdmin.storage
      .from('nrdlojas-images')
      .getPublicUrl(path as string)

    return new Response(
      JSON.stringify({ url: publicUrlData.publicUrl }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 }
    )
  } catch (error) {
    console.error("Upload error:", error)
    return new Response(
      JSON.stringify({ error: error.message }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 }
    )
  }
})
