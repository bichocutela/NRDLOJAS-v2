import { normalizeProductPage } from "./product-contract.mjs";
// Internal pilot. Never forward client-supplied URLs, headers, credentials or raw upstream bodies.
const TENANT = "https://nordestao12.acp.app.br";
const API = "https://api.acp.app.br";
const reply = (status, body) => new Response(JSON.stringify(body), {status, headers: {"Content-Type":"application/json", "Cache-Control":"no-store", "X-Content-Type-Options":"nosniff"}});
export function createHandler({authenticate, credentials, consumeBudget = async()=>false, fetcher = fetch}) {
  return async (req) => {
    if (req.method !== "POST") return reply(405,{error:"METHOD_NOT_ALLOWED"});
    try { if (!(await authenticate(req))) return reply(403,{error:"ACCESS_DENIED"}); }
    catch { return reply(401,{error:"AUTH_REQUIRED"}); }
    let stage="validation";
    try {
      // Bounded streaming read; Content-Length alone is not trustworthy.
      const reader=req.body?.getReader(); if (!reader) return reply(400,{error:"INVALID_REQUEST"});
      let size=0, chunks=[];
      for (;;) { const {done,value}=await reader.read(); if(done)break; size+=value.length; if(size>4096){await reader.cancel();return reply(413,{error:"REQUEST_TOO_LARGE"});} chunks.push(value); }
      const bytes=new Uint8Array(size);let offset=0;for(const c of chunks){bytes.set(c,offset);offset+=c.length;}
      let input;try{input=JSON.parse(new TextDecoder().decode(bytes));}catch{return reply(400,{error:"INVALID_REQUEST"});}
      if (!input || Object.keys(input).some(k=>!["query","field","page"].includes(k)) ||
          typeof input.query!=="string" || input.query.trim().length<2 || input.query.length>120 ||
          !["code","barCode","description"].includes(input.field) ||
          !Number.isInteger(input.page ?? 0) || (input.page ?? 0)<0 || (input.page ?? 0)>100)
        return reply(400,{error:"INVALID_REQUEST"});
      stage="rate-limit";
      if (!(await consumeBudget(req))) return reply(429,{error:"RATE_LIMITED"});
      const secret=credentials();
      if (!secret.login || !secret.password) return reply(503,{error:"SERVICE_NOT_READY"});
      // Every pilot request gets a separate cookie jar; no user/session mixing.
      const jar=new Map();
      const json=async(url,options={})=>{
        const response=await fetcher(url,{...options,redirect:"error",signal:AbortSignal.timeout(15000),
          headers:{Accept:"application/json",...options.headers,...(jar.size?{Cookie:[...jar].map(([k,v])=>k+"="+v).join("; ")}:{})}});
        for(const cookie of response.headers.getSetCookie()){
          const pair=cookie.split(";")[0], eq=pair.indexOf("=");if(eq>0)jar.set(pair.slice(0,eq),pair.slice(eq+1));
        }
        if(!response.ok)throw new Error("UPSTREAM_FAILURE");
        const body=await response.text();if(body.length>2000000)throw new Error("UPSTREAM_FAILURE");
        return JSON.parse(body);
      };
      stage="csrf";
      const csrf=await json(TENANT+"/api/auth/csrf");
      if(typeof csrf.csrfToken!=="string")throw new Error("UPSTREAM_FAILURE");
      stage="login";
      const callback=await json(TENANT+"/api/auth/callback/credentials",{
        method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded",Origin:TENANT,Referer:TENANT+"/login"},
        body:new URLSearchParams({login:secret.login,password:secret.password,csrfToken:csrf.csrfToken,callbackUrl:TENANT+"/print-template",json:"true"})});
      if(callback.error || (typeof callback.url==="string" && new URL(callback.url,TENANT).searchParams.has("error")))throw new Error("UPSTREAM_FAILURE");
      stage="session";
      const session=await json(TENANT+"/api/auth/session");
      if(typeof session.user?.accessToken!=="string" || !session.user.accessToken)throw new Error("UPSTREAM_FAILURE");
      const base=session.user.proxyEnable?TENANT+"/api/proxy/api/v1/":API+"/api/v1/";
      const url=new URL(base+"Product/all");
      url.search=new URLSearchParams({pageSize:"20",pageIndex:String(input.page??0),[input.field]:input.query.trim()}).toString();
      // Tenant cookies must never be forwarded to the API host.
      stage="products";
      const response=await fetcher(url,{method:"GET",redirect:"error",signal:AbortSignal.timeout(15000),
        headers:{Authorization:"Bearer "+session.user.accessToken,Accept:"application/json","Cache-Control":"no-cache",...(session.user.proxyEnable?{Cookie:[...jar].map(([k,v])=>k+"="+v).join("; ")}:{})}});
      if(!response.ok)throw new Error("UPSTREAM_FAILURE");
      const raw=await response.text();if(raw.length>2000000)throw new Error("UPSTREAM_FAILURE");
      const payload=JSON.parse(raw);
      // Fail closed on an unknown envelope; mapping must be validated before Android migration.
      stage="mapping";
      return reply(200,normalizeProductPage(payload,input.page??0));
    } catch { console.warn("NRD_PRICE_PILOT_FAILURE",stage); return reply(502,{error:"CONSULTATION_UNAVAILABLE"}); }
  };
}
