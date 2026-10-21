import {test} from 'node:test';
import assert from 'node:assert/strict';
import {providerOrder} from './provider-routing.mjs';

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
