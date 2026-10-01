import { createRemoteJWKSet, jwtVerify } from "https://deno.land/x/jose@v4.14.4/index.ts";
import { createHandler } from "./handler.mjs";
const keys=createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
Deno.serve(createHandler({
  authenticate: async(req: Request)=>{
    const token=req.headers.get("x-firebase-token");
    if(!token)throw new Error("AUTH_REQUIRED");
    const {payload}=await jwtVerify(token,keys,{algorithms:["RS256"],issuer:"https://securetoken.google.com/appcodigo-7f245",audience:"appcodigo-7f245"});
    const pilotUid=Deno.env.get("NRD_GATEWAY_PILOT_UID");
    return !!pilotUid && payload.sub===pilotUid;
  },
  credentials:()=>({login:Deno.env.get("NRD_PRICE_LOGIN"),password:Deno.env.get("NRD_PRICE_PASSWORD")})
}));
