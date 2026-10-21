import {test} from 'node:test';
import assert from 'node:assert/strict';
import {providerOrder,providerSettingsFromFields} from './provider-routing.mjs';
import {encodeFields} from '../nrd-promotion-control/documents.mjs';

test('stored control document overrides the deployment default after field decoding',()=>{
  const settings={legacyEnabled:true,acpTechEnabled:true,primary:'legacy'};
  assert.deepEqual(providerSettingsFromFields(encodeFields(settings),'acptech'),settings);
  assert.deepEqual(providerOrder(providerSettingsFromFields(encodeFields(settings),'acptech'),'acptech'),['legacy','acptech']);
  assert.deepEqual(providerOrder(providerSettingsFromFields({},'acptech'),'acptech'),['acptech']);
});

test('keeps the configured provider as the default when no master setting exists',()=>{
  assert.deepEqual(providerOrder({},'acptech'),['acptech']);
  assert.deepEqual(providerOrder({},'legacy'),['legacy']);
});

test('supports each provider alone and both providers with an explicit priority',()=>{
  assert.deepEqual(providerOrder({legacyEnabled:true,acpTechEnabled:false,primary:'acptech'}),['legacy']);
  assert.deepEqual(providerOrder({legacyEnabled:false,acpTechEnabled:true,primary:'legacy'}),['acptech']);
  assert.deepEqual(providerOrder({legacyEnabled:true,acpTechEnabled:true,primary:'legacy'}),['legacy','acptech']);
  assert.deepEqual(providerOrder({legacyEnabled:true,acpTechEnabled:true,primary:'acptech'}),['acptech','legacy']);
});

test('fails closed when the master disables both sources',()=>{
  assert.deepEqual(providerOrder({legacyEnabled:false,acpTechEnabled:false},'acptech'),[]);
});
