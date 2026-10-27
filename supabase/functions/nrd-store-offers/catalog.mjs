const text = value => typeof value === 'string' ? value.trim().slice(0, 10000) : '';
const number = value => typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : 0;
export function sourceDate(value) {
  const raw = text(value);
  if(!raw) return null;
  const timestamp = Date.parse(raw + (/Z$|[+-]\d\d:\d\d$/.test(raw) ? '' : '-03:00'));
  return Number.isFinite(timestamp) ? new Date(timestamp).toISOString() : null;
}
export function activeOffers(offers, now = Date.now()) {
  return offers.filter(item => Date.parse(item.startsAt) <= now && Date.parse(item.endsAt) >= now);
}
export function offerCatalog(source) {
  if(!source || !Array.isArray(source.promotions)) throw new Error('INVALID_CATALOG');
  const offers = [], seen = new Set();
  for(const campaign of source.promotions) {
    if(!Array.isArray(campaign.items)) throw new Error('INVALID_ITEMS');
    if(campaign.status !== 'A') continue;
    const startsAt = sourceDate(campaign.startDate), endsAt = sourceDate(campaign.endDateOfVigency || campaign.endDate);
    if(!startsAt || !endsAt) throw new Error('INVALID_DATES');
    for(const item of campaign.items) {
      const id = `${campaign.id}:${item.id}`;
      if(!Number.isSafeInteger(campaign.id) || !Number.isSafeInteger(item.id) || !text(item.description)) throw new Error('INVALID_ITEM');
      if(seen.has(id)) continue;
      seen.add(id);
      const original = number(item.originalPrice), price = number(item.price);
      const cashback = item.cashbackRule;
      const rules = Array.isArray(item.rules) ? item.rules.map(rule => [text(rule.message), number(rule.discountPercentual) ? `${number(rule.discountPercentual)}%` : ''].filter(Boolean).join(' ')).filter(Boolean) : [];
      const images=[...new Set((Array.isArray(item.images) ? item.images : [])
        .filter(url => typeof url === 'string').map(url => url.trim())
        .filter(url => /^https:\/\//.test(url) && !/semImagem/i.test(url)))];
      const barcode = [item.barCode, item.barcode, item.ean, item.gtin, item.codigoBarras, item.codigo_barras,
        item.product?.barCode, item.product?.barcode, item.product?.ean, item.product?.gtin]
        .find(value => typeof value === 'string' || typeof value === 'number');
      const ean = barcode == null ? '' : String(barcode).trim().replace(/\s+/g, '');
      offers.push({ id, description:text(item.description), code:String(item.codeErp || item.productId || ''), barcode: /^[0-9]{8,14}$/.test(ean) ? ean : '',
        category:text(item.categoryName) || text(item.department) || 'Outras',
        image:images[0] || '', images,
        priceCents:Math.round((price > 0 ? price : original) * 100), originalPriceCents:Math.round(original * 100),
        showPrice: price > 0 || item.indexibirPrecoBase !== false,
        discount: price > 0 && original > price && item.indexibirPercDesconto !== false ? Math.floor((original-price)/original*100) : 0,
        cashback: cashback && number(cashback.value) ? `${cashback.calculationType === '%' ? number(cashback.value) + '%' : 'R$ ' + number(cashback.value).toFixed(2).replace('.', ',')} de cashback` : '',
        maxAmount:number(item.amount), tag:text(item.tag?.text), startsAt, endsAt,
        validity:text(campaign.expirationMessage), campaign:text(campaign.name),
        conditions:[...new Set([...rules, text(item.appMessage), text(item.message1), text(item.message2), text(cashback?.message)].filter(Boolean))].join('\n\n'),
        activationRequired:campaign.type === 'A' });
    }
  }
  return offers;
}
