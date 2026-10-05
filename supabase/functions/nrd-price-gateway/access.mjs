export function createAuthorizer({verify, document, hash}) {
  return async req => {
    const token = req.headers.get('x-firebase-token');
    let identity = null;
    if (token) identity = await verify(token);
    if (identity?.email === 'mestre@nrdlojas.com') return {allowed:true,master:true};
    if (identity?.email?.endsWith('@usuarios.nrdlojas.com')) {
      const deviceToken=req.headers.get('x-device-session');
      if(!deviceToken || !/^[a-f0-9]{64}$/.test(deviceToken))return {allowed:false,master:false};
      const session=await document('access_sessions/'+encodeURIComponent(identity.uid));
      if(session.deviceHash?.stringValue!==await hash(deviceToken) || Number(session.authTime?.integerValue)!==identity.authTime)return {allowed:false,master:false};
    }
    const settings = await document('config/restricted_access',identity?.token);
    if (settings.publicAccess?.booleanValue === true) return {allowed:true,master:false};
    if (!identity?.uid) return {allowed:false,master:false};
    const access = await document('restricted_access/'+encodeURIComponent(identity.uid),identity.token);
    return {allowed:access.enabled?.booleanValue === true && access.prices?.booleanValue === true,master:false};
  };
}
