// Read-only readiness check. Never logs credentials or deletes production users.
import {createSign} from 'node:crypto';
const project='appcodigo-7f245';
try {
  const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT ?? '{}');
  if(account.project_id!==project)throw Error('Firebase project configuration mismatch');
  const now=Math.floor(Date.now()/1000);
  const encode=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
  const unsigned=encode({alg:'RS256',typ:'JWT'})+'.'+encode({iss:account.client_email,sub:account.client_email,
    aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+600,scope:'https://www.googleapis.com/auth/iam.test'});
  const signer=createSign('RSA-SHA256');signer.update(unsigned);signer.end();
  const assertion=unsigned+'.'+signer.sign(account.private_key).toString('base64url');
  const oauth=await fetch('https://oauth2.googleapis.com/token',{method:'POST',signal:AbortSignal.timeout(15000),
    body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion})});
  const token=await oauth.json();
  if(!oauth.ok || typeof token.access_token!=='string')throw Error('Firebase readiness OAuth failed');
  const permissions=['firebaseauth.users.get','firebaseauth.users.delete','firebaseauth.users.update','datastore.entities.get','datastore.entities.create','datastore.entities.update','datastore.entities.delete'];
  const response=await fetch(`https://cloudresourcemanager.googleapis.com/v1/projects/${project}:testIamPermissions`,{
    method:'POST',signal:AbortSignal.timeout(15000),headers:{Authorization:'Bearer '+token.access_token,'Content-Type':'application/json'},
    body:JSON.stringify({permissions})});
  if(!response.ok)throw Error('Firebase readiness IAM check failed (HTTP '+response.status+')');
  const granted=(await response.json()).permissions ?? [];
  const missing=permissions.filter(permission=>!granted.includes(permission));
  if(missing.length)throw Error('Missing account administration permissions: '+missing.join(', '));
  console.log('PASS: Firebase service account can manage access accounts (read-only IAM check).');
} catch(error) { console.error(error.message);process.exitCode=1; }
