import {decodeFields} from './documents.mjs';
/** Fresh authoritative permissions. Offline failures never invent or retain grants. */
export function createControlAuthorizer({verify,document,hash}) {
 const denied={enabled:false,profile:false,promotions:false,flyers:false,prices:false,stores:false,publicAccess:false};
 const audience=settings=>settings?.tabAudience&&typeof settings.tabAudience==='object'?settings.tabAudience:{};
 const all=(settings,tab)=>Object.hasOwn(audience(settings),tab)?audience(settings)[tab]==='all':['flyers','stores'].includes(tab);
 return async req=>{
  const token=req.headers.get('x-firebase-token');
  const user=token?await verify(token):null;
  if(user?.email==='mestre@nrdlojas.com')return {...denied,enabled:true,profile:true,promotions:true,flyers:true,prices:true,stores:true};
  if(user?.email?.endsWith('@usuarios.nrdlojas.com')){
   const device=req.headers.get('x-device-session');
   if(!device||!/^[a-f0-9]{64}$/.test(device))return denied;
   const session=decodeFields(await document('access_sessions/'+user.uid));
   if(session.deviceHash!==await hash(device)||session.authTime!==user.authTime)return denied;
  }
  const settings=decodeFields(await document('config/restricted_access'));
  const publicAccess=settings.publicAccess===true;
  if(!user)return {...denied,profile:publicAccess||all(settings,'profile'),promotions:publicAccess||all(settings,'promotions'),flyers:publicAccess||all(settings,'flyers'),prices:publicAccess||all(settings,'prices'),stores:publicAccess||all(settings,'stores'),publicAccess};
  const grant=decodeFields(await document('restricted_access/'+user.uid));
  const hasPublicTab=['profile','promotions','flyers','prices','stores'].some(tab=>all(settings,tab));
  return {...denied,enabled:grant.enabled===true||publicAccess||hasPublicTab,profile:publicAccess||all(settings,'profile')||grant.enabled===true&&grant.profile===true,promotions:publicAccess||all(settings,'promotions')||grant.enabled===true&&grant.promotions===true,flyers:publicAccess||all(settings,'flyers')||grant.enabled===true&&grant.flyers===true,prices:publicAccess||all(settings,'prices')||grant.enabled===true&&grant.prices===true,stores:publicAccess||all(settings,'stores')||grant.enabled===true&&grant.stores===true,publicAccess};
 };
}
