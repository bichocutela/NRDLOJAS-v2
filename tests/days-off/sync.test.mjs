import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
const source = stripTypeScriptTypes(readFileSync(new URL('../../supabase/functions/save-my-days-off/index.ts', import.meta.url), 'utf8').replace(/^import .*;\n/gm, ''));
const fields = value => Object.fromEntries(Object.entries(value).map(([k,v]) => [k, encode(v)]));
function encode(v) {
  if (Array.isArray(v)) return {arrayValue:{values:v.map(encode)}};
  if (typeof v === 'object') return {mapValue:{fields:fields(v)}};
  if (typeof v === 'number') return {integerValue:String(v)};
  if (typeof v === 'boolean') return {booleanValue:v};
  return {stringValue:v};
}
const own = {registration:'123',name:'Pessoa Um',daysOff:[1,7],vacationDays:[],shift:'08:00'};
const other = {registration:'456',name:'Pessoa Dois',daysOff:[2,8],vacationDays:[]};
function setup(identityStatus = 200) {
  let handler;
  const calls=[];
  const jwt = {setProtectedHeader(){return this},setIssuer(){return this},setSubject(){return this},setAudience(){return this},setIssuedAt(){return this},setExpirationTime(){return this},async sign(){return 'mock'}};
  const document = {name:'projects/test/databases/(default)/documents/work_schedules/2026-10',updateTime:'timestamp', fields:fields({year:2026,month:10,employees:[own,other],updatedAt:1,revision:1})};
  const fetch = async (url, options = {}) => {
    calls.push({url,options});
    if (url.endsWith('/me')) return Response.json({matricula:'123',nome:'Pessoa Um'},{status:identityStatus});
    if (url.includes('oauth2')) return Response.json({access_token:'server-only'});
    if (options.method === 'PATCH') return Response.json({});
    if (url.includes('pageToken=next')) return Response.json({documents:[]});
    if (url.includes('pageSize')) return Response.json({documents:[document],nextPageToken:'next'});
    return Response.json(document);
  };
  vm.runInNewContext(source,{Deno:{serve:fn=>handler=fn,env:{get:()=>JSON.stringify({private_key:'mock',client_email:'mock'})}},importPKCS8:async()=>'',SignJWT:function(){return jwt},Request,Response,URLSearchParams,fetch,console});
  return {calls, request: input => handler(new Request('https://example.test',{method:'POST',headers:{'x-nossa-gente-token':'session'},body:JSON.stringify(input)})), anonymous:()=>handler(new Request('https://example.test',{method:'POST',body:'{}'}))};
}
test('restores only authenticated employee, ignores supplied registration and follows pages',async()=>{
 const app=setup(); const response=await app.request({action:'readMySchedules',registration:'456'});
 assert.equal(response.status,200); const data=await response.json();
 assert.equal(data.registration,'123'); assert.deepEqual(data.schedules[0].employees,[own]);
 assert.equal(app.calls.filter(c=>c.url.includes('pageSize')).length,2);
 assert.equal(app.calls.some(c=>c.options.method==='PATCH'),false);
});
test('rejects anonymous and expired sessions before reading schedules',async()=>{
 const app=setup(401); assert.equal((await app.anonymous()).status,401); assert.equal(app.calls.length,0);
 assert.equal((await app.request({action:'readMySchedules'})).status,401); assert.equal(app.calls.length,1);
});
test('saves own days with concurrency protection and preserves other employee',async()=>{
 const app=setup(); const response=await app.request({year:2026,month:10,daysOff:[14,7,7],registration:'456'});
 assert.equal(response.status,200); const write=app.calls.find(c=>c.options.method==='PATCH');
 assert.match(write.url,/currentDocument.updateTime=timestamp/);
 const rows=JSON.parse(write.options.body).fields.employees.arrayValue.values;
 assert.deepEqual(rows[1],encode(other));
 assert.deepEqual(rows[0].mapValue.fields.daysOff,encode([7,14]));
});
test('rejects dates outside chosen month without touching Firestore',async()=>{
 const app=setup(); assert.equal((await app.request({year:2026,month:2,daysOff:[30]})).status,400);
 assert.equal(app.calls.length,1);
});
