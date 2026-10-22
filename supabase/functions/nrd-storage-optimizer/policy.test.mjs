import {test} from 'node:test';
import assert from 'node:assert/strict';
import {authorizedWorkflow,losslessWebp} from './policy.mjs';
test('only the exact main-branch maintenance workflow can replace images',()=>{
  const claims={repository:'bichocutela/NRDLOJAS-v2',repository_id:'1332397983',repository_owner_id:'186314089',ref:'refs/heads/main',
    workflow_ref:'bichocutela/NRDLOJAS-v2/.github/workflows/storage-optimizer.yml@refs/heads/main',event_name:'push'};
  assert.ok(authorizedWorkflow(claims));
  for(const [key,value] of Object.entries({repository_id:'1',repository_owner_id:'1',repository:'other/NRDLOJAS-v2',ref:'refs/pull/1/merge',workflow_ref:'bichocutela/NRDLOJAS-v2/.github/workflows/main.yml@refs/heads/main',event_name:'pull_request'})){
    assert.equal(authorizedWorkflow({...claims,[key]:value}),false,key);
  }
});
test('lossless replacement rejects lossy, animated, truncated, or forged container lengths',()=>{
  const file=Uint8Array.from(Buffer.concat([Buffer.from('RIFF'),Buffer.from([18,0,0,0]),Buffer.from('WEBPVP8L'),Buffer.from([5,0,0,0,0x2f,0,0,0,0,0])]));
  assert.ok(losslessWebp(file));
  assert.equal(losslessWebp(file.slice(0,-1)),false);
  const lossy=file.slice();lossy.set(Buffer.from('VP8 '),12);assert.equal(losslessWebp(lossy),false);
  const animated=file.slice();animated.set(Buffer.from('ANMF'),12);assert.equal(losslessWebp(animated),false);
  const fake=file.slice();fake[4]=255;assert.equal(losslessWebp(fake),false);
});
