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
 Utilities:{base64DecodeWebSafe:x=>x,base64Encode:x=>x},
 ScriptApp:{getProjectTriggers:()=>[],newTrigger:()=>({timeBased:()=>({everyMinutes:()=>({create(){authorized=true;}})})})},
 Gmail:{Users:{getProfile:()=>({emailAddress:'box@gmail.com'}),Messages:{list:()=>({messages:[{id:'1'}]}),get:()=>({payload:{parts:[
 {filename:'CENTRO_0005.pdf',body:{data:'PDF'}},{parts:[{filename:'CLUBE_0005.pdf',body:{data:'PDF2'}}]},
 {filename:'CENTRO_0012.pdf',body:{data:'CITY'}},{filename:'other.txt',body:{data:'x'}}]}})}}},
 UrlFetchApp:{fetch:(url,options)=>{sent.push(JSON.parse(options.payload));return {getResponseCode:()=>200,getContentText:()=>'{"ok":true}'};}}};
 vm.createContext(context);vm.runInContext(code.replace('__NRD_CONFIG__',JSON.stringify({email:'box@gmail.com',key:'secret',url:'https://test'})),context);
 vm.runInContext('ativarNRD(); receberRelatoriosNRD();',context);
 assert.equal(authorized,true);assert.deepEqual(sent.map(x=>x.filename),['CENTRO_0005.pdf','CLUBE_0005.pdf']);
 const manifest=JSON.parse(readFileSync(new URL('appsscript.json',root),'utf8'));
 assert.equal(manifest.oauthScopes.filter(x=>x.includes('gmail')).join(''),'https://www.googleapis.com/auth/gmail.readonly');
});
