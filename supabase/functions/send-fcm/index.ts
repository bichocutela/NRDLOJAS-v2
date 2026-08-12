import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import admin from "npm:firebase-admin@11.11.0"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-firebase-token',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const firebaseToken = req.headers.get('x-firebase-token')
    if (!firebaseToken) {
      throw new Error('Missing x-firebase-token')
    }
        
    // Validacao administrativa adicional compativel com o app local
    const adminPassword = Deno.env.get('ADMIN_PASSWORD') ?? 'nrdlojas'
    if (firebaseToken !== adminPassword) {
      throw new Error('Unauthorized: Invalid admin token')
    }

    const { title, body, topic } = await req.json()
    
    if (!admin.apps.length) {
        const serviceAccountStr = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')
        if (!serviceAccountStr) {
            throw new Error("Missing FIREBASE_SERVICE_ACCOUNT environment variable in Supabase.")
        }
        const serviceAccount = JSON.parse(serviceAccountStr)
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        })
    }

    const response = await admin.messaging().send({
        topic: topic || 'products',
        notification: { title, body }
    })

    return new Response(
      JSON.stringify({ success: true, response }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 }
    )
  } catch (error) {
    console.error("FCM Error:", error)
    return new Response(
      JSON.stringify({ error: error.message }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 }
    )
  }
})
