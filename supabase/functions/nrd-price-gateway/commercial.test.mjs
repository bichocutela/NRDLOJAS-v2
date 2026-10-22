import test from 'node:test';
import assert from 'node:assert/strict';
import {mergeCommercial, normalizeCommercial, notExpired, withinValidity} from './commercial.mjs';

const now = Date.parse('2026-10-18T19:00:00Z');
test('current campaign De/Por replaces the normal catalogue price as one price pair', () => {
  const result = mergeCommercial({id:'p', price:10, previous_price:0}, [
    {id:'c', price:6, previous_price:10, startDate:'2026-10-16', endDate:'2026-10-18'}
  ], now);
  assert.equal(result.value, 6);
  assert.equal(result.price, 6);
  assert.equal(result.previousValue, 10);
  assert.equal(result.endDate, '2026-10-18');
});

test('expired and future campaigns do not replace current catalogue prices', () => {
  const result = mergeCommercial({price:10}, [
    {price:2, previous_price:10, endDate:'2026-10-17'},
    {price:3, previous_price:10, startDate:'2026-10-19'}
  ], now);
  assert.equal(result.value, 10);
});

test('club-only campaign is not converted into an unconditional De/Por price', () => {
  const result = mergeCommercial({price:10, club_price:8}, [
    {price:6, previous_price:10, club_price:5, promotion_type:'clube'}
  ], now);
  assert.equal(result.value, 10);
  assert.equal(result.clubValue, 8);
});

test('does not attach quantities from a different campaign to a discounted unit price', () => {
  const result = mergeCommercial({price:10, leve_quantity:3, pague_quantity:2}, [
    {price:6, previous_price:10}
  ], now);
  assert.equal(result.value, 6);
  assert.equal(result.quantityTake, null);
  assert.equal(result.quantityPay, null);
});

test('store snake-case values, including explicit zero, replace stale aliases', () => {
  const result = normalizeCommercial({price:9, value:12, club_price:0, clubValue:5, cashback_percent:10, cashback_value:2});
  assert.equal(result.value, 9);
  assert.equal(result.clubValue, 0);
  assert.equal(result.cashback, 10);
  assert.equal(result.cashbackValue, 2);
});

test('validity uses the Fortaleza calendar date and includes the final day', () => {
  assert.equal(withinValidity({endDate:'2026-10-18'}, Date.parse('2026-10-19T02:59:00Z')), true);
  assert.equal(withinValidity({endDate:'2026-10-18'}, Date.parse('2026-10-19T03:00:00Z')), false);
});

test('upcoming campaigns remain available to search until their end date', () => {
  assert.equal(notExpired({startDate:'2026-10-19', endDate:'2026-10-21'}, now), true);
  assert.equal(notExpired({startDate:'2026-10-17', endDate:'2026-10-17'}, now), false);
});
