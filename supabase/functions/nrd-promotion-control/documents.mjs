/** Server-only document storage. No cached authorization or client service key. */
export class ControlDocuments {
  constructor({url,key,fetcher=fetch,now=()=>Date.now(),configCacheMs=15000}) {
    this.url=url?.replace(/\/$/,'');this.key=key;this.fetcher=fetcher;
    // Only non-auth configuration: never cache restricted_access, access_sessions or validity rules.
    this.now=now;this.configCacheMs=configCacheMs;this.localConfig=new Map();this.pendingConfig=new Map();this.generations=new Map();
  }
  valid(path) {
    if(!/^(?:config\/(?:restricted_access|promotion_stores|acpOfferValidity|acpProvider|promotion_[a-zA-Z0-9_-]+)|(?:restricted_access|access_sessions)\/[a-zA-Z0-9_-]{1,128})$/.test(path))throw Error('INVALID_DOCUMENT');
    return path;
  }
  async request(path,options={}) {
    if(!this.url||!this.key)throw Error('CONTROL_UNAVAILABLE');
    const response=await this.fetcher(this.url+'/rest/v1/'+path,{...options,signal:AbortSignal.timeout(10000),headers:{apikey:this.key,Authorization:'Bearer '+this.key,'Content-Type':'application/json',...options.headers}});
    if(!response.ok)throw Error('CONTROL_UNAVAILABLE');return response;
  }
  async get(path) {
    const key=this.valid(path);
    const cacheable=key==='config/acpProvider'||key==='config/promotion_stores';
    if(cacheable) {
      const hit=this.localConfig.get(key);
      if(hit&&hit.until>this.now())return structuredClone(hit.row);
      const pending=this.pendingConfig.get(key);
      if(pending)return structuredClone(await pending);
    }
    const generation=this.generations.get(key)||0;
    const task=(async()=>{
      const query=new URLSearchParams({path:'eq.'+key,select:'path,fields,version,deleted'});
      const row=(await (await this.request('nrd_control_documents?'+query)).json())[0]??null;
      if(cacheable&&(this.generations.get(key)||0)===generation) {
        this.localConfig.set(key,{row:structuredClone(row),until:this.now()+this.configCacheMs});
      }
      return row;
    })();
    if(cacheable)this.pendingConfig.set(key,task);
    try{return structuredClone(await task);}
    finally {if(cacheable&&this.pendingConfig.get(key)===task)this.pendingConfig.delete(key);}
  }
  async fields(path) {const row=await this.get(path);return !row||row.deleted?{}:row.fields;}
  async list(collection) {
    if(!['restricted_access','access_sessions','config'].includes(collection))throw Error('INVALID_DOCUMENT');
    const result=[];
    for(let offset=0;offset<10000;offset+=500){
      const query=new URLSearchParams({path:'like.'+collection+'/*',deleted:'eq.false',select:'path,fields,version',order:'path.asc',limit:'500',offset:String(offset)});
      const rows=await (await this.request('nrd_control_documents?'+query)).json();result.push(...rows);
      if(rows.length<500)return result;
    }
    throw Error('CONTROL_TOO_LARGE');
  }
  async write(path,fields,{merge=false,version=null,insertOnly=false,deleted=false}={}) {
    const key=this.valid(path);
    const response=await this.request('rpc/nrd_control_write',{method:'POST',body:JSON.stringify({p_path:key,p_fields:fields,p_merge:merge,p_version:version,p_insert_only:insertOnly,p_deleted:deleted})});
    const row=(await response.json())[0]??null;
    if(row) {
      this.generations.set(key,(this.generations.get(key)||0)+1);
      this.localConfig.delete(key);
    }
    return row;
  }
}
export function encode(value) {
  if(value===null)return {nullValue:null};
  if(typeof value==='boolean')return {booleanValue:value};
  if(typeof value==='string')return {stringValue:value};
  if(typeof value==='number'&&Number.isFinite(value))return Number.isInteger(value)?{integerValue:String(value)}:{doubleValue:value};
  if(Array.isArray(value))return {arrayValue:{values:value.map(encode)}};
  if(value&&typeof value==='object')return {mapValue:{fields:Object.fromEntries(Object.entries(value).map(([k,v])=>[k,encode(v)]))}};
  throw Error('INVALID_VALUE');
}
export function decode(value) {
  if('nullValue'in value)return null;
  if('booleanValue'in value)return value.booleanValue;
  if('stringValue'in value)return value.stringValue;
  if('integerValue'in value)return Number(value.integerValue);
  if('doubleValue'in value)return value.doubleValue;
  if('mapValue'in value)return decodeFields(value.mapValue.fields??{});
  if('arrayValue'in value)return (value.arrayValue.values??[]).map(decode);
  return null;
}
export function encodeFields(value){return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,encode(v)]));}
export function decodeFields(value){return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,decode(v)]));}
