export async function encartePdf(id,tabloids,{fetcher=fetch,now=Date.now(),limit=25*1024*1024}={}){
 if(!/^[1-9]\d{0,14}$/.test(id))return new Response(null,{status:400});
 const item=tabloids.find(t=>String(t.id)===id);if(!item)return new Response(null,{status:404});
 const date=s=>Date.parse(s+(/Z$|[+-]\d\d:\d\d$/.test(s)?'':'-03:00'));
 if(!(date(item.startDate)<=now&&date(item.endDate)>=now))return new Response(null,{status:404});
 const url=new URL(item.uri);if(url.protocol!=='https:'||url.hostname!=='datalakeidever.blob.core.windows.net'||url.username||url.password||url.port||!url.pathname.toLowerCase().endsWith('.pdf'))return new Response(null,{status:400});
 const response=await fetcher(url,{redirect:'error',signal:AbortSignal.timeout(20000)});if(!response.ok)throw Error('PDF_UNAVAILABLE');
 if(Number(response.headers.get('Content-Length'))>limit)throw Error('PDF_TOO_LARGE');
 const reader=response.body?.getReader();if(!reader)throw Error('EMPTY_PDF');const chunks=[];let length=0;
 try{for(;;){const {done,value}=await reader.read();if(done)break;length+=value.length;if(length>limit)throw Error('PDF_TOO_LARGE');chunks.push(value);}}catch(e){await reader.cancel().catch(()=>{});throw e;}
 const bytes=new Uint8Array(length);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
 if(new TextDecoder().decode(bytes.slice(0,5))!=='%PDF-')throw Error('INVALID_PDF');
 return new Response(bytes,{headers:{'Content-Type':'application/pdf','Content-Length':String(length),'Cache-Control':'public, max-age=300','Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization, apikey, content-type','X-Content-Type-Options':'nosniff'}});
}
