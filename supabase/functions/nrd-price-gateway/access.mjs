export function createAuthorizer({verify, document, hash}) {
  return async req => {
    const promotionsOnly = req.headers.get('x-nrd-scope') === 'promotions';
    const result = (allowed, master) => ({allowed, master, ...(promotionsOnly ? {promotionsOnly:true} : {})});
    const token = req.headers.get('x-firebase-token');
    let identity = null;
    if (token) identity = await verify(token);
    if (identity?.email === 'mestre@nrdlojas.com') return result(true,true);
    if (identity?.email?.endsWith('@usuarios.nrdlojas.com')) {
      const deviceToken=req.headers.get('x-device-session');
      if(!deviceToken || !/^[a-f0-9]{64}$/.test(deviceToken))return result(false,false);
      const session=await document('access_sessions/'+encodeURIComponent(identity.uid));
      if(session.deviceHash?.stringValue!==await hash(deviceToken) || Number(session.authTime?.integerValue)!==identity.authTime)return result(false,false);
    }
    const settings = await document('config/restricted_access',identity?.token);
    if (settings.publicAccess?.booleanValue === true) return result(true,false);
    if (!identity?.uid) return result(false,false);
    const access = await document('restricted_access/'+encodeURIComponent(identity.uid),identity.token);
    return result(access.enabled?.booleanValue === true && access[promotionsOnly ? "promotions" : "prices"]?.booleanValue === true,false);
  };
}
