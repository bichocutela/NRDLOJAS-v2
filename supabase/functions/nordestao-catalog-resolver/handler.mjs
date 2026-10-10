const STOP_WORDS = new Set([
  'a', 'as', 'o', 'os', 'um', 'uma', 'uns', 'umas', 'de', 'da', 'das', 'do', 'dos',
  'e', 'em', 'no', 'na', 'nos', 'nas', 'para', 'por', 'com', 'sem', 'tipo', 'pct',
  'pc', 'un', 'unid', 'unidade'
]);

const WEIGHT = Object.freeze({
  tokenJaccard: 0.58,
  importantTokenRecall: 0.22,
  brandAndSize: 0.20
});

const DEFAULT_MIN_SCORE = 0.86;

export function normalizeText(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

export function normalizeCode(value) {
  const raw = String(value ?? '').trim();
  if (!raw) return null;
  // Preserve leading zeroes: internal product codes are identifiers, not numbers.
  return raw.replace(/\s+/g, '').toUpperCase();
}

export function tokens(value) {
  return new Set(normalizeText(value).split(' ').filter(token => token && !STOP_WORDS.has(token)));
}

function numericAttributes(value) {
  const text = normalizeText(value);
  const sizes = [...text.matchAll(/\b\d+(?:[.,]\d+)?\s*(?:kg|g|mg|l|ml|cl|un|und|unid|unidade)\b/g)]
    .map(match => match[0].replace(',', '.').replace(/\s+/g, ''));
  const numbers = [...text.matchAll(/\b\d+(?:[.,]\d+)?\b/g)].map(match => match[0].replace(',', '.'));
  return {sizes: new Set(sizes), numbers: new Set(numbers)};
}

function brandTokenSet(input) {
  return tokens(input).size ? tokens(input) : new Set();
}

function jaccard(left, right) {
  if (!left.size || !right.size) return 0;
  let intersection = 0;
  for (const item of left) if (right.has(item)) intersection++;
  const union = left.size + right.size - intersection;
  return union ? intersection / union : 0;
}

function recall(left, right) {
  if (!left.size) return 0;
  let found = 0;
  for (const item of left) if (right.has(item)) found++;
  return found / left.size;
}

function attributeScore(query, candidate) {
  const queryAttrs = numericAttributes(query);
  const candidateAttrs = numericAttributes(candidate);
  const sizeScore = queryAttrs.sizes.size
    ? recall(queryAttrs.sizes, candidateAttrs.sizes)
    : 0.5;
  const numberScore = queryAttrs.numbers.size
    ? recall(queryAttrs.numbers, candidateAttrs.numbers)
    : 0.5;
  return sizeScore * 0.75 + numberScore * 0.25;
}

function toCatalogRecord(record) {
  if (!record || typeof record !== 'object') return null;
  const productId = String(record.produto_id ?? record.productId ?? record.id ?? '').trim();
  const description = String(record.descricao ?? record.description ?? record.nome ?? record.name ?? '').trim();
  if (!productId || !description) return null;
  const images = Array.isArray(record.imagens) ? record.imagens : [];
  const imageUrl = String(
    record.imagem ?? record.image ?? record.imageUrl ?? images.find(image => image?.src)?.src ?? ''
  ).trim() || null;
  const slug = String(record.slug ?? record.link ?? '').trim();
  const productUrl = String(record.productUrl ?? record.url ?? (slug
    ? `https://www.lojaonline.nordestao.com.br/produto/${encodeURIComponent(productId)}/${slug.replace(/^\/+/, '')}`
    : `https://www.lojaonline.nordestao.com.br/produto/${encodeURIComponent(productId)}`)).trim();
  const internalCode = normalizeCode(
    record.codigo_interno ?? record.codigoInterno ?? record.codigo ?? record.code ?? record.sku
  );
  return {
    productId,
    description,
    normalizedDescription: normalizeText(description),
    internalCode,
    imageUrl,
    productUrl,
    raw: record
  };
}

export function buildCatalogIndex(records) {
  const byCode = new Map();
  const catalog = [];
  for (const raw of records ?? []) {
    const record = toCatalogRecord(raw);
    if (!record) continue;
    catalog.push(record);
    if (record.internalCode && !byCode.has(record.internalCode)) byCode.set(record.internalCode, record);
  }
  return {catalog, byCode};
}

function scoreDescription(query, candidate) {
  const queryTokens = tokens(query);
  const candidateTokens = tokens(candidate.description);
  const importantQueryTokens = new Set([...queryTokens].filter(token => token.length >= 3));
  const brandAndSize = attributeScore(query, candidate.description);
  return WEIGHT.tokenJaccard * jaccard(queryTokens, candidateTokens)
    + WEIGHT.importantTokenRecall * recall(importantQueryTokens, candidateTokens)
    + WEIGHT.brandAndSize * brandAndSize;
}

export function resolveCatalogItem(item, index, options = {}) {
  const code = normalizeCode(item?.codproduto ?? item?.codigo ?? item?.code ?? item?.internalCode);
  const description = String(item?.desc_prod ?? item?.descricao ?? item?.description ?? item?.name ?? '').trim();
  const minScore = Number.isFinite(options.minScore) ? options.minScore : DEFAULT_MIN_SCORE;

  if (code && index.byCode.has(code)) {
    const exact = index.byCode.get(code);
    return {
      input: item,
      matched: true,
      matchType: 'internal_code',
      score: 1,
      product: exact
    };
  }

  if (!description) {
    return {input: item, matched: false, matchType: 'none', score: 0, product: null, candidates: []};
  }

  const candidates = index.catalog
    .map(product => ({product, score: scoreDescription(description, product)}))
    .sort((left, right) => right.score - left.score || left.product.productId.localeCompare(right.product.productId));
  const best = candidates[0];
  const second = candidates[1];
  // The margin prevents choosing between near-identical package sizes or flavours.
  const margin = best && second ? best.score - second.score : best?.score ?? 0;
  const accepted = Boolean(best && best.score >= minScore && (best.score === 1 || margin >= 0.08));
  return {
    input: item,
    matched: accepted,
    matchType: accepted ? 'description' : 'ambiguous',
    score: best?.score ?? 0,
    margin,
    product: accepted ? best.product : null,
    candidates: candidates.slice(0, 3).map(candidate => ({
      productId: candidate.product.productId,
      description: candidate.product.description,
      score: candidate.score,
      imageUrl: candidate.product.imageUrl,
      productUrl: candidate.product.productUrl
    }))
  };
}

export function enrichPromotion(item, resolution) {
  if (!resolution?.matched || !resolution.product) return {...item};
  return {
    ...item,
    // Prefer the official Nordestão catalog image; retain the ACP image only
    // when the catalog match has no usable image URL.
    imagem: resolution.product.imageUrl || item.imagem,
    linkloja: resolution.product.productUrl || item.linkloja,
    catalog_match_type: resolution.matchType,
    catalog_match_score: Number(resolution.score.toFixed(4)),
    catalog_product_id: resolution.product.productId
  };
}

export function resolvePromotions(items, records, options = {}) {
  const index = buildCatalogIndex(records);
  return (items ?? []).map(item => {
    const resolution = resolveCatalogItem(item, index, options);
    return {resolution, promotion: enrichPromotion(item, resolution)};
  });
}

export function createHandler({loadCatalog, minScore = DEFAULT_MIN_SCORE} = {}) {
  if (typeof loadCatalog !== 'function') throw new TypeError('CATALOG_LOADER_REQUIRED');
  return async request => {
    if (request.method !== 'POST') return Response.json({error: 'METHOD_NOT_ALLOWED'}, {status: 405});
    let body;
    try {
      body = await request.json();
    } catch {
      return Response.json({error: 'INVALID_JSON'}, {status: 400});
    }
    if (!Array.isArray(body?.items) || body.items.length > 500) {
      return Response.json({error: 'INVALID_ITEMS'}, {status: 400});
    }
    try {
      const catalog = await loadCatalog();
      const results = resolvePromotions(body.items, catalog, {minScore});
      return Response.json({items: results.map(result => result.promotion), matches: results.map(result => result.resolution)}, {
        headers: {'Cache-Control': 'no-store'}
      });
    } catch {
      return Response.json({error: 'CATALOG_UNAVAILABLE'}, {status: 503});
    }
  };
}
