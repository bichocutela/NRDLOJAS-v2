import {createRemoteJWKSet,jwtVerify} from 'npm:jose@5.9.6';
import {authorizedWorkflow,losslessWebp} from './policy.mjs';

// Only the reviewed main-branch maintenance workflow can replace existing PNGs.
// No listing, upload, or delete capability is exposed to app clients.
const keys=createRemoteJWKSet(new URL('https://token.actions.githubusercontent.com/.well-known/jwks'));
const bucket='nrdlojas-images';
const roots=new Set(['theme_backgrounds','dynamic-pages','benefit_card_backgrounds','products','consultation_backgrounds','offer_banners','banners','glass_particles']);
const storage=(Deno.env.get('SUPABASE_URL') ?? '')+'/storage/v1';
const key=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
const headers={apikey:key,Authorization:'Bearer '+key,'Content-Type':'application/json'};

async function list(prefix:string) {
  const rows=[];
  for(let offset=0;offset<5000;offset+=500){
    const response=await fetch(storage+'/object/list/'+bucket,{method:'POST',headers,
      signal:AbortSignal.timeout(15000),body:JSON.stringify({prefix,limit:500,offset,sortBy:{column:'name',order:'asc'}})});
    if(!response.ok)throw Error('STORAGE_LIST_FAILED');
    const batch=await response.json();
    if(!Array.isArray(batch))throw Error('INVALID_LIST');
    rows.push(...batch);
    if(batch.length<500)return rows;
  }
  throw Error('LIST_TOO_LARGE');
}
function validPath(path:unknown):path is string {
  return typeof path==='string' && path.length<600 && roots.has(path.split('/')[0]) &&
    !path.includes('..') && !path.includes('\\') && !/[\x00-\x1f?#]/.test(path) && /\.(png|jpe?g|webp)$/i.test(path);
}
function png(row:any) {
  return row?.metadata?.mimetype==='image/png' && Number(row.metadata.size)>64*1024 && Number(row.metadata.size)<8*1024*1024;
}
Deno.serve(async request=>{
  if(request.method!=='POST')return Response.json({error:'METHOD_NOT_ALLOWED'},{status:405});
  try{
    const {payload}=await jwtVerify(request.headers.get('x-github-oidc-token') ?? '',keys,{
      algorithms:['RS256'],issuer:'https://token.actions.githubusercontent.com',audience:'nrd-storage-optimizer'});
    if(!authorizedWorkflow(payload))throw Error('UNAUTHORIZED');
  }catch{return Response.json({error:'UNAUTHORIZED'},{status:403});}
  try{
    const raw=await request.text();
    if(raw.length>12*1024*1024)return Response.json({error:'BODY_TOO_LARGE'},{status:413});
    const input=JSON.parse(raw);
    if(input.operation==='manifest' && Object.keys(input).length===1){
      const queue=[...roots],files=[];
      while(queue.length){
        const prefix=queue.shift()!;
        if(prefix.split('/').length>8)throw Error('PATH_TOO_DEEP');
        for(const row of await list(prefix)){
          const path=prefix+'/'+row.name;
          if(!row.id && !row.metadata){queue.push(path);continue;}
          if(validPath(path)&&png(row))files.push({path,bytes:Number(row.metadata.size),etag:row.metadata.eTag});
        }
        if(files.length+queue.length>3000)throw Error('MANIFEST_TOO_LARGE');
      }
      return Response.json({files});
    }
    if(input.operation!=='replace' || Object.keys(input).sort().join(',')!=='base64,etag,operation,path' ||
      !validPath(input.path) || typeof input.etag!=='string' || typeof input.base64!=='string'){
      return Response.json({error:'INVALID_REQUEST'},{status:400});
    }
    const slash=input.path.lastIndexOf('/');
    const rows=await list(input.path.slice(0,slash));
    const row=rows.find(row=>row.name===input.path.slice(slash+1));
    if(!png(row) || row.metadata.eTag!==input.etag)return Response.json({error:'SOURCE_CHANGED'},{status:409});
    const binary=atob(input.base64);
    const bytes=Uint8Array.from(binary,character=>character.charCodeAt(0));
    if(bytes.length>=Number(row.metadata.size)*0.9 || !losslessWebp(bytes))return Response.json({error:'INVALID_LOSSLESS_WEBP'},{status:400});
    const path=input.path.split('/').map(encodeURIComponent).join('/');
    const response=await fetch(storage+'/object/'+bucket+'/'+path,{method:'PUT',signal:AbortSignal.timeout(30000),
      headers:{...headers,'Content-Type':'image/webp','Cache-Control':'max-age=3600'},body:bytes});
    if(!response.ok)throw Error('STORAGE_UPDATE_FAILED');
    return Response.json({ok:true,saved:Number(row.metadata.size)-bytes.length});
  }catch(error){
    const message=error instanceof Error && /^[A-Z_]+$/.test(error.message)?error.message:'OPTIMIZATION_FAILED';
    return Response.json({error:message},{status:502});
  }
});
