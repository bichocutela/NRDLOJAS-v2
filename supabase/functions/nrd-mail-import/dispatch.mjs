/** Server-only invocation. Never return the connector URL or capability to an app client. */
export async function dispatchScan({url,token,requestId,fetcher=fetch}) {
  const target=new URL(url);
  if(target.protocol!=='https:' || target.hostname!=='script.google.com' || target.port ||
    target.username || target.password || target.search || target.hash ||
    !/^\/macros\/s\/[A-Za-z0-9_-]+\/exec$/.test(target.pathname) ||
    !/^[a-f0-9]{64}$/.test(token || '') || !/^[a-f0-9-]{36}$/.test(requestId || '')) throw Error('INVALID_CONNECTOR');
  const response=await fetcher(target.href,{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({requestId,token}),signal:AbortSignal.timeout(130000)});
  if(!response.ok || (await response.json()).ok!==true) throw Error('CONNECTOR_FAILED');
}
