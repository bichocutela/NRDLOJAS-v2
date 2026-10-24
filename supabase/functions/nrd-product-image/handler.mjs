import { boundedJson } from '../nrd-price-gateway/handler.mjs';
export function createImageHandler({lookup}){
 const headers={'Content-Type':'application/json','Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization, apikey, content-type','Cache-Control':'public, max-age=300'};
 return async request=>{
  if(request.method==='OPTIONS')return new Response(null,{headers});if(request.method!=='POST')return Response.json({error:'METHOD_NOT_ALLOWED'},{status:405,headers});
  let input;try{input=await boundedJson(request,2048);}catch{return Response.json({error:'INVALID_REQUEST'},{status:400,headers});}
  if(!input||Array.isArray(input)||Object.keys(input).some(k=>!['code','barcode','description'].includes(k))||typeof input.description!=='string'||input.description.length>180||![input.code,input.barcode].every(v=>typeof v==='string'&&/^[0-9]{0,30}$/.test(v))||!input.code&&!input.barcode&&!input.description.trim())return Response.json({error:'INVALID_IDENTITY'},{status:400,headers});
  try{
   const records=await lookup([{code:input.code,barCode:input.barcode,description:input.description.trim()}]);
   const images=[...new Set(records.map(r=>r.imagem).filter(value=>{try{const url=new URL(value);return url.protocol==='https:'&&url.hostname==='produto-assets-vipcommerce-com-br.br-se1.magaluobjects.com'&&!url.username&&!url.password&&/\.(jpg|jpeg|png|webp)$/i.test(url.pathname);}catch{return false;}}))];
   return Response.json({images}, {headers});
  }catch{return Response.json({error:'IMAGE_LOOKUP_UNAVAILABLE'},{status:503,headers:{...headers,'Cache-Control':'no-store'}});}
 };
}
