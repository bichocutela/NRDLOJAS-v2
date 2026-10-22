export function authorizedWorkflow(payload) {
  return payload.repository==='bichocutela/NRDLOJAS-v2' && payload.repository_id==='1332397983' &&
    payload.repository_owner_id==='186314089' && payload.ref==='refs/heads/main' &&
    payload.workflow_ref==='bichocutela/NRDLOJAS-v2/.github/workflows/storage-optimizer.yml@refs/heads/main' &&
    ['push','workflow_dispatch'].includes(String(payload.event_name));
}

export function losslessWebp(bytes) {
  const ascii=(offset,size)=>String.fromCharCode(...bytes.subarray(offset,offset+size));
  if(bytes.length<25 || ascii(0,4)!=='RIFF' || ascii(8,4)!=='WEBP')return false;
  const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  if(view.getUint32(4,true)+8!==bytes.length)return false;
  let lossless=false,offset=12;
  while(offset+8<=bytes.length){
    const tag=ascii(offset,4),size=view.getUint32(offset+4,true);
    if(offset+8+size>bytes.length || ['VP8 ','ANIM','ANMF'].includes(tag))return false;
    if(tag==='VP8L'){
      if(lossless || size<5 || bytes[offset+8]!==0x2f)return false;
      lossless=true;
    }
    offset+=8+size+(size%2);
  }
  return lossless && offset===bytes.length;
}
