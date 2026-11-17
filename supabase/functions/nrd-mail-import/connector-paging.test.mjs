import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {createHash} from 'node:crypto';
const code=readFileSync(new URL('./connector.gs',import.meta.url),'utf8');
function fixture() {
 const props=new Map(), calls=[], pages=[], reads=[];
 let time=2_000_000, release=0, reject=false, pause=false, pending=true;
 const context={ContentService:{MimeType:{JSON:'json'},createTextOutput:text=>({text,setMimeType(){return this;}})},Date:{now:()=>time},JSON,Error,console:{log(){}},
  LockService:{getScriptLock:()=>({tryLock:()=>true,releaseLock(){release++;}})},
  PropertiesService:{getScriptProperties:()=>({getProperty:k=>props.get(k),setProperty:(k,v)=>props.set(k,v),deleteProperty:k=>props.delete(k)})},
  Utilities:{DigestAlgorithm:{SHA_256:'sha256'},computeDigest:(_algorithm,value)=>Array.from(createHash('sha256').update(value).digest()),base64DecodeWebSafe:x=>Buffer.from(x,'base64url'),base64Encode:x=>Buffer.from(x).toString('base64')},
  Gmail:{Users:{getProfile:()=>({emailAddress:'box@gmail.com'}),Messages:{
   list:(_user,params)=>{const page=params.pageToken||'first';pages.push(page);return {messages:(page==='first'?['1']:['2','3']).map(id=>({id})),...(page==='first'?{nextPageToken:'second'}:{})};},
   get:(_user,id)=>{reads.push(id);if(pause&&id==='2')time+=90001;return {payload:{parts:[{filename:'CENTRO_0005.pdf',body:{data:Buffer.from('%PDF-1.4 '+id).toString('base64url')}}]}};}
  }}},
  UrlFetchApp:{fetch:(_url,options)=>{const data=JSON.parse(options.payload);calls.push(data);
   return {getResponseCode:()=>data.operation==='ingest'&&reject?400:200,
    getContentText:()=>JSON.stringify(data.operation==='poll'?{requestId:pending?'11111111-1111-4111-8111-111111111111':null,finishedId:null}:{ok:!reject,error:'INVALID_PDF'})};
  }}
 };
 vm.createContext(context);vm.runInContext(code.replace('__NRD_CONFIG__',JSON.stringify({email:'box@gmail.com',url:'https://test',key:'synthetic'})),context);
 return {props,calls,pages,reads,run:()=>vm.runInContext('buscarAgoraNRD()',context),reject:v=>{reject=v;},pause:v=>{pause=v;},pending:v=>{pending=v;},scheduled:()=>vm.runInContext('receberRelatoriosNRD()',context),post:body=>context.doPost({postData:{contents:JSON.stringify(body)}}),get released(){return release;}};
}
test('daily scan reads every available page in one run and acknowledges only the completed pass',()=>{
 const f=fixture();f.run();
 assert.deepEqual(f.pages,['first','second']);assert.deepEqual(f.reads,['1','2','3']);
 assert.equal(f.calls.filter(c=>c.operation==='poll').length,1);
 assert.equal(f.calls.filter(c=>c.operation==='ingest').length,3);
 assert.equal(f.calls.at(-1).operation,'ack');assert.equal(f.calls.at(-1).failed,false);
 assert.equal(f.props.has('NRD_PAGE'),false);assert.equal(f.props.has('NRD_LAST_SCAN'),true);
 f.run();assert.equal(f.calls.filter(c=>c.operation==='ingest').length,3);assert.equal(f.released,2);
});
test('time limit retains the unfinished page; a forced retry resumes without resending completed messages',()=>{
 const f=fixture();f.pause(true);f.run();
 assert.equal(f.props.get('NRD_PAGE'),'second');assert.equal(f.props.has('NRD_LAST_SCAN'),false);
 assert.equal(f.calls.some(c=>c.operation==='ack'),false);
 assert.equal(f.calls.filter(c=>c.operation==='ingest').length,1);
 f.pause(false);f.run();
 assert.deepEqual(f.pages,['first','second','second']);
 assert.equal(f.calls.filter(c=>c.operation==='ingest').length,3);
 assert.equal(f.props.has('NRD_PAGE'),false);assert.equal(f.calls.at(-1).failed,false);
});
test('a rejected attachment does not advance the cursor or mark its message as completed',()=>{
 const f=fixture();f.props.set('NRD_PAGE','second');f.reject(true);
 assert.throws(()=>f.run(),/anexos pendentes/);
 assert.equal(f.props.get('NRD_PAGE'),'second');assert.equal(f.props.has('NRD_DONE'),false);
 assert.equal(f.calls.at(-1).failed,true);assert.equal(f.released,1);
 f.reject(false);f.run();assert.equal(f.calls.at(-1).failed,false);
});
test('a recent completed scan can be forced immediately without adding a periodic trigger',()=>{
 const f=fixture();f.props.set('NRD_LAST_SCAN','2000000');f.run();
 assert.equal(f.pages.length,2);assert.equal(f.props.has('NRD_FORCE_SCAN'),false);
});

test('no pending app request means no Gmail access, even with a saved continuation page',()=>{
 const f=fixture();f.pending(false);f.props.set('NRD_PAGE','second');f.scheduled();
 assert.equal(f.pages.length,0);assert.equal(f.reads.length,0);
});
test('direct invocation rejects invalid credentials before polling or opening Gmail',()=>{
 const f=fixture();const response=f.post({token:'a'.repeat(64),requestId:'11111111-1111-4111-8111-111111111111'});
 assert.equal(response.text,'{"ok":false}');assert.equal(f.calls.length,0);assert.equal(f.pages.length,0);
 const token=createHash('sha256').update('synthetic').digest('hex');
 assert.equal(f.post({token,requestId:'11111111-1111-4111-8111-111111111111'}).text,'{"ok":true}');
 assert.equal(f.pages.length,2);
});
