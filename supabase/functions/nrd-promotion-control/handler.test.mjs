import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createControlHandler} from './handler.mjs';
import {createControlAuthorizer} from './access.mjs';
import {ControlDocuments,encodeFields,decodeFields} from './documents.mjs';
const fields=encodeFields({enabled:true,profile:true,promotions:false,prices:false});
const authorize=createControlAuthorizer({verify:async()=>({uid:'u1',email:'one@usuarios.nrdlojas.com',authTime:7}),document:async path=>path==='access_sessions/u1'?encodeFields({deviceHash:'digest',authTime:7}):path==='config/restricted_access'?encodeFields({publicAccess:false}):fields,hash:async()=> 'digest'});
const req=(body,headers={})=>new Request('https://test',{method:'POST',headers,body:JSON.stringify(body)});
test('fresh profile-only grant works, missing or foreign session fails closed',async()=>{
 const granted=await authorize(req({}, {'x-firebase-token':'valid','x-device-session':'a'.repeat(64)}));
 assert.equal(granted.profile,true);assert.equal(granted.promotions,false);
 assert.equal((await authorize(req({}, {'x-firebase-token':'valid'}))).enabled,false);
 const denied=createControlAuthorizer({verify:async()=>({uid:'u1',email:'one@usuarios.nrdlojas.com',authTime:8}),document:async()=>encodeFields({deviceHash:'digest',authTime:7}),hash:async()=> 'digest'});
 assert.equal((await denied(req({}, {'x-firebase-token':'valid','x-device-session':'a'.repeat(64)}))).enabled,false);
});
test('control documents round trip null, lists, numbers and nested maps',()=>{
 const value={bool:false,n:12,price:3.25,items:[{name:'Clube',to:null}],store:{enabled:true}};
 assert.deepEqual(decodeFields(encodeFields(value)),value);
 const docs=new ControlDocuments({});assert.throws(()=>docs.valid('config/anything'));assert.throws(()=>docs.valid('restricted_access/../master'));
});
test('anonymous client cannot list grants, mutate settings or read private documents',async()=>{
 const documents=new ControlDocuments({});let writes=0;documents.write=async()=>{writes++;};
 const handler=createControlHandler({verify:async()=>null,authorize:async()=>({enabled:false}),documents});
 for(const body of [{operation:'list'},{operation:'read',path:'restricted_access/other'},{operation:'write',path:'config/restricted_access',data:{publicAccess:true},merge:false}])assert.equal((await handler(req(body))).status,403);
 assert.equal(writes,0);
});
test('public read is protected by current policy and profile-only access cannot read products',async()=>{
 const documents=new ControlDocuments({});documents.fields=async()=>encodeFields({x:3});
 const handler=createControlHandler({verify:async()=>null,authorize:async()=>({enabled:true,profile:true,promotions:false,prices:false}),documents});
 assert.equal((await handler(req({operation:'read',path:'config/promotion_stores'}))).status,403);
});
test('master merges fields with CAS and retries a competing edit',async()=>{
 const documents=new ControlDocuments({});let attempt=0;
 documents.get=async()=>({fields:encodeFields({'0012':{count:4,enabled:true}}),version:5});
 documents.write=async(path,fields,options)=>{assert.equal(options.version,5);assert.equal(decodeFields(fields)['0012'].count,4);return ++attempt===1?null:{version:6};};
 const handler=createControlHandler({verify:async()=>({email:'mestre@nrdlojas.com'}),authorize,documents});
 assert.equal((await handler(req({operation:'write',path:'config/promotion_stores',data:{'0012':{enabled:false}},merge:true},{'x-firebase-token':'master'}))).status,200);
 assert.equal(attempt,2);
});
