// Each source retains its own price basis, quantities and validity. Never calculate
// a multi-buy using the price of a different campaign.
export function normalizeCommercial(item) {
  return {
    ...item,
    value: item.price ?? item.value ?? null,
    previousValue: item.previous_price ?? item.previousValue ?? null,
    clubValue: item.club_price ?? item.clubValue ?? null,
    wholesaleValue: item.wholesale_price ?? item.wholesaleValue ?? null,
    wholesaleQuantity: item.wholesale_quantity ?? item.wholesaleQuantity ?? null,
    quantityTake: item.leve_quantity ?? item.quantityTake ?? null,
    quantityPay: item.pague_quantity ?? item.quantityPay ?? null,
    secondUnitDiscount: item.second_unit_discount ?? item.secondUnitDiscount ?? null,
    cashback: item.cashback_percent ?? item.cashback ?? null,
    cashbackValue: item.cashback_value ?? item.cashbackValue ?? null,
    unitLimitPerCPF: item.unit_limit ?? item.unitLimitPerCPF ?? null,
    startDate: item.start_date ?? item.startDate ?? null,
    endDate: item.valid_until ?? item.endDate ?? null
  };
}

export function withinValidity(item, now = Date.now()) {
  const today = new Intl.DateTimeFormat('en-CA', {timeZone: 'America/Fortaleza', year: 'numeric', month: '2-digit', day: '2-digit'}).format(now);
  const start = item.startDate?.slice(0, 10);
  const end = item.endDate?.slice(0, 10);
  return (!start || start <= today) && (!end || end >= today);
}

const positive = value => Number.isFinite(Number(value)) && Number(value) > 0;
const type = row => String(row.promotion_type ?? row.promotionType ?? '').toLowerCase().replace(/[^a-z0-9]/g, '');
function unconditional(row) {
  // Do not turn an explicitly conditional campaign into an ordinary price.
  return ['', 'depor', 'regular', 'normal', 'varejo'].includes(type(row));
}

export function mergeCommercial(base, campaigns, now = Date.now()) {
  const normalized = normalizeCommercial(base);
  const current = campaigns.map(normalizeCommercial).filter(row => withinValidity(row, now));
  // Only explicit De/Por campaigns replace a positive catalogue price. A plain
  // campaign price is a fallback when the catalogue has no usable price.
  const promotions = current.filter(row => unconditional(row) && positive(row.value) && Number(row.previousValue) > Number(row.value));
  const selected = promotions.sort((a, b) => Number(a.value) - Number(b.value) || String(a.id).localeCompare(String(b.id)))[0]
    ?? (!positive(normalized.value) ? current.find(row => unconditional(row) && positive(row.value)) : null);
  if (!selected) return normalized;
  // Copy one complete commercial record. Other campaigns are exposed through
  // the existing Campaign/all contract, not mixed into its multi-buy price basis.
  const result = {...normalized};
  for (const key of ['value', 'previousValue', 'clubValue', 'wholesaleValue', 'wholesaleQuantity', 'quantityTake', 'quantityPay',
    'secondUnitDiscount', 'cashback', 'cashbackValue', 'unitLimitPerCPF', 'startDate', 'endDate']) result[key] = selected[key];
  // A campaign which omits Clube does not erase the independently available
  // catalogue Clube price. Quantity-dependent rules keep their own price basis
  // through Campaign/all instead of borrowing this campaign's unit price.
  if (!positive(result.clubValue) && positive(normalized.clubValue)) result.clubValue = normalized.clubValue;
  for (const [raw, alias] of [['price','value'], ['previous_price','previousValue'], ['club_price','clubValue'],
    ['wholesale_price','wholesaleValue'], ['wholesale_quantity','wholesaleQuantity'], ['leve_quantity','quantityTake'],
    ['pague_quantity','quantityPay'], ['valid_until','endDate'], ['start_date','startDate']]) result[raw] = result[alias];
  return result;
}
