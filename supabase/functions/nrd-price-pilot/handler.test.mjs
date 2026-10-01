import {test} from "node:test";
import assert from "node:assert/strict";
import {createHandler} from "./handler.mjs";
const request=(body)=>new Request("https://nrd.invalid",{method:"POST",body:JSON.stringify(body)});
const valid={field:"description",query:"downy",page:0};
test("unauthorized callers never contact supplier",async()=>{
 let calls=0;const h=createHandler({consumeBudget:async()=>true,authenticate:async()=>false,credentials:()=>({}),fetcher:async()=>{calls++;}});
 assert.equal((await h(request(valid))).status,403);assert.equal(calls,0);
});
test("URLs and oversized requests are rejected before credentials or supplier use",async()=>{
 const h=createHandler({consumeBudget:async()=>true,authenticate:async()=>true,credentials:()=>{throw Error("must not read");}});
 assert.equal((await h(request({...valid,url:"https://evil.invalid"}))).status,400);
 assert.equal((await h(request({...valid,query:"x".repeat(5000)}))).status,413);
});
test("missing secrets fail closed",async()=>{
 const h=createHandler({consumeBudget:async()=>true,authenticate:async()=>true,credentials:()=>({})});
 assert.equal((await h(request(valid))).status,503);
});
test("secrets and raw upstream fields never leave the gateway; cookies stay at tenant",async()=>{
 const calls=[];
 const responses=[{csrfToken:"csrf"},{url:"/print-template"},{user:{accessToken:"private-token",proxyEnable:false}},{data:[{code:"251783",description:"Queijo",value:149.99,accessToken:"leak",url:"private",user:{password:"leak"}}]}];
 const h=createHandler({consumeBudget:async()=>true,authenticate:async()=>true,credentials:()=>({login:"private-login",password:"private-password"}),fetcher:async(url,options)=>{
 calls.push({url:String(url),options});
 return new Response(JSON.stringify(responses.shift()),{headers:{"set-cookie":"session=private-cookie; Secure; HttpOnly"}});
 }});
 const res=await h(request(valid));assert.equal(res.status,200);
 const output=await res.text();assert.ok(!/private|accessToken|password/.test(output));
 assert.equal(calls[3].options.headers.Cookie,undefined);
 assert.equal(calls[3].options.method,"GET");
 assert.equal(new URL(calls[3].url).searchParams.get("pageSize"),"20");
});
test("upstream exceptions return only a neutral error",async()=>{
 const h=createHandler({consumeBudget:async()=>true,authenticate:async()=>true,credentials:()=>({login:"x",password:"y"}),fetcher:async()=>{throw Error("supplier token=secret");}});
 const res=await h(request(valid));assert.equal(res.status,502);
 assert.deepEqual(await res.json(),{error:"CONSULTATION_UNAVAILABLE"});
});
test("unknown supplier envelope is not forwarded",async()=>{
 let n=0;const payloads=[{csrfToken:"x"},{},{user:{accessToken:"x"}},{sensitive:"secret"}];
 const h=createHandler({consumeBudget:async()=>true,authenticate:async()=>true,credentials:()=>({login:"x",password:"y"}),fetcher:async()=>new Response(JSON.stringify(payloads[n++]))});
 assert.equal((await h(request(valid))).status,502);
});

test("real items envelope is accepted without exposing nested metadata",async()=>{
 let n=0;const payloads=[{csrfToken:"x"},{},{user:{accessToken:"x"}},{items:[{code:"25",description:"Produto",quantityTake:4,quantityPay:3,secondUnitDiscount:50,user:{secret:"x"}}],totalCount:1}];
 const h=createHandler({consumeBudget:async()=>true,authenticate:async()=>true,credentials:()=>({login:"x",password:"y"}),fetcher:async()=>new Response(JSON.stringify(payloads[n++]))});
 const res=await h(request(valid));assert.equal(res.status,200);
 const data=await res.json();assert.equal(data.products[0].quantityTake,4);assert.equal(data.products[0].quantityPay,3);assert.equal(data.products[0].secondUnitDiscount,50);assert.equal(data.products[0].user,undefined);
});

test("exhausted budget blocks supplier access",async()=>{
 let called=false;
 const h=createHandler({authenticate:async()=>true,consumeBudget:async()=>false,credentials:()=>{called=true;return{};}});
 assert.equal((await h(request(valid))).status,429);assert.equal(called,false);
});
test("unavailable budget does not fail open",async()=>{
 const h=createHandler({authenticate:async()=>true,consumeBudget:async()=>{throw Error("db");},credentials:()=>{throw Error("must not read");}});
 assert.equal((await h(request(valid))).status,502);
});
