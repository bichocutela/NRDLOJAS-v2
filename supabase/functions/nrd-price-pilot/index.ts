import { createRemoteJWKSet, jwtVerify } from "https://deno.land/x/jose@v4.14.4/index.ts";
import { createHandler } from "./handler.mjs";
const keys=createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
const subjects=new WeakMap<Request,string>();
Deno.serve(createHandler({
  authenticate: async(req: Request)=>{
    const token=req.headers.get("x-firebase-token");
    if(!token)throw new Error("AUTH_REQUIRED");
    const {payload}=await jwtVerify(token,keys,{algorithms:["RS256"],issuer:"https://securetoken.google.com/appcodigo-7f245",audience:"appcodigo-7f245"});
    const pilotUid=Deno.env.get("NRD_GATEWAY_PILOT_UID");
    if (!pilotUid || payload.sub!==pilotUid) return false;
    subjects.set(req,pilotUid); return true;
  },
  consumeBudget:async(req: Request)=>{
    const subject=subjects.get(req); if(!subject) return false;
    const response=await fetch(Deno.env.get("SUPABASE_URL")+"/rest/v1/rpc/consume_nrd_price_budget",{
      method:"POST",redirect:"error",signal:AbortSignal.timeout(5000),
      headers:{"Content-Type":"application/json",apikey:Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"",Authorization:"Bearer "+(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"")},
      body:JSON.stringify({p_subject:subject})});
    if(!response.ok)throw new Error("BUDGET_UNAVAILABLE");
    return await response.json()===true;
  },
  credentials:()=>({login:Deno.env.get("NRD_PRICE_LOGIN"),password:Deno.env.get("NRD_PRICE_PASSWORD")})
}));
