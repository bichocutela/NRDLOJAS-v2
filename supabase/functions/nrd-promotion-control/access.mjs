import {decodeFields} from './documents.mjs';
/** Fresh authoritative permissions. Offline failures never invent or retain grants. */
export function createControlAuthorizer({verify,document,hash}) {
 const denied={enabled:false,profile:false,promotions:false,prices:false,publicAccess:false};
 return async req=>{
  const token=req.headers.get('x-firebase-token');
  const user=token?await verify(token):null;
  if(user?.email==='mestre@nrdlojas.com')return {...denied,enabled:true,profile:true,promotions:true,prices:true};
  if(user?.email?.endsWith('@usuarios.nrdlojas.com')){
   const device=req.headers.get('x-device-session');
   if(!device||!/^[a-f0-9]{64}$/.test(device))return denied;
   const session=decodeFields(await document('access_sessions/'+user.uid));
   if(session.deviceHash!==await hash(device)||session.authTime!==user.authTime)return denied;
  }
  const settings=decodeFields(await document('config/restricted_access'));
  if(settings.publicAccess===true)return {...denied,enabled:true,profile:true,promotions:true,prices:true,publicAccess:true};
  if(!user)return denied;
  const grant=decodeFields(await document('restricted_access/'+user.uid));
  return {...denied,enabled:grant.enabled===true,profile:grant.enabled===true&&grant.profile===true,promotions:grant.enabled===true&&grant.promotions===true,prices:grant.enabled===true&&grant.prices===true};
 };
}
