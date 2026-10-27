import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const root=new URL('../../../app/src/main/assets/mail-import/',import.meta.url);
const code=readFileSync(new URL('connector.js',root),'utf8');
test('private Gmail connector imports all nested PDFs, skips 0012 and does not resend completed messages',()=>{
 const sent=[], props=new Map();let authorized=false;
 const context={Date,JSON,Error,console:{log(){}},LockService:{getScriptLock:()=>({tryLock:()=>true,releaseLock(){}})},
 PropertiesService:{getScriptProperties:()=>({getProperty:k=>props.get(k),setProperty:(k,v)=>props.set(k,v),deleteProperty:k=>props.delete(k)})},
 Utilities:{base64DecodeWebSafe:x=>Array.from(Buffer.from(x,'base64url')),base64Encode:x=>Buffer.from(x).toString('base64')},
 ScriptApp:{getProjectTriggers:()=>[],newTrigger:()=>({timeBased:()=>({everyMinutes:(minutes)=>({create(){assert.equal(minutes,1);authorized=true;}})})})},
 Gmail:{Users:{getProfile:()=>({emailAddress:'box@gmail.com'}),Messages:{list:()=>({messages:[{id:'1'}]}),get:()=>({payload:{parts:[
 {filename:'CENTRO_0005.pdf',body:{data:Array.from(Buffer.from('%PDF-1.4 byte attachment'))}},{parts:[{filename:'CLUBE_0005.pdf',body:{data:Buffer.from('%PDF-1.4 encoded attachment').toString('base64url')}}]},
 {filename:'CENTRO_0012.pdf',body:{data:'CITY'}},{filename:'other.txt',body:{data:'x'}}]}})}}},
 UrlFetchApp:{fetch:(url,options)=>{
     const payload=JSON.parse(options.payload);
     if(payload.operation==='ingest')sent.push(payload);
     return {getResponseCode:()=>200,getContentText:()=>payload.operation==='poll'
       ? '{"requestId":"scan-1","finishedId":null}' : '{"ok":true}'};
 }}};
 vm.createContext(context);vm.runInContext(code.replace('__NRD_CONFIG__',JSON.stringify({email:'box@gmail.com',key:'secret',url:'https://test'})),context);
 vm.runInContext('ativarNRD(); receberRelatoriosNRD();',context);
 assert.equal(authorized,true);assert.deepEqual(sent.map(x=>x.filename),['CENTRO_0005.pdf','CLUBE_0005.pdf']);
 assert.equal(Buffer.from(sent[0].pdf,'base64').toString(),'%PDF-1.4 byte attachment');
 assert.equal(Buffer.from(sent[1].pdf,'base64').toString(),'%PDF-1.4 encoded attachment');
 const manifest=JSON.parse(readFileSync(new URL('appsscript.json',root),'utf8'));
 assert.equal(manifest.oauthScopes.filter(x=>x.includes('gmail')).join(''),'https://www.googleapis.com/auth/gmail.readonly');
});

test('button scan is acknowledged only after a complete mail pass; later minute ticks skip unnecessary Gmail searches',()=>{
 const calls=[], properties=new Map();let now=2000000, polls=0, lists=0;
 const fakeDate={now:()=>now};
 const context={Date:fakeDate,JSON,Error,console:{log(){}},
  LockService:{getScriptLock:()=>({tryLock:()=>true,releaseLock(){}})},
  PropertiesService:{getScriptProperties:()=>({getProperty:k=>properties.get(k),setProperty:(k,v)=>properties.set(k,v),deleteProperty:k=>properties.delete(k)})},
  Gmail:{Users:{getProfile:()=>({emailAddress:'box@gmail.com'}),Messages:{
    list:()=>{lists++;return {messages:[]};},get:()=>{throw Error('unexpected get');}
  }}},
  UrlFetchApp:{fetch:(_url,options)=>{
    const data=JSON.parse(options.payload);calls.push(data);
    if(data.operation==='poll')return {getResponseCode:()=>200,getContentText:()=>{
      polls++;return JSON.stringify({requestId:polls<3?'11111111-1111-4111-8111-111111111111':null,finishedId:polls<3?null:'11111111-1111-4111-8111-111111111111'});
    }};
    return {getResponseCode:()=>200,getContentText:()=>'{"ok":true}'};
  }}};
 vm.createContext(context);vm.runInContext(code.replace('__NRD_CONFIG__',JSON.stringify({email:'box@gmail.com',key:'secret',url:'https://test'})),context);
 vm.runInContext('receberRelatoriosNRD(); receberRelatoriosNRD(); receberRelatoriosNRD();',context);
 assert.equal(lists,2);
 assert.equal(calls.filter(x=>x.operation==='ack').length,2);
 assert.equal(calls.filter(x=>x.operation==='ack').every(x=>x.failed===false),true);
});
