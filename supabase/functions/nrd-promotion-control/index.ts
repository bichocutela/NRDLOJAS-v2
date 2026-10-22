import {createRemoteJWKSet,jwtVerify} from 'npm:jose@5.9.6';
import {SupabaseCache} from '../nrd-price-gateway/supabase-cache.mjs';
import {ControlDocuments} from './documents.mjs';
import {createControlHandler} from './handler.mjs';
import {createControlAuthorizer} from './access.mjs';
const project='appcodigo-7f245';
const jwks=createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
const documents=new ControlDocuments({url:Deno.env.get('SUPABASE_URL'),key:Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')});
const verify=async(token:string)=>{
 const {payload}=await jwtVerify(token,jwks,{algorithms:['RS256'],issuer:`https://securetoken.google.com/${project}`,audience:project});
 if(!payload.sub||!/^[a-zA-Z0-9_-]{1,128}$/.test(payload.sub))throw Error('INVALID_IDENTITY');
 return {uid:payload.sub,email:payload.email,authTime:payload.auth_time};
};
const authorize=createControlAuthorizer({verify,document:(path:string)=>documents.fields(path),hash:async(value:string)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)))).map(v=>v.toString(16).padStart(2,'0')).join('')});
const handler=createControlHandler({verify,authorize,documents});
const cache=new SupabaseCache({url:Deno.env.get('SUPABASE_URL'),key:Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')});
Deno.serve(async req=>{
 try {if(!(await cache.get('promotion_control_migration'))?.payload.ready)return new Response(JSON.stringify({error:'MIGRATION_PENDING'}),{status:503,headers:{'Content-Type':'application/json'}});}
 catch {return new Response(JSON.stringify({error:'CONTROL_UNAVAILABLE'}),{status:503});}
 return handler(req);
});
